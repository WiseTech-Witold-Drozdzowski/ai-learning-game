# issue-1 — Kręgosłup Jobów: runner + globalny SSE (tracer bullet)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `jobs`; runner + `JobHandler`; globalny strumień SSE).

## What to build
Uniwersalna, asynchroniczna abstrakcja Joba jako kolejka w bazie — kręgosłup, na którym
wiszą wszystkie późniejsze plasterki (PLANNING, EVALUATION, AGENT). Ten plasterek tnie przez
wszystkie warstwy end-to-end na **trywialnym** handlerze (no-op / echo), żeby zweryfikować
maszynerię runnera i podgląd na żywo, zanim dojdzie prawdziwa treść AI.

- Flyway: tabela `job(id, type, status, input JSONB, output JSONB, related_goal_id,
  related_task_id, attempts, max_attempts, next_run_at, locked_at, error, created_at,
  started_at, finished_at)` + indeks `job(status, next_run_at)` dla pollera. `input/output`
  przez natywny Hibernate 6 (`@JdbcTypeCode(SqlTypes.JSON)`) na typowane rekordy.
- Runner: poller `@Scheduled(~1–2s)`; claim batch przez `SELECT … FOR UPDATE SKIP LOCKED` +
  `UPDATE status=RUNNING, locked_at=now()`; dispatch do `ThreadPoolTaskExecutor`; wybór
  `JobHandler` po `job.type` (wzorzec strategii).
- Odporność: limit współbieżności per typ (konfigurowalny), retry z backoffem
  (`next_run_at`, `attempts/max_attempts`) → po przekroczeniu `FAILED`; restart-safe (stan w
  bazie); odzysk „zawieszonych" `RUNNING` po `locked_at` + timeout.
- Rejestr `JobHandler` (`interface JobHandler { JobType type(); JobResult handle(Job); }`) —
  `jobs` zna **tylko** ten interfejs. Trywialny handler do testów, żeby przejść cykl życia.
- `GET /api/jobs/{id}` — odczyt statusu Joba.
- `GET /api/events` — globalny `SseEmitter`; emituje przejścia statusu Jobów
  (`QUEUED → RUNNING → DONE|FAILED`).
- Konwencja testowa: **deterministyczne wyzwalanie pollera** (bez czekania na harmonogram).

## Acceptance criteria
- [ ] Migracja Flyway tworzy `job` + indeks `(status, next_run_at)`; aplikacja wstaje na czystym Postgresie.
- [ ] `input/output` round-trip: typowany rekord zapisany/odczytany jako JSONB.
- [ ] Poller claimuje QUEUED przez `FOR UPDATE SKIP LOCKED`, ustawia `RUNNING`, dispatchuje do handlera po `type`, kończy `DONE`.
- [ ] Nieudany Job jest ponawiany z backoffem (`next_run_at`, `attempts`) i po `max_attempts` przechodzi w `FAILED`.
- [ ] Limit współbieżności per typ jest respektowany (np. przy limicie 1 drugi Job czeka).
- [ ] `RUNNING` starszy niż timeout (po `locked_at`) jest odzyskiwany i ponownie kolejkowany.
- [ ] Po „restarcie" (nowy kontekst) poller zgarnia pozostawione `QUEUED`.
- [ ] `GET /api/jobs/{id}` zwraca aktualny status; `GET /api/events` emituje zdarzenia zmiany statusu.
- [ ] Test integracyjny (Testcontainers) przez REST + serwisowy (Mockito) dla claim-logic; poller wyzwalany deterministycznie.

## Blocked by
None — can start immediately.
