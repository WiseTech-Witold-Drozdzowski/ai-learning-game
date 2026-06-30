# issue-7 — Audyt i przeliczalność exp (historia + rebuild)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `gamification`).

## What to build
Domknięcie obietnicy „uczciwego" licznika: prześledzenie, skąd wziął się exp, oraz odbudowa
liczników z ledgera (fundament pod eksperymentalne strojenie balansu — TBD T3).

- Odczyt historii: lista `ExpEvent` per skill (i/lub per zadanie) — „skąd te 340 exp na SQL".
  Ekspozycja przez rozszerzenie istniejącego odczytu (np. `GET /api/skills/{key}` z historią
  lub dedykowany sub‑zasób); kontrakt do dociągnięcia, bez nowej domeny.
- Rebuild liczników z ledgera: operacja przeliczająca `skill.exp`/`level`, `goal.exp_earned`,
  `career_profile.total_exp`/`level` wyłącznie z `exp_event`. Idempotentna; wynik identyczny jak
  liczniki utrzymywane inkrementalnie, gdy reguły balansu się nie zmieniły.

## Acceptance criteria
- [ ] Endpoint zwraca chronologiczną listę `ExpEvent` dla danego skilla (amount, reason, source_task_id,
      attempt_id, created_at) — tylko dane usera.
- [ ] Rebuild przeliczony z samego ledgera daje liczniki identyczne z utrzymywanymi inkrementalnie
      (test: award N zadań → rebuild → liczniki bez zmian).
- [ ] Rebuild jest idempotentny i bezpieczny do powtórzenia; nie tworzy nowych `ExpEvent`.
- [ ] Po (symulowanej) zmianie krzywej poziomów rebuild przelicza `level` spójnie z nową regułą.
- [ ] Testy: serwisowy z Mockito na logikę rebuildu + integracyjny (award → rebuild → porównanie liczników).

## Blocked by
`issue-6`
