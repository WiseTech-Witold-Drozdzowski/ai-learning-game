# Technical Design — AI Career Coach

> Status: high-level design (v1). Decyzje architektoniczne i ich uzasadnienia.
> Towarzyszący dokument produktowy: [`PRODUCT_DESIGN.md`](./PRODUCT_DESIGN.md).

## 0. Stack

| Warstwa | Technologia |
|---|---|
| Backend | Java + Spring Boot (najnowsze) |
| Frontend | Angular (najnowsze) |
| Baza | PostgreSQL + Flyway |
| AI (chat) | OpenRouter (HTTP, SSE streaming) |
| AI (agent) | Claude CLI (subprocess) |
| Auth | Spring Security + Google OAuth2 |
| Deployment | Self-hosted (własny serwer autora), Docker dla Postgresa |

## 1. Topologia — monolit modularny

**Decyzja:** jeden Spring Boot, podzielony na wyraźne moduły. **Nie** mikroserwisy.

**Uzasadnienie:**
- Single-user → brak problemu skali uzasadniającego podział.
- Job runner jako wątki + tabela w bazie daje pełny wzorzec async/status/restart-safe
  bez kosztu sieci, serializacji i dwóch deploymentów.
- Claude CLI odpalany jako subprocess z tej samej aplikacji — działa identycznie w monolicie.
- W portfolio „dobrze zmodularyzowany monolit z czystymi granicami" > „mikroserwisy bez powodu"
  (to drugie czytane jest jako over-engineering).
- Granicę (osobny worker) można wyciągnąć później — moduły już rozdzielone, więc to refaktor.

### Moduły (pakiety)
```
goals         — drzewo celów, stany, akceptacja
tasks         — instancje zadań/questów, typy (config-driven)
jobs          — uniwersalna abstrakcja Job + runner (kolejka, poller, executor)
coach         — assembler kontekstu, logika planowania/oceny, coach_notes
ai            — OpenRouterClient (chat) ORAZ ClaudeCliClient (agent) — JAWNIE ROZDZIELONE
gamification  — exp, skille, poziomy, awatar (stan jako dane)
calendar      — integracja Google Calendar (warstwa 2)
auth          — Google OAuth2, email-whitelist
```

## 2. Job runner — uniwersalna abstrakcja

Wszystko, co trwa dłużej niż mrugnięcie, jest **Jobem** tego samego kształtu:

```
Job {
  id, type: PLANNING | EVALUATION | AGENT,
  status: QUEUED | RUNNING | DONE | FAILED,
  input (JSONB), output (JSONB),
  createdAt, startedAt, finishedAt,
  attempts, error
}
```

### Mechanika wykonania — tabela-jako-kolejka + poller

**Decyzja:** tabela `jobs` jest kolejką. `@Scheduled` poller co ~1–2s zgarnia `QUEUED`
(`SELECT ... FOR UPDATE SKIP LOCKED`), oznacza `RUNNING`, oddaje wykonanie do
`ThreadPoolTaskExecutor`. Wynik zapisywany z powrotem do wiersza.

**Uzasadnienie:**
- **Restart-safe za darmo** — stan żyje w bazie, nie w wątku. Po restarcie poller
  po prostu zgarnia to, co zostało; nie trzeba osobnej logiki recovery.
- Dokładnie ten wzorzec, który skaluje się na Postgres-as-a-queue / realny broker —
  migracja to wymiana jednego komponentu (mocna opowieść na interview).
- Naturalne miejsce na **limit współbieżności, timeouty, retry** — istotne zwłaszcza dla
  AGENT-jobów (Claude CLI: kosztowne, nie odpalać wielu naraz).
- `FOR UPDATE SKIP LOCKED` to kanoniczny wzorzec bezpiecznego zgarniania z tabeli-kolejki.

Minus (opóźnienie pollera do ~2s przed startem Joba) — nieistotny dla coachingu.

### Kontrakt Jobów — wzorzec strategii

```java
interface JobHandler {
    JobType type();
    JobResult handle(Job job);   // input → output, oba JSON
}
```

- `PlanningJobHandler`   → woła OpenRouter, generuje propozycje zadań, zapisuje.
- `EvaluationJobHandler` → woła OpenRouter z artefaktem/odpowiedzią usera, zwraca ocenę + exp.
- `AgentJobHandler`      → `ProcessBuilder` odpala `claude -p "<prompt>" --output-format json`,
                           parsuje **strukturalny** output (nie luźny tekst).

