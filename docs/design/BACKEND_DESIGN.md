# Backend Design — AI Career Coach

> Status: detailed backend design (v1). Decyzje szczegółowe + uzasadnienia.
> Kontekst: [`PRODUCT_DESIGN.md`](./PRODUCT_DESIGN.md), [`TECHNICAL_DESIGN.md`](./TECHNICAL_DESIGN.md).
> Stack: Java + Spring Boot, PostgreSQL + Flyway, monolit modularny.

## Zasada przewodnia

**Złożoność proporcjonalna do stawki.** Tam gdzie granica jest realnie zewnętrzna/zmienna
(providerzy AI, Google Calendar) — port/adapter. Tam gdzie to zapis stanu (cele, zadania,
gamifikacja) — prosty warstwowy CRUD z logiką w serwisach. To samo dotyczy stanów,
akceptacji i autonomii AI (patrz dokumenty produktowy/techniczny).

---

## 1. Struktura modułów (decyzja: warstwowo + porty tylko na granicach I/O)

Monolit, moduły jako pakiety. **Warstwowo per moduł** (`controller / service / domain / repository`),
**porty & adaptery tylko** dla `ai` i `calendar` (realnie zewnętrzne, zmienne, testowalne przez mock).

```
goals         — rekursywne drzewo celów, stany, akceptacja
tasks         — instancje zadań/questów; routing weryfikacji
jobs          — abstrakcja Job + runner (kolejka, poller, executor); zna tylko JobHandler
coach         — assembler kontekstu, logika planowania/oceny, coach_notes, mock sessions
ai            — PORTY: OpenRouterClient (chat/SSE) ORAZ ClaudeCliClient (agent) — jawnie rozdzielone
gamification  — exp (ledger + liczniki), skille, poziomy, awatar
calendar      — PORT: Google Calendar (warstwa 2)
auth          — Google OAuth2, email-whitelist
config        — TaskTypeDefinition + katalog skilli (seed YAML → baza, runtime-editable)
```

**Reguły zależności:**
- Moduły komunikują się przez **interfejsy serwisów aplikacyjnych** — nigdy nawzajem do repozytoriów.
- `jobs` zna tylko `JobHandler`; konkretne handlery rejestrują się do runnera:
  `PlanningJobHandler` (coach), `EvaluationJobHandler` (coach + gamification),
  `AgentJobHandler` (ai). `jobs` nie zna ich treści — kierunek zależności czysty.
- AI: `OpenRouterClient` i `ClaudeCliClient` **nie** są chowane za wspólnym `LlmClient`
  (różne byty: chat vs agent-z-narzędziami). Wspólnym kontraktem jest `JobHandler`.

---

## 2. Model domenowy

### 2.1 Tożsamość
- **`User`** — `id, email, googleSub, displayName, createdAt`. Single-user, email-whitelist.
- **`CareerProfile`** (1:1) — `userId, totalExp, level, avatarState (JSONB)`.

### 2.2 Drzewo celów — rekursywny `Goal` (adjacency list)
**Decyzja:** jedna rekursywna encja `Goal` z `parentId`; `Task` jako **osobna** encja.

`Goal`:
```
id, parentId (null = STRATEGIC), kind: STRATEGIC | LEVEL,
title, description,
state: PROPOSED | ACCEPTED | ACTIVE | CLOSED,
createdBy: COACH | USER,
orderIndex,
expEarned (licznik-cache, bąbelkowany),
createdAt, updatedAt
```
Niezmienniki (walidacja w serwisie): `STRATEGIC` ma `parentId=null`; zadania wiszą tylko pod
celem; coach generuje zadania **tylko** pod `Goal` w stanie `ACTIVE`.

*Uzasadnienie:* jeden zestaw operacji (akceptacja, stany, bąbelkowanie) na każdym piętrze;
elastyczna głębokość; bąbelkowanie = walk po `parentId`; Postgres `WITH RECURSIVE` dla poddrzew.

### 2.3 `Task` (liść — instancja questa)
**Decyzja:** 4 stany; fazę weryfikacji modeluje `Job.status`, nie stan Taska; stan końcowy ustawia backend.

