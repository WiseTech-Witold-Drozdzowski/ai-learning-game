# issue-5 — Silnik gamifikacji (ledger + liczniki + award)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `gamification`).

## What to build
Deterministyczny rdzeń naliczania exp: `ExpEvent` (append‑only) jako źródło prawdy plus
zdenormalizowane liczniki. Ten plasterek to **sam silnik** — kontrakt `GamificationService.award(...)`
— weryfikowany na seamie serwisowym (Mockito) + asercjach stanu w bazie. Wpięcie do `submit`
i endpointy odczytu (`/profile`, `/skills`) dochodzą w issue‑6.

- Flyway: `skill(key FK→skill_definition PK, level, exp)` oraz
  `exp_event(id, source_task_id, attempt_id, skill_key, amount, reason, created_at)` (append‑only),
  z indeksem `exp_event(source_task_id)` (idempotencja). Liczniki‑cache: `skill.exp`,
  `goal.exp_earned`, `career_profile.total_exp`/`level`.
- `GamificationService.award(...)` — w jednej transakcji:
  1. Clamp `expProposed` wg `TaskTypeDefinition` (np. `exp_base`; AI tylko proponuje — tu wejście stałe).
  2. Idempotencja po `source_task_id` (+ `attempt_id`) — ponowne wywołanie nie podwaja exp.
  3. Zapis `ExpEvent` per skill.
  4. Aktualizacja liczników: `skill.exp` + przeliczenie `level` (krzywa poziomów),
     bąbelkowanie `goal.exp_earned` walkiem po `parent_id`, `career_profile.total_exp` + `level`.
  5. Zwrot wyniku/zdarzenia domenowego (`exp-gain`/`level-up`) — sama emisja SSE należy do PRD‑2.

## Acceptance criteria
- [ ] Migracje tworzą `skill` i `exp_event` (append‑only) + indeks na `source_task_id`.
- [ ] `award` zapisuje `ExpEvent` per skill i aktualizuje `skill.exp`/`level` zgodnie z krzywą.
- [ ] Exp bąbelkuje po `parent_id`: `goal.exp_earned` rośnie na każdym piętrze aż do korzenia,
      `career_profile.total_exp`/`level` rosną.
- [ ] Idempotencja: dwukrotne `award` dla tego samego `source_task_id`(+`attempt_id`) → jeden zestaw
      `ExpEvent`, liczniki niepodwojone.
- [ ] Clamp: `expProposed` powyżej limitu typu jest przycięte do wartości z `TaskTypeDefinition`.
- [ ] Całość w jednej transakcji (event ↔ licznik spójne przy błędzie częściowym).
- [ ] Testy: serwisowe z Mockito (clamp, idempotencja, krzywa poziomów, bąbelkowanie) + integracyjny
      asertujący stan w bazie po `award`.

## Blocked by
`issue-4`