Poller wybiera handler po `job.type`. Cała logika AI ląduje za jedną abstrakcją —
„coach planuje" i „coach ocenia" to po prostu Joby, nie specjalne ścieżki.

## 3. Dwa providery AI — JAWNIE rozdzielone

**Decyzja:** `OpenRouterClient` i `ClaudeCliClient` **NIE** są chowane za wspólnym
interfejsem `LlmClient`.

**Uzasadnienie:** to różne byty — chat zwraca tekst, agent wykonuje narzędzia i może
modyfikować pliki. Wspólny interfejs byłby fałszywą abstrakcją. Wspólnym kontraktem
jest `JobHandler`; *pod* nim każdy handler woła to, czego potrzebuje.

| | OpenRouter (chat) | Claude CLI (agent) |
|---|---|---|
| Charakter | synchroniczny, request→response, streaming SSE | długo-żyjący, autonomiczny, używa narzędzi |
| Wywołanie | HTTP client + SSE | `ProcessBuilder`, `--output-format json` |
| Joby | PLANNING, EVALUATION | AGENT |

**Output Claude CLI:** zawsze `--output-format json` (lub stream-json), parsowany
strukturalnie do `job.output`.

> **TBD:** katalog roboczy agenta — izolacja per job (`workspace/jobs/{jobId}/`)
> vs stały katalog (np. lokalny klon repo portfolio). Do ustalenia przy implementacji AGENT.

## 4. Pamięć coacha — hybryda

**Decyzja:** twardy **stan strukturalny (z bazy)** + lekka **pamięć narracyjna** (`coach_notes`),
z wyraźnym prymatem stanu strukturalnego.

**Uzasadnienie:**
- Stan strukturalny to **prawda gry** — exp/skille/postęp liczy baza, nie wspomnienia LLM.
- Czysty stan jest „zimny" — coach nie pamiętałby preferencji/słabości/kontekstu.
- Czyste transkrypty są drogie tokenowo, rozjeżdżają się i i tak wymagają destylacji.
- Hybryda: tania, kontrolowalna, daje efekt „on mnie pamięta".

### Assembler kontekstu
Przed każdym Jobem PLANNING/EVALUATION buduje prompt z:
1. profil + cel strategiczny,
2. aktywne drzewo celów/zadań,
3. poziomy skilli,
4. ostatnie N ukończonych/ocenionych zadań,
5. `coach_notes`.

Transkrypt pojedynczej rozmowy (np. trwającego mocka) żyje **tylko w obrębie tej sesji** —
nie jest pamięcią długoterminową.

### Zarządzanie `coach_notes`
**Decyzja:** coach aktualizuje notatki **autonomicznie** (przez dedykowane narzędzie),
ale są **jawne i edytowalne** w UI (użytkownik widzi, może skasować/poprawić).

**Uzasadnienie:** human-in-the-loop rezerwujemy dla decyzji o wysokiej stawce
(kierunek kariery). Notatka jest tania i odwracalna — bramka na każdej z osobna to tarcie
bez wartości. Pełna autonomia bez wglądu byłaby niekontrolowalna. To zastosowanie zasady
**akceptacja skaluje się ze stawką decyzji**.

## 5. Baza danych — PostgreSQL + Flyway

**Decyzja:** PostgreSQL w Dockerze, migracje Flyway.

**Uzasadnienie:**
- `SELECT ... FOR UPDATE SKIP LOCKED` — kanoniczny wzorzec tabeli-jako-kolejki (patrz §2).
- **JSONB** dla zmiennych kształtów `job.input`/`job.output` (różne per typ) i `coach_notes` —
  bez sztywnego schematu na wszystko.
- Standard „dorosłego" backendu; lepiej w portfolio niż H2.
- Flyway = wersjonowanie schematu od początku, porządek + plus do portfolio.

### Config typów zadań — hybryda (seed → baza)
**Decyzja:** definicje typów zadań ładowane z **YAML jako seed/defaults** przy starcie
(loader wrzuca do bazy), a **baza jest źródłem prawdy** i pozwala edytować w runtime.