```
id, goalId, typeKey (→ TaskTypeDefinition),
title, description,
state: TODO | IN_PROGRESS | DONE | REJECTED,
skillKeys[] (referencje do katalogu skilli),
artifact (nullable: tekst/link),
expAwarded,
scheduledFor (nullable; calendar warstwa 2),
verificationJobId (nullable → Job),
createdAt, updatedAt
```
Przejścia:
| Przejście | Wyzwalacz |
|---|---|
| `TODO → IN_PROGRESS` | user (start; pomijalne dla HONOR) |
| `IN_PROGRESS → DONE` | **backend** po pozytywnej weryfikacji / od razu dla HONOR |
| `IN_PROGRESS → REJECTED` | **backend** po negatywnej weryfikacji |
| `REJECTED → TODO` | retry (historia prób zachowana w ExpEvent/ocenach) |

`REJECTED` **nie jest terminalny** — wraca do `TODO`. „Oblać mocka" = sygnał „poćwicz jeszcze".
Stanu końcowego **nigdy** nie ustawia AI ani klient — zawsze backend.

### 2.4 Konfiguracja mechaniki (seed YAML → baza, runtime-editable)
**`TaskTypeDefinition`:**
```
key, displayName,
verificationMethod: HONOR | HONOR_WITH_PROOF | AUTO_QUIZ | AI_DIALOG | AI_ARTIFACT_REVIEW,
expBase, expScaleByScore (bool), requiresArtifact
```
**`SkillDefinition`** (katalog skilli — kuratorowany, **nie** dynamiczny):
```
key, displayName, category
```
*Uzasadnienie skilli jako config:* stabilna oś postępu (bez rozmycia na prawie-duplikaty),
przewidywalny zestaw „statystyk postaci", spójność z `TaskTypeDefinition`. Coach **wybiera
z listy** (dostaje ją z assemblera kontekstu); brakujący skill dodajesz wpisem w runtime.

### 2.5 Gamifikacja — ledger + liczniki
**Decyzja:** `ExpEvent` (append-only) = źródło prawdy; liczniki zdenormalizowane = cache.

**`Skill`** (postęp usera): `key (→ SkillDefinition), level, exp`.
**`ExpEvent`** (append-only): `id, sourceTaskId, attemptId (nullable), skillKey (null=ogólny), amount, reason, createdAt`.
Liczniki-cache: `Skill.exp`, `Goal.expEarned`, `CareerProfile.totalExp`.

