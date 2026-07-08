# issue-7 — Pamięć coacha (coach_notes: autonomia + transparentność)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `coach`; `CoachNote`; wypełnienie seamu assemblera z issue-2).

## What to build
Lekka pamięć narracyjna, dzięki której coach „mnie pamięta" — trwałe obserwacje o użytkowniku
(np. „woli hands-on niż teorię"). Coach zarządza notatkami **autonomicznie**, ale są **jawne i
edytowalne** przez użytkownika (transparentność). Ten plasterek wypełnia sekcję `coach_notes`
assemblera, zostawioną jako seam w issue-2.

- Flyway: `coach_note(id, content, active, created_at, updated_at)`.
- `GET/PUT/DELETE /api/coach-notes[/{id}]` — użytkownik widzi, edytuje i kasuje notatki.
- Autonomiczne zarządzanie: podczas PLANNING/EVALUATION coach może tworzyć/aktualizować notatki
  **przez narzędzie** (a nie luźnym tekstem), na podstawie przebiegu pracy.
- Integracja z assemblerem (issue-2): aktywne `coach_notes` realnie wchodzą do promptu
  planowania/oceny.
- Destylat mocka: po sesji (issue-6) do `coach_notes` może trafić **wynik/destylat**, nigdy surowy transkrypt.

## Acceptance criteria
- [ ] Migracja tworzy `coach_note`; `GET/PUT/DELETE /api/coach-notes` pozwala użytkownikowi czytać, edytować i usuwać notatki.
- [ ] Coach tworzy/aktualizuje notatki autonomicznie przez narzędzie w trakcie PLANNING/EVALUATION (weryfikowane na stubie portu).
- [ ] Aktywne `coach_notes` są dołączane przez assembler do promptu (wypełniony seam z issue-2).
- [ ] Notatki nieaktywne (`active=false`) nie wchodzą do assemblera.
- [ ] Do `coach_notes` trafia destylat mocka, nie surowy transkrypt.
- [ ] Testy: serwisowy autonomicznego zapisu + integracji z assemblerem; integracyjny CRUD przez REST.

## Blocked by
`issue-6`
