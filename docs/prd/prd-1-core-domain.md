# PRD 1 — Rdzeń domeny i silnik gamifikacji (Backend)

> Część 1 z 2. Deterministyczny rdzeń bez AI. Towarzyszy: [`prd-2-ai-jobs.md`](./prd-2-ai-jobs.md).
> Źródła decyzji: `docs/design/PRODUCT_DESIGN.md`, `docs/design/TECHNICAL_DESIGN.md`, `docs/design/BACKEND_DESIGN.md`.
> Uwaga: greenfield — brak istniejącego kodu/testów; PRD oparty o design docs + sesje grill-me.

## Problem Statement

Jako uczący się chcę, by mój rozwój zawodowy był uporządkowany w strukturę celów i konkretnych
zadań oraz nagradzany w sposób mierzalny i **uczciwy** (punkty, poziomy, skille), tak żebym
widział realny postęp i miał motywację do regularnej pracy. Dziś nauka i przygotowanie do
rekrutacji są rozproszone, nie mam jednego miejsca z celem strategicznym rozbitym na kroki,
ani wiarygodnego licznika postępu, któremu mogę ufać (że punkty nie są „z powietrza").

## Solution

Backendowy silnik, który modeluje **drzewo celów** (cel strategiczny → levele → zadania) ze
stanami i bramką akceptacji na wyższych piętrach, oraz **gamifikację** opartą na rejestrze
zdarzeń exp (append-only) ze zdenormalizowanymi licznikami. Każde ukończone zadanie przyznaje
exp na konkretnych skillach, exp bąbelkuje w górę drzewa, a profil/awatar rośnie z sumy.
Punkty zawsze nalicza backend (nie klient, nie AI), z możliwością audytu „skąd ten exp".
Ta część obejmuje też zadania weryfikowane natychmiastowo (HONOR) — bez udziału AI.

## User Stories

1. Jako uczący się chcę utworzyć cel strategiczny (np. „Zostać Senior Java Dev"), aby nadać kierunek całej nauce.
2. Jako uczący się chcę, aby cel strategiczny miał stan (`PROPOSED/ACCEPTED/ACTIVE/CLOSED`), abym świadomie go aktywował, zanim zacznie generować pracę.
3. Jako uczący się chcę akceptować cele wyższego poziomu (strategiczne i levele), bo to decyzje o wysokiej stawce dotyczące kierunku kariery.
4. Jako uczący się chcę, aby pod aktywnym celem mogły istnieć levele (cele pośrednie), abym widział kamienie milowe.
5. Jako uczący się chcę widzieć całe drzewo celów z ich stanami, aby rozumieć strukturę swojej „kampanii".
6. Jako uczący się chcę zamykać cele (`CLOSED`), gdy są osiągnięte lub porzucone, aby drzewo odzwierciedlało rzeczywistość.
7. Jako uczący się chcę, aby zadania (questy) były przypisane do konkretnego celu, abym wiedział, czemu służą.
8. Jako uczący się chcę, aby każde zadanie miało typ (READING, QUIZ, MOCK, ARTIFACT, EXTERNAL, …), bo typ determinuje sposób weryfikacji i nagrodę.
9. Jako uczący się chcę rozpocząć zadanie (`TODO → IN_PROGRESS`), aby zaznaczyć, że nad nim pracuję.
10. Jako uczący się chcę oznaczyć zadanie typu HONOR jako zrobione i natychmiast dostać exp, bez czekania na jakąkolwiek ocenę.
11. Jako uczący się chcę, aby zadanie EXTERNAL pozwalało dołączyć opcjonalny dowód (link/screenshot) przy oznaczaniu „zrobione".
12. Jako uczący się chcę, aby exp był naliczany przez backend, a nie przez klienta, abym ufał, że nie da się go „naklikać" w obejście reguł.
13. Jako uczący się chcę, aby wysokość exp zależała od typu zadania (twardość weryfikacji), tak by realny wysiłek był nagradzany mocniej niż „przeczytane".
14. Jako uczący się chcę, aby exp z zadania trafiał na konkretne skille (np. „Behavioral", „SQL"), aby rozwój był rozbity na osie kompetencji.
15. Jako uczący się chcę, aby skille pochodziły z kuratorowanego katalogu, a nie były tworzone losowo, by postęp kumulował się na kilku znaczących osiach.
16. Jako uczący się chcę widzieć poziom i exp każdego skilla, aby wiedzieć, gdzie jestem silny, a gdzie słaby.
17. Jako uczący się chcę, aby exp z zadania bąbelkował w górę drzewa celów (level, cel strategiczny), aby paski wyższych poziomów rosły wraz z pracą u podstaw.
18. Jako uczący się chcę widzieć łączny exp i poziom mojego profilu/awatara, jako syntetyczną miarę całego postępu.
19. Jako uczący się chcę móc prześledzić historię „skąd wziął się exp na danym skillu" (lista zdarzeń), aby gamifikacja była przejrzysta i uczciwa.
20. Jako uczący się chcę, aby ponowne lub omyłkowe naliczenie tego samego zadania nie podwajało exp (idempotencja), aby liczniki pozostały wiarygodne.
21. Jako uczący się chcę, aby zadanie po negatywnej weryfikacji wracało do `TODO` (a nie było martwym `REJECTED`), abym mógł spróbować ponownie z zachowaną historią prób.
22. Jako uczący się chcę, aby stan końcowy zadania (DONE/REJECTED) ustawiał wyłącznie backend, aby źródło prawdy było jedno.
23. Jako uczący się chcę móc dodać/edytować definicje typów zadań w runtime (bez restartu), gdy zechcę dostroić mechanikę.
24. Jako uczący się chcę móc dodać/edytować katalog skilli w runtime, gdy pojawi się nowa kompetencja, którą chcę śledzić.
25. Jako uczący się chcę, aby definicje typów i skilli miały sensowne wartości domyślne (seed) już przy pierwszym uruchomieniu, abym nie zaczynał od pustki.
26. Jako uczący się chcę móc filtrować zadania (np. po stanie, po celu), aby skupić się na tym, co istotne.
27. Jako uczący się chcę, aby exp i poziomy były odporne na zmianę reguł balansu (możliwość przeliczenia z historii), bo ekonomia będzie strojona eksperymentalnie.
28. Jako uczący się (jedyny użytkownik) chcę, aby moje dane były odizolowane za logowaniem, bo aplikacja jest wystawiona publicznie.

## Implementation Decisions

**Moduły (warstwowo per moduł: controller/service/domain/repository):**
- `goals` — rekursywny `Goal` (adjacency list, `parentId`, `kind: STRATEGIC|LEVEL`), stany `PROPOSED→ACCEPTED→ACTIVE→CLOSED`, akceptacja, niezmienniki (STRATEGIC bez rodzica; zadania tylko pod celem).
- `tasks` — `Task` (osobna encja), stany `TODO→IN_PROGRESS→DONE|REJECTED`; `REJECTED→TODO` (retry); routing weryfikacji wg `verificationMethod` (tu tylko gałąź HONOR — synchroniczna).
- `gamification` — `ExpEvent` (append-only, źródło prawdy), liczniki-cache (`Skill.exp`, `Goal.expEarned`, `CareerProfile.totalExp`), `GamificationService.award(...)`.
- `config` — `TaskTypeDefinition` + `SkillDefinition`: seed z YAML → baza (źródło prawdy), edycja w runtime; mapowanie YAML przez `@ConfigurationProperties`.
- `auth` — Google OAuth2 + email-whitelist (Spring Security) — wspólne dla obu PRD.

**Domena / schemat (zarys, Flyway):** `users`, `career_profile(avatar_state JSONB)`, `goal`,
`task(skill_keys[])`, `task_type_definition`, `skill_definition`, `skill`, `exp_event` (append-only).
Indeksy: `task(state)`, `task(goal_id)`, `goal(parent_id)`, `exp_event(source_task_id)` (idempotencja).

**Decyzje kluczowe (z grill-me):**
- Drzewo: **rekursywny `Goal`**, `Task` osobno.
- Stany `Task`: **4 stany**; faza weryfikacji nie jest stanem Taska (modeluje ją `Job.status` — patrz PRD 2); stan końcowy ustawia backend; `REJECTED` nie jest terminalny.
- Skille: **kuratorowany config**, nie dynamiczne.
- Exp: **ledger (`ExpEvent`) źródłem prawdy + liczniki-cache**; idempotencja po `sourceTaskId` (+ `attemptId`); bąbelkowanie = walk po `parentId` w jednej transakcji.
- Typy zadań i skille: **seed YAML → baza**, runtime-editable.
- HONOR: **synchronicznie, bez Joba** — `submit` od razu woła `GamificationService.award` i ustawia `DONE`.
- JSONB: **natywny Hibernate 6** (`@JdbcTypeCode(SqlTypes.JSON)`) na typowane rekordy; JSON tylko dla danych nieprzeszukiwanych relacyjnie (`avatarState`).

**Kontrakt `GamificationService.award(...)` (wspólny punkt styku z PRD 2):** w jednej transakcji —
(1) clamp `expProposed` wg `TaskTypeDefinition`; (2) idempotencja po `sourceTaskId`/`attemptId`;
(3) zapis `ExpEvent` per skill; (4) aktualizacja liczników + przeliczenie poziomów + bąbelkowanie;
(5) sygnał zdarzenia (`exp-gain`/`level-up`) — emisja przez SSE należy do PRD 2, tu tylko zwracany wynik/zdarzenie domenowe.

**API (część rdzenia):**
```
GET  /api/me
GET  /api/goals                 POST /api/goals
POST /api/goals/{id}/accept     POST /api/goals/{id}/close
GET  /api/tasks?due=&state=&goalId=
POST /api/tasks/{id}/start
POST /api/tasks/{id}/submit     # gałąź HONOR: sync award + DONE
GET  /api/tasks/{id}
GET  /api/profile               GET /api/skills
GET/PUT /api/task-types[/{key}] GET/PUT /api/skill-defs[/{key}]
```
(`POST /goals/{id}/plan` oraz gałąź AI w `submit` → PRD 2.)

## Testing Decisions

**Co czyni dobry test:** sprawdza **zachowanie zewnętrzne** (kontrakt API, zmianę stanu w bazie,
poprawność naliczeń exp), nie szczegóły implementacji. Test nie zna struktury klas — zna wejście
(request/stan) i wyjście (response/stan po operacji).

**Dwa seamy (per ustalenie):**
1. **Integracyjne przez REST API** — uderzają w endpointy, na **realnym Postgresie przez Testcontainers**.
   Weryfikują pełne ścieżki: utworzenie i akceptacja celu, dodanie/start/submit zadania HONOR,
   naliczenie i bąbelkowanie exp, idempotencję ponownego submitu, powrót `REJECTED→TODO`,
   runtime-edycję configu. To główny seam — najwyższy punkt, jeden na ścieżkę.
2. **Serwisowe (jednostkowe) z mockami** — dla logiki bogatej w reguły, izolowanej od HTTP/bazy:
   `GamificationService` (clamp, idempotencja, krzywa poziomów, bąbelkowanie po drzewie),
   walidacja niezmienników `goals`, routing `verificationMethod`. Współpracownicy (repozytoria,
   zegar) mockowani.

**Moduły objęte testami:** `goals`, `tasks`, `gamification`, `config`.
**Prior art:** brak (greenfield) — ten PRD ustanawia wzorzec: Testcontainers Postgres dla
`@SpringBootTest`/slice integracyjnych + czyste testy serwisów z Mockito.

## Out of Scope

- Cała maszyneria AI i Jobów (runner, OpenRouter, Claude CLI, assembler kontekstu, mock sessions,
  PLANNING/EVALUATION/AGENT) → **PRD 2**.
- SSE / streaming (emisja zdarzeń gamifikacji „na żywo") → **PRD 2** (rdzeń zwraca zdarzenia domenowe).
- Frontend (Angular, ekrany, awatar wizualnie) — poza zakresem obu PRD na tym etapie.
- Integracja Google Calendar (warstwa 2).
- Strojenie krzywej expa/ekonomii (TBD T3) — tu tylko mechanizm umożliwiający przeliczenie.

## Further Notes

- Awatar w tej części to **dane** (`avatarState`, poziom, exp), bez warstwy wizualnej.
- `auth` (Google OAuth2 + whitelist) jest wspólny dla obu PRD — implementacyjnie raz, opisany w obu dla kompletności kontekstu.
- Ledger (`ExpEvent`) celowo umożliwia „rebuild" liczników — to fundament pod eksperymentalne strojenie balansu bez utraty spójności.
