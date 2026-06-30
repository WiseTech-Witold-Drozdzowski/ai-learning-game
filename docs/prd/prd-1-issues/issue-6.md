# issue-6 — Submit HONOR + odczyt profilu i skilli

## Parent
`docs/prd/prd-1-core-domain.md` (moduły `tasks` + `gamification`).

## What to build
Pierwsza pełna, user‑widoczna pętla nagrody: oznaczam zadanie HONOR jako zrobione i natychmiast
widzę wzrost profilu/skilli — bez AI, bez Joba.

- `POST /api/tasks/{id}/submit` — routing wg `TaskTypeDefinition.verificationMethod`:
  - **HONOR / HONOR_WITH_PROOF** → synchronicznie, bez Joba: od razu `GamificationService.award(...)`
    (issue‑5) + `Task → DONE`, `task.exp_awarded` ustawione.
  - `HONOR_WITH_PROOF` przyjmuje opcjonalny dowód (link/screenshot) zapisywany w `task.artifact`;
    jeśli `requires_artifact` = true, brak dowodu jest odrzucany.
  - Pozostałe metody (AUTO_QUIZ/AI_*) → poza zakresem PRD‑1 (gałąź Job w PRD‑2); tu zwracają jasny
    błąd „nieobsługiwane w tej wersji".
- Odczyt stanu gamifikacji:
  - `GET /api/profile` — total exp, level, awatar (`avatarState`), skille usera.
  - `GET /api/skills` — poziom i exp każdego skilla.

## Acceptance criteria
- [ ] `submit` zadania HONOR: w jednym żądaniu task przechodzi do DONE, powstają `ExpEvent`,
      liczniki i `task.exp_awarded` zaktualizowane — bez tworzenia Joba.
- [ ] `HONOR_WITH_PROOF`: dowód trafia do `task.artifact`; gdy `requires_artifact` = true, brak dowodu → błąd.
- [ ] Ponowny `submit` tego samego zadania nie podwaja exp (idempotencja z issue‑5) i nie psuje stanu DONE.
- [ ] `GET /api/profile` i `GET /api/skills` po submicie pokazują wzrost (skill, total, level) — tylko dane usera.
- [ ] Submit metodą AI/AUTO_QUIZ zwraca czytelny błąd „poza zakresem", nie nalicza exp.
- [ ] Test integracyjny end‑to‑end: cel ACTIVE → task HONOR → start → submit → wzrost na skillu i bąbelkowanie
      widoczne przez `/profile`/`/goals`.

## Blocked by
`issue-5`
