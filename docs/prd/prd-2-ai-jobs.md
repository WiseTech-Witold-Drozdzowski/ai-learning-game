# PRD 2 — Podsystem AI i Jobów (Backend)

> Część 2 z 2. Asynchroniczna „inteligencja" na fundamencie PRD 1. Towarzyszy: [`prd-1-core-domain.md`](./prd-1-core-domain.md).
> Źródła decyzji: `docs/design/PRODUCT_DESIGN.md`, `docs/design/TECHNICAL_DESIGN.md`, `docs/design/BACKEND_DESIGN.md`.
> Uwaga: greenfield — brak istniejącego kodu/testów; PRD oparty o design docs + sesje grill-me.
> Zależność: konsumuje `GamificationService.award(...)`, encje `Goal`/`Task` oraz katalogi config z PRD 1.

## Problem Statement

Jako uczący się chcę mieć **AI-coacha**, który pomaga mi ustalać cele wyższego poziomu,
samodzielnie układa pod nimi konkretne zadania, a następnie **weryfikuje** moją pracę
(quiz, mock interview, recenzja artefaktu) i przydziela za nią exp. Chcę też, by coach
„mnie pamiętał" (moje preferencje, słabości) i by cięższe, autonomiczne zadania (np. przegląd
mojego repo) mógł wykonywać agent. To wszystko musi działać asynchronicznie, odpornie na restart
i z podglądem postępu na żywo — bez tego coaching jest albo płytki, albo zawiesza interfejs.

## Solution

Podsystem **Jobów** jako uniwersalna, asynchroniczna abstrakcja (kolejka w tabeli + poller +
executor, restart-safe) z trzema typami: **PLANNING** (coach proponuje cele/zadania),
**EVALUATION** (coach ocenia pracę i proponuje exp), **AGENT** (Claude CLI wykonuje autonomiczne
zadania z narzędziami). Dwaj **jawnie rozdzieleni** providerzy AI: OpenRouter (chat/streaming)
i Claude CLI (agent). **Assembler kontekstu** składa stan z bazy (PRD 1) + lekką pamięć narracyjną
(`coach_notes`). Mock interview to sesja z trwałym, inkrementalnie zapisywanym transkryptem.
Wyniki weryfikacji wpinają się w gamifikację przez `GamificationService.award` (PRD 1),
a postęp Jobów i streaming odpowiedzi LLM lecą do klienta przez **SSE**.

## User Stories

