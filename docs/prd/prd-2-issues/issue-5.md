# issue-5 — QUIZ generowany i auto-oceniany (AUTO_QUIZ)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `coach`; specjalizacja EVALUATION dla `AUTO_QUIZ`).

## What to build
Pierwsza specjalizacja generycznego EVALUATION (issue-4): zadanie typu QUIZ, którego treść
generuje AI, a odpowiedzi są **automatycznie** oceniane, z exp zależnym od % poprawnych.

- Generowanie quizu przez AI (OpenRouter, mock w testach) — zestaw pytań z kluczem odpowiedzi
  jako część `input`/stanu zadania.
- Routing `submit` dla `verificationMethod = AUTO_QUIZ` → `EVALUATION` Job z `answers` usera.
- `EvaluationJobHandler` w wariancie QUIZ: liczy % poprawnych deterministycznie względem klucza,
  ustawia `score` i `expProposed` **skalowany procentem** (`expScaleByScore`), `passed` wg progu.
- Naliczenie exp jak w issue-4: przez `GamificationService.award` (clamp + idempotencja) i SSE.

## Acceptance criteria
- [ ] Quiz jest generowany przez AI (mock w testach) i utrwalony wraz z kluczem odpowiedzi.
- [ ] `submit` z odpowiedziami przy `AUTO_QUIZ` tworzy `EVALUATION` Job z `answers`.
- [ ] Ocena jest deterministyczna: % poprawnych → `score`; `expProposed` skaluje się z procentem.
- [ ] 100% i 0% poprawnych dają skrajne, przewidywalne wartości exp; próg `passed` respektowany.
- [ ] Naliczenie przechodzi przez `award` (clamp + idempotencja) i emituje `exp-gain`/`level-up` na SSE.
- [ ] Testy: serwisowy oceniania (procenty, progi, skalowanie exp) + integracyjny ścieżki QUIZ end-to-end.

## Blocked by
`issue-4`