**Uzasadnienie:** typy zmieniasz rzadko (mechanika, nie dane operacyjne), więc YAML w gicie
daje przejrzystą historię; jednocześnie runtime-edycja nie wymaga restartu. Mapowanie YAML →
obiekty przez `@ConfigurationProperties`.

## 6. Komunikacja async — SSE

**Decyzja:** Server-Sent Events (`SseEmitter` w Spring, `EventSource` w Angular) jako
główny mechanizm zdarzeń. Nie WebSocket, nie polling.

**Uzasadnienie:**
- Komunikacja z natury jednokierunkowa (serwer → klient o postępie/tokenach) — WebSocket zbędny.
- Jeden mechanizm, dwa zastosowania: **streaming odpowiedzi LLM** token-po-tokenie
  **i** **push statusów Jobów** (`RUNNING → DONE`).
- OpenRouter i tak streamuje SSE — przepuszczamy to naturalnie przez Spring do Angulara.
- Natywne wsparcie po obu stronach, bez bibliotek.

Polling pozostaje jako ewentualny fallback, nie domyślny.

## 7. Auth — Google OAuth2

**Decyzja:** logowanie przez Google (`spring-boot-starter-oauth2-client`),
dostęp ograniczony **email-whitelist** (tylko adres autora).

**Uzasadnienie:**
- Aplikacja wystawiona na publiczny URL (self-hosted) → musi być zamknięta;
  za nią stoją klucze OpenRouter i Claude CLI (potrafi odpalać procesy).
- **Zabija dwie pieczenie** — logowanie + autoryzacja Google Calendar w jednym flow OAuth.
- Whitelist = de facto single-user, zero zarządzania hasłami.
- „OAuth2 + Spring Security" to mocny punkt portfolio.

## 8. Frontend (Angular)

### Ekrany (priorytet MVP — patrz PRODUCT_DESIGN §6)
1. Dashboard / „dziś" *(MVP)*
2. Drzewo celów / mapa kariery *(MVP)*
3. Widok zadania / sesja *(MVP)*
4. Rozmowa z coachem
5. Profil / awatar / skille

Globalnie: **wskaźnik Jobów w toku** (przez SSE).

### Awatar
Statyczny na MVP (obrazek/ikona + paski expa/poziomy/odznaki). Stan awatara trzymany jako
**dane** (poziom, odblokowane atrybuty, tier), nie zaszyty w grafice → późniejsza podmiana
renderera na animowany (Lottie/Pixi) to wymiana komponentu, nie modelu.

## 9. Deployment

- **Self-hosted** na serwerze autora. Live-URL do portfolio.
- Claude CLI zainstalowane i zautoryzowane **na hoście** (dlatego subprocess działa).
- Postgres przez Docker (`docker-compose`); aplikacja ma dostęp do `claude` w PATH.

## 10. Otwarte decyzje (TBD — nie blokują MVP)

| # | Temat | Notatka |
|---|---|---|
| T1 | Katalog roboczy agenta Claude CLI | izolacja per job vs stały klon repo — przy implementacji AGENT |
| T2 | Modele OpenRouter per typ Joba | tani model do QUIZ, mocny do MOCK — detal konfiguracyjny |
| T3 | Krzywa expa / ekonomia poziomów | strojenie eksperymentalne, nie projektowane z góry |
| T4 | Kierunek sync Google Calendar | jednokierunkowo app→kalendarz vs dwukierunkowo — warstwa 2 |
| T5 | Polityka retry/timeout Jobów | zwłaszcza AGENT — doprecyzowanie przy implementacji runnera |

## 11. Najcieńszy pionowy plasterek (pierwszy milestone)

> Jedna ścieżka end-to-end, która już *jest* tą wizją:

```
Cel "przygotowanie behawioralne" (AKTYWNY)
  → coach generuje 3 zadania (PLANNING job)
    → robisz jedno zadanie typu MOCK
      → AI przepytuje i ocenia (EVALUATION job, SSE)
        → backend przyznaje exp na skillu "Behavioral"
          → awatar/pasek rośnie
```

Bez kalendarza, bez AGENT-joba, bez wielu ścieżek. Gdy to działa i daje frajdę —
dokładamy resztę (to wtedy „więcej tego samego", nie nowe ryzyko).
