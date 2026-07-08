# issue-4 — Routing weryfikacji + generyczny EVALUATION + award + SSE exp-gain/level-up

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `coach`; `EvaluationJobHandler`; styk z gamifikacją PRD 1).

## What to build
Pełna ścieżka oceny pracy przez AI i naliczenia exp — druga „inteligencja" po PLANNING i
domknięcie milestone'u wokół weryfikacji. Ten plasterek buduje **generyczny** mechanizm
EVALUATION i demonstruje go na najprostszej konkretnej metodzie `AI_ARTIFACT_REVIEW`
(artefakt na wejściu → ocena na wyjściu). QUIZ i mock to specjalizacje w kolejnych issue.

- Routing w `POST /api/tasks/{id}/submit` wg `TaskTypeDefinition.verificationMethod`: gałąź AI
  (`AI_ARTIFACT_REVIEW` w tym plasterku) → tworzy `EVALUATION` Job, `Task → IN_PROGRESS`.
  (HONOR / HONOR_WITH_PROOF to synchroniczna ścieżka PRD 1 — nietykana tutaj.)
- `EvaluationJobHandler` (typ `EVALUATION`): in `{taskId, typeKey, artifact?, answers?}`;
  out `{score, expProposed, skillBreakdown[], feedback, passed}`. Woła OpenRouter (mock w testach).
- Po zakończeniu Joba: `GamificationService.award(...)` (PRD 1) **clampuje** `expProposed`,
  zapewnia **idempotencję** (po `sourceTaskId` + `attemptId`) i nalicza exp — ten podsystem
  **nie nalicza sam**. Następnie `Task → DONE` (passed) lub `REJECTED` (failed).
- SSE: po `award` globalny strumień (`GET /api/events`) emituje `exp-gain` i ewentualnie `level-up`.

## Acceptance criteria
- [ ] `submit` przy `AI_ARTIFACT_REVIEW` tworzy `EVALUATION` Job i przełącza `Task → IN_PROGRESS`.
- [ ] `EvaluationJobHandler` przy zamockowanym porcie mapuje `input` → `{score, expProposed, skillBreakdown, feedback, passed}`.
- [ ] Po zakończeniu Joba wołany jest `GamificationService.award` z `expProposed`; exp jest **clampowany** przez award (AI tylko proponuje).
- [ ] Ponowne wykonanie tego samego EVALUATION (np. po retry) **nie podwaja** exp (idempotencja po `sourceTaskId`/`attemptId`).
- [ ] `passed=true` → `Task → DONE`; `passed=false` → `Task → REJECTED`.
- [ ] Po `award` na `GET /api/events` pojawia się `exp-gain` (i `level-up`, gdy próg poziomu przekroczony).
- [ ] Testy: integracyjny `submit (AI) → EVALUATION → award → exp naliczony` (Testcontainers, port AI stub) + serwisowy routingu i handlera.

## Blocked by
`issue-3`
