# issue-2 — Port OpenRouter + assembler kontekstu + PLANNING

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduły `ai`, `coach`; `PlanningJobHandler`).

## What to build
Pierwszy prawdziwy typ Joba: coach rozplanowuje aktywny cel. Plasterek wprowadza port AI do
chatu, assembler kontekstu (żeby rady nie były generyczne) i handler PLANNING, a wynik —
propozycje — domyka pętlę przez akceptację i utworzenie realnych zadań PRD 1.

- `ai`: port `OpenRouterClient` (HTTP, wywołanie z ustrukturyzowaną odpowiedzią). **Jawnie
  osobny** byt — bez wspólnego `LlmClient`. W testach **mockowany** (deterministyczny JSON).
- `coach`: **assembler kontekstu** — składa prompt z twardego stanu bazy: profil + cel
  strategiczny, aktywne drzewo celów/zadań, poziomy skilli **+ lista dostępnych
  `SkillDefinition`** (coach wybiera z katalogu, nie wymyśla), ostatnie N ukończonych/ocenionych
  zadań, oraz **sekcja `coach_notes`** (na razie pusta — seam pod issue-7). Transkrypty mocków tu nie wchodzą.
- `PlanningJobHandler` (typ `PLANNING`): in `{goalId, mode: DECOMPOSE | GENERATE_TASKS}`;
  out `{proposedGoals[]}` lub `{proposedTasks[]}`. Woła OpenRouter przez assembler.
- `POST /api/goals/{id}/plan` → tworzy `PLANNING` Job (async, status przez SSE z issue-1).
- Zamknięcie pętli propozycji: **propozycje celów** wymagają akceptacji użytkownika
  (powstają jako `PROPOSED`); **propozycje zadań** po przyjęciu tworzą realne `Task` w stanie
  `TODO` (PRD 1). Coach generuje zadania **tylko** pod celem w stanie `ACTIVE`.

## Acceptance criteria
- [ ] Port `OpenRouterClient` istnieje jako osobny interfejs (nie schowany za wspólnym `LlmClient`) i jest mockowalny.
- [ ] Assembler składa prompt ze wszystkich sekcji stanu, w tym listy dostępnych `SkillDefinition`; transkrypty mocków nie są dołączane.
- [ ] `POST /api/goals/{id}/plan` tworzy Job `PLANNING` i zwraca jego id; runner (issue-1) go przetwarza.
- [ ] `PlanningJobHandler` mapuje `input` → `output` przy zamockowanym porcie: `DECOMPOSE` → `proposedGoals[]`, `GENERATE_TASKS` → `proposedTasks[]`.
- [ ] Próba `GENERATE_TASKS` pod celem spoza `ACTIVE` jest odrzucana.
- [ ] Przyjęcie propozycji zadań tworzy realne `Task` w stanie `TODO`; propozycje celów lądują jako `PROPOSED` (wymagają akceptacji).
- [ ] Testy: integracyjny przez REST (Testcontainers, port AI zastąpiony stubem) na ścieżce `plan → Job → propozycje zapisane` + serwisowy assemblera i handlera.

## Blocked by
`issue-1`