*Uzasadnienie:* audyt/wyjaśnialność („skąd te 340 exp?"), przeliczalność przy strojeniu balansu
(TBD T3), idempotencja przy retry (`sourceTaskId` + `attemptId` → brak podwojenia), szybki odczyt.
Spójność event↔licznik w jednej transakcji; możliwy „rebuild" liczników z ledgera.

### 2.6 Pamięć coacha
**`CoachNote`**: `id, content, active, createdAt, updatedAt`. Coach tworzy/aktualizuje
autonomicznie (przez narzędzie), user widzi/edytuje/kasuje w UI.
**`MockSession`**: `id, taskId, state, score, startedAt, finishedAt`.
**`MockMessage`** (osobna tabela — decyzja A): `id, sessionId, role, content, seq, createdAt`.
Zapisywana **inkrementalnie** (wiadomość po wiadomości — odporność na crash w połowie sesji).
Transkrypt **nie wchodzi** do długoterminowej pamięci coacha (assemblera) — przeżywa go tylko
wynik (`score`, `ExpEvent`) i ewentualny destylat do `coach_notes`.

### 2.7 Joby
**`Job`:**
```
id, type: PLANNING | EVALUATION | AGENT,
status: QUEUED | RUNNING | DONE | FAILED,
input (JSONB), output (JSONB),
relatedGoalId (nullable), relatedTaskId (nullable),
attempts, maxAttempts, nextRunAt (backoff),
lockedAt, error,
createdAt, startedAt, finishedAt
```

---

## 3. Mapowanie JSONB

**Decyzja:** natywny Hibernate 6 — `@JdbcTypeCode(SqlTypes.JSON)`, mapowane na **typowane
rekordy Javy** (np. `EvaluationOutput`, `PlanningOutput`, `AvatarState`). Bez `hypersistence-utils`.

**Granica:** JSONB tylko dla danych **nieprzeszukiwanych relacyjnie** (`Job.input/output`,
`avatarState`). Cokolwiek, po czym filtrujesz/sortujesz (stany, klucze, daty) → **normalne kolumny**.

---

## 4. Runner Jobów

`@Scheduled(fixedDelay ~1–2s)` poller:
1. **Claim batch:** `UPDATE jobs SET status='RUNNING', locked_at=now() WHERE id IN
   (SELECT id FROM jobs WHERE status='QUEUED' AND (next_run_at IS NULL OR next_run_at<=now())
   ORDER BY created_at LIMIT :n FOR UPDATE SKIP LOCKED) RETURNING ...`
2. **Dispatch** do `ThreadPoolTaskExecutor`; wybór `JobHandler` po `job.type`.
3. **Limit współbieżności per typ** (AGENT = 1 — kosztowny subprocess Claude CLI).
4. **Retry** z backoffem przez `nextRunAt` + `attempts/maxAttempts`; przekroczenie → `FAILED`.
5. **Restart-safe:** stan w bazie; po restarcie poller zgarnia QUEUED; ew. „zawieszone" RUNNING
   (po `lockedAt` + timeout) odzyskiwane.

### Handlery + kształty JSONB
```java
interface JobHandler { JobType type(); JobResult handle(Job job); }
```
- **PLANNING** (coach) — in `{goalId, mode: DECOMPOSE | GENERATE_TASKS}`;
  out `{proposedGoals[]}` lub `{proposedTasks[]}`. Woła OpenRouter.
- **EVALUATION** (coach+gamification) — in `{taskId, typeKey, artifact?, answers?}`;
  out `{score, expProposed, skillBreakdown[], feedback, passed}`. Woła OpenRouter.
- **AGENT** (ai) — in `{prompt, workdir, taskId?}`; out `{structuredFindings, raw}`.
  `ProcessBuilder` → `claude -p "..." --output-format json`, parsowanie strukturalne.

---

## 5. Przepływ ukończenia zadania + exp

`POST /api/tasks/{id}/submit` rozgałęzia się wg `TaskTypeDefinition.verificationMethod`:

- **HONOR / HONOR_WITH_PROOF** → **synchronicznie, bez Joba:** od razu `GamificationService.award(...)`
  + `Task → DONE`. (Job istnieje dla rzeczy długich/async; HONOR jest natychmiastowy.)
- **AUTO_QUIZ / AI_DIALOG / AI_ARTIFACT_REVIEW** → tworzy `EVALUATION` job,
  `Task → IN_PROGRESS`; wynik i `award` po zakończeniu Joba; postęp przez SSE.

**`GamificationService.award(...)` (wspólne dla obu ścieżek)** — w jednej transakcji:
1. Clamp `expProposed` wg `TaskTypeDefinition` (AI tylko *proponuje*).
2. Idempotencja po `sourceTaskId` (+ `attemptId`) — brak podwojenia przy retry.
3. Zapis `ExpEvent`(ów) per skill.
4. Aktualizacja liczników: `Skill.exp` (+ przeliczenie `level`), bąbelkowanie `Goal.expEarned`
   walkiem po `parentId`, `CareerProfile.totalExp` (+ `level`).
5. Emit SSE: `exp-gain`, ew. `level-up`.

---

## 6. Assembler kontekstu coacha

Przed `PLANNING`/`EVALUATION` buduje prompt z **twardego stanu (baza)** + lekkiej pamięci:
1. profil + cel strategiczny, 2. aktywne drzewo celów/zadań, 3. poziomy skilli
(+ **lista dostępnych `SkillDefinition`** do wyboru), 4. ostatnie N ukończonych/ocenionych zadań,
5. `coach_notes`. Transkrypty mocków **nie** wchodzą.

---

## 7. API (REST + SSE)

```
GET    /api/me
GET    /api/goals                       # drzewo
POST   /api/goals                       # user tworzy cel strategiczny
POST   /api/goals/{id}/accept           # PROPOSED → ACTIVE
POST   /api/goals/{id}/close
POST   /api/goals/{id}/plan             # → PLANNING job
GET    /api/tasks?due=today&state=&goalId=
POST   /api/tasks/{id}/start            # → IN_PROGRESS
POST   /api/tasks/{id}/submit           # HONOR: sync; AI: → EVALUATION job
GET    /api/tasks/{id}
POST   /api/tasks/{id}/mock/start       # → MockSession + SSE
POST   /api/mock/{sessionId}/messages   # SSE stream
POST   /api/coach/messages              # czat strategiczny, SSE
GET    /api/jobs/{id}
GET    /api/events                      # globalny SSE: status jobów, exp-gain, level-up
GET/PUT/DELETE /api/coach-notes[/{id}]
GET    /api/profile                     # exp, level, awatar, skille
GET    /api/skills
GET/PUT /api/task-types[/{key}]         # config w runtime
GET/PUT /api/skill-defs[/{key}]         # katalog skilli w runtime
```
**SSE** (`SseEmitter`): globalny strumień zdarzeń (status Jobów, exp-gain, level-up) +
streaming odpowiedzi LLM (coach/mock) token-po-tokenie.

---

## 8. Schemat tabel (Flyway — zarys)

```
users(id, email UNIQUE, google_sub, display_name, created_at)
career_profile(user_id PK/FK, total_exp, level, avatar_state JSONB)
goal(id, parent_id FK→goal, kind, title, description, state, created_by,
     order_index, exp_earned, created_at, updated_at)
task(id, goal_id FK, type_key FK, title, description, state, skill_keys[],
     artifact, exp_awarded, scheduled_for, verification_job_id FK→job,
     created_at, updated_at)
task_type_definition(key PK, display_name, verification_method, exp_base,
     exp_scale_by_score, requires_artifact)
skill_definition(key PK, display_name, category)
skill(key FK PK, level, exp)
exp_event(id, source_task_id, attempt_id, skill_key, amount, reason, created_at)  -- append-only
coach_note(id, content, active, created_at, updated_at)
mock_session(id, task_id FK, state, score, started_at, finished_at)
mock_message(id, session_id FK, role, content, seq, created_at)
job(id, type, status, input JSONB, output JSONB, related_goal_id, related_task_id,
     attempts, max_attempts, next_run_at, locked_at, error,
     created_at, started_at, finished_at)
```
Indeksy m.in.: `job(status, next_run_at)` (poller), `task(state)`, `task(goal_id)`,
`exp_event(source_task_id)` (idempotencja), `goal(parent_id)`.

---

## 9. Otwarte decyzje (dziedziczone — nie blokują MVP)

| # | Temat | Notatka |
|---|---|---|
| T1 | Katalog roboczy agenta Claude CLI | izolacja per job vs stały klon repo |
| T2 | Modele OpenRouter per typ Joba | tani do QUIZ, mocny do MOCK |
| T3 | Krzywa expa / ekonomia poziomów | strojenie eksperymentalne (ledger umożliwia rebuild) |
| T4 | Kierunek sync Google Calendar | app→kalendarz vs dwukierunkowo |
| T5 | Polityka retry/timeout Jobów | progi backoffu, timeout RUNNING dla recovery |

---

## 10. Pierwszy pionowy plasterek (milestone 1)

```
Cel "przygotowanie behawioralne" (ACTIVE)
  → POST /goals/{id}/plan → PLANNING job → 3 proponowane taski (typ MOCK)
    → POST /tasks/{id}/mock/start → MockSession (mock_message zapisywane inkrementalnie, SSE)
      → EVALUATION job → {score, expProposed, passed}
        → GamificationService.award → ExpEvent(skill="behavioral") + bąbelkowanie + SSE
          → Task DONE, paski/awatar rosną
```
Bez kalendarza, bez AGENT-joba, bez wielu ścieżek.