1. Jako uczący się chcę poprosić coacha o rozplanowanie aktywnego celu, aby dostać propozycję leveli/zadań pod nim.
2. Jako uczący się chcę, aby coach generował zadania **tylko** pod celami w stanie `ACTIVE`, by nie zasypywał mnie pracą pod niezatwierdzonym kierunkiem.
3. Jako uczący się chcę, aby propozycje celów wyższego poziomu wymagały mojej akceptacji, a zadania coach mógł dodawać autonomicznie (autonomia skalowana ze stawką).
4. Jako uczący się chcę rozmawiać z coachem (czat) przy ustalaniu celów strategicznych, widząc odpowiedź pojawiającą się na żywo (token po tokenie).
5. Jako uczący się chcę, aby planowanie odbywało się asynchronicznie (Job), tak by interfejs nie zawieszał się na czas myślenia AI.
6. Jako uczący się chcę widzieć status pracy coacha („w kolejce / myśli / gotowe"), aby wiedzieć, że coś się dzieje.
7. Jako uczący się chcę, aby zadanie typu QUIZ było generowane przez AI i automatycznie oceniane, a exp zależał od % poprawnych odpowiedzi.
8. Jako uczący się chcę odbyć mock interview, w którym coach mnie przepytuje, a rozmowa płynie strumieniowo.
9. Jako uczący się chcę, aby po mocku AI oceniło moją odpowiedź i zaproponowało exp, który backend zatwierdzi i naliczy.
10. Jako uczący się chcę, aby transkrypt mocka był zapisywany w całości i inkrementalnie, tak by awaria w połowie sesji nie kasowała historii.
11. Jako uczący się chcę móc wrócić do zapisanego transkryptu mocka dla wglądu, nawet jeśli nie zasila on długoterminowej pamięci coacha.
12. Jako uczący się chcę, aby zadanie ARTIFACT (np. fragment portfolio / zadanie kodowe) było recenzowane przez AI, a ocena przekładała się na exp.
13. Jako uczący się chcę, aby cięższe zadania (np. „przejrzyj moje repo i powiedz, co poprawić") wykonywał agent (Claude CLI) autonomicznie, używając narzędzi.
14. Jako uczący się chcę, aby agent-Joby były ograniczone współbieżnościowo (np. jeden naraz), bo są kosztowne i obciążające.
15. Jako uczący się chcę, aby wynik agenta był strukturalny (parsowalny), a nie luźnym tekstem, by dało się go wykorzystać dalej.
16. Jako uczący się chcę, aby Joby przetrwały restart aplikacji (kolejka w bazie), tak by nic nie ginęło po wdrożeniu czy awarii.
17. Jako uczący się chcę, aby nieudany Job był ponawiany z backoffem do limitu prób, zanim zostanie oznaczony jako `FAILED`.
18. Jako uczący się chcę, aby „zawieszony" Job (RUNNING ponad timeout) był wykrywany i odzyskiwany, by nie blokował systemu.
19. Jako uczący się chcę, aby exp z weryfikacji AI był tylko **propozycją** AI, którą backend clampuje i zatwierdza, bo AI nie może rozdawać punktów dowolnie.
20. Jako uczący się chcę, aby ponowne wykonanie tego samego EVALUATION (np. po retry) nie podwoiło exp (idempotencja).
21. Jako uczący się chcę, aby coach miał kontekst mojego stanu (cele, postęp, poziomy skilli, ostatnie zadania), aby jego rady i plany nie były generyczne.
22. Jako uczący się chcę, aby coach pamiętał trwałe obserwacje o mnie (np. „wolę hands-on niż teorię") i używał ich przy planowaniu.
23. Jako uczący się chcę, aby coach zarządzał tymi notatkami autonomicznie, ale żebym je widział i mógł edytować/usuwać (transparentność).
24. Jako uczący się chcę, aby transkrypty mocków **nie** trafiały do długoterminowej pamięci coacha — tylko ich wynik/destylat.
25. Jako uczący się chcę, aby coach przy doborze skilli dla zadania wybierał z istniejącego katalogu (z kontekstu), a nie wymyślał nowych.
26. Jako uczący się chcę widzieć na żywo przyznanie exp / level-up jako zdarzenie push, gdy weryfikacja się zakończy.
27. Jako uczący się chcę, by przyjęcie propozycji zadań od coacha tworzyło realne zadania (PRD 1) w stanie `TODO`.
28. Jako uczący się (jedyny użytkownik) chcę, aby wywołania AI i agent działały za moim logowaniem, bo stoją za nimi moje klucze i lokalny Claude CLI.

## Implementation Decisions

**Moduły:**
- `jobs` — `Job` (`type: PLANNING|EVALUATION|AGENT`, `status: QUEUED|RUNNING|DONE|FAILED`, `input/output` JSONB, `attempts/maxAttempts/nextRunAt/lockedAt`); runner; rejestr `JobHandler`. Zna **tylko** interfejs `JobHandler`.
- `coach` — `PlanningJobHandler`, `EvaluationJobHandler`, assembler kontekstu, `coach_notes`, `MockSession`/`MockMessage`.
- `ai` — **PORTY/adaptery**: `OpenRouterClient` (HTTP + SSE) i `ClaudeCliClient` (`ProcessBuilder`, `claude -p ... --output-format json`). **Jawnie rozdzielone**, bez wspólnego `LlmClient`. `AgentJobHandler` żyje tu.

**Runner (decyzje):**
- **Tabela-jako-kolejka + poller** `@Scheduled(~1–2s)`; claim batch przez `SELECT ... FOR UPDATE SKIP LOCKED` + `UPDATE status=RUNNING`; dispatch do `ThreadPoolTaskExecutor`.
- **Wybór handlera po `job.type`** (wzorzec strategii: `interface JobHandler { JobType type(); JobResult handle(Job); }`).
- **Limit współbieżności per typ** (AGENT = 1). **Retry** z backoffem (`nextRunAt`, `attempts/maxAttempts`). **Restart-safe** (stan w bazie); odzysk „zawieszonych" RUNNING po `lockedAt` + timeout.

**Kształty JSONB (typowane rekordy, natywny Hibernate 6):**
- PLANNING — in `{goalId, mode: DECOMPOSE|GENERATE_TASKS}`; out `{proposedGoals[]}` / `{proposedTasks[]}`.
- EVALUATION — in `{taskId, typeKey, artifact?, answers?}`; out `{score, expProposed, skillBreakdown[], feedback, passed}`.
- AGENT — in `{prompt, workdir, taskId?}`; out `{structuredFindings, raw}`.

**Pamięć coacha (decyzje):** hybryda — twardy stan z bazy (PRD 1) **+** `coach_notes` (lekka pamięć
narracyjna). Assembler składa prompt z: profil + cel strategiczny, aktywne drzewo, poziomy skilli
(+ **lista dostępnych `SkillDefinition`**), ostatnie N ukończonych/ocenionych zadań, `coach_notes`.
`coach_notes`: coach aktualizuje **autonomicznie**, są **jawne i edytowalne** w UI. Transkrypty mocków **nie** wchodzą do assemblera.

**Mock sessions (decyzje):** `MockSession` + **osobna tabela `MockMessage`**; zapis **inkrementalny**
(wiadomość po wiadomości — odporność na crash). Po sesji EVALUATION → `award`.

**Routing weryfikacji (gałąź AI; HONOR jest w PRD 1):** `submit` przy `verificationMethod ∈
{AUTO_QUIZ, AI_DIALOG, AI_ARTIFACT_REVIEW}` tworzy `EVALUATION` Job, `Task → IN_PROGRESS`;
po zakończeniu Joba — `GamificationService.award` (PRD 1) i `Task → DONE|REJECTED`.

**Styk z gamifikacją:** EVALUATION zwraca `expProposed`; **clamp/idempotencję/naliczenie robi
`GamificationService.award` (PRD 1)** — ten podsystem nie nalicza exp samodzielnie.

**SSE:** `SseEmitter` — globalny strumień zdarzeń (status Jobów, `exp-gain`, `level-up`) +
streaming odpowiedzi LLM (coach/mock) token po tokenie. OpenRouter SSE przepuszczane przez Spring do klienta.

**API (część AI/Jobów):**
```
POST /api/goals/{id}/plan              # → PLANNING job
POST /api/tasks/{id}/submit            # gałąź AI: → EVALUATION job (HONOR → PRD 1)
POST /api/tasks/{id}/mock/start        # → MockSession + SSE
POST /api/mock/{sessionId}/messages    # SSE stream
POST /api/coach/messages               # czat strategiczny, SSE
GET  /api/jobs/{id}
GET  /api/events                       # globalny SSE
GET/PUT/DELETE /api/coach-notes[/{id}]
```

## Testing Decisions

**Co czyni dobry test:** sprawdza **zachowanie zewnętrzne** — że Job przechodzi właściwe stany,
że handler produkuje poprawny `output` dla danego `input`, że wynik EVALUATION wywołuje `award`
z oczekiwanymi argumentami, że transkrypt mocka jest zapisany inkrementalnie. **Nie** testuje
prawdziwych wywołań LLM — porty AI są **mockowane** (deterministyczne odpowiedzi).

**Dwa seamy (per ustalenie):**
1. **Integracyjne przez REST API** — na **realnym Postgresie (Testcontainers)**, z **mockowanymi
   portami `ai`** (`OpenRouterClient`/`ClaudeCliClient` podstawione stubem zwracającym ustalony JSON).
   Ścieżki: `plan` → utworzenie Joba → przetworzenie przez runner → propozycje zapisane; `submit`
   (AI) → EVALUATION → `award` → exp naliczony; start mocka → zapis `MockMessage` → ocena → DONE.
   Poller w testach wyzwalany deterministycznie (bez czekania na harmonogram).
2. **Serwisowe (jednostkowe) z mockami** — `JobRunner`/claim-logic (kolejność, SKIP LOCKED,
   retry/backoff, limit współbieżności, odzysk RUNNING), assembler kontekstu (czy składa właściwe
   sekcje ze stanu), poszczególne `JobHandler`-y (mapowanie input→output przy zamockowanym porcie AI),
   routing `verificationMethod`. Porty AI, zegar i repozytoria mockowane.

**Moduły objęte testami:** `jobs`, `coach`, `ai` (przez kontrakt portów — adaptery testowane
płytko/kontraktowo, bez realnych wywołań zewnętrznych).
**Prior art:** brak (greenfield) — wzorzec jak w PRD 1 (Testcontainers + Mockito); dodatkowo
ustanawiamy konwencję **stubowania portów AI** i **deterministycznego wyzwalania pollera** w testach.

## Out of Scope

- Cały rdzeń domeny/gamifikacji (drzewo celów, `Task`, exp ledger+liczniki, `GamificationService`,
  config typów/skilli, HONOR) → **PRD 1** (konsumowany, nie reimplementowany).
- Frontend (Angular, ekrany, konsumpcja SSE po stronie UI, awatar wizualnie).
- Integracja Google Calendar (warstwa 2).
- Generowanie obrazu awatara przez AI.
- Konkretny dobór modeli OpenRouter per typ Joba (TBD T2), polityka progów retry/timeout (TBD T5),
  katalog roboczy agenta Claude CLI (TBD T1) — decyzje dostrojone przy implementacji, nie blokują kontraktu.

## Further Notes

- **Granica z PRD 1 jest jednokierunkowa:** podsystem AI/Jobów woła rdzeń (czyta `Goal`/`Task`,
  woła `award`), rdzeń nie zna AI. To trzyma `gamification` deterministycznym i w pełni testowalnym bez AI.
- OpenRouter i Claude CLI **celowo** nie są chowane za wspólnym interfejsem — to różne byty
  (chat vs agent-z-narzędziami); wspólnym kontraktem jest `JobHandler`.
- Pierwszy pionowy plasterek (milestone 1) łączy oba PRD: cel `ACTIVE` → `plan` (PLANNING) →
  zadanie MOCK → mock session → EVALUATION → `award` → exp/level-up przez SSE.
