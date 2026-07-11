# issue-6 — Mock interview (sesja + transkrypt inkrementalny + ocena)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `coach`; `MockSession`/`MockMessage`; specjalizacja `AI_DIALOG`).

## What to build
Interaktywna sesja, w której coach przepytuje użytkownika, rozmowa płynie strumieniowo, a
transkrypt jest zapisywany na bieżąco (odporność na crash). Po sesji AI ocenia odpowiedzi i
proponuje exp, który backend zatwierdza. To domknięcie milestone'u 1.

- Flyway: `mock_session(id, task_id, state, score, started_at, finished_at)` oraz **osobna**
  `mock_message(id, session_id, role, content, seq, created_at)`.
- `POST /api/tasks/{id}/mock/start` → tworzy `MockSession` + otwiera SSE (streaming reużywa seam z issue-3).
- `POST /api/mock/{sessionId}/messages` → tura rozmowy: wiadomość usera i odpowiedź coacha
  zapisywane **inkrementalnie** (wiadomość po wiadomości), odpowiedź streamowana tokenami.
- Zakończenie sesji → `EVALUATION` Job (`AI_DIALOG`): ocena transkryptu → `{score, expProposed,
  passed}` → `GamificationService.award` (clamp + idempotencja) → `Task → DONE|REJECTED` + SSE.
- Transkrypt jest **odczytywalny** dla wglądu i **nie wchodzi** do assemblera (długoterminowej
  pamięci coacha) — przeżywa go tylko wynik (score/ExpEvent) i ewentualny destylat (issue-7).

## Acceptance criteria
- [ ] Migracje tworzą `mock_session` i `mock_message`; `start` tworzy sesję i otwiera SSE.
- [ ] Każda tura zapisuje `mock_message` inkrementalnie (kolejne `seq`); przerwanie w połowie sesji zachowuje dotychczasowe wiadomości.
- [ ] Odpowiedź coacha jest streamowana tokenami (reuse seam SSE z issue-3).
- [ ] Zakończenie sesji tworzy `EVALUATION` Job oceniający transkrypt; wynik przechodzi przez `award` i ustawia stan `Task`.
- [ ] Zapisany transkrypt jest odczytywalny po sesji dla wglądu.
- [ ] Transkrypt mocka **nie** trafia do assemblera kontekstu (weryfikacja: prompt planowania/oceny go nie zawiera).
- [ ] Testy: integracyjny `start → zapis MockMessage → ocena → DONE` (Testcontainers, port AI stub) + serwisowy inkrementalnego zapisu i wykluczenia z assemblera.

## Blocked by
`issue-5`
