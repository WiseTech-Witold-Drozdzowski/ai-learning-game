# issue-3 — Drzewo celów (rekursywny Goal, stany, akceptacja)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `goals`).

## What to build
Rekursywne drzewo celów ze stanami i bramką akceptacji — „kampania" użytkownika.

- Flyway: `goal(id, parent_id FK→goal, kind, title, description, state, created_by,
  order_index, exp_earned, created_at, updated_at)`. Indeks `goal(parent_id)`.
  `kind` ∈ {STRATEGIC, LEVEL}; `state` ∈ {PROPOSED, ACCEPTED, ACTIVE, CLOSED}.
- Niezmienniki (walidowane w serwisie, nie tylko w DB): STRATEGIC ma `parent_id = null`;
  LEVEL ma rodzica; cel powstaje jako PROPOSED.
- Maszyna stanów `PROPOSED → ACCEPTED → ACTIVE → CLOSED`; akceptacja to świadoma decyzja usera
  (cele wyższego poziomu). Niedozwolone przejścia odrzucane.
- API:
  - `POST /api/goals` — user tworzy cel strategiczny.
  - `GET /api/goals` — całe drzewo z zagnieżdżeniem i stanami.
  - `POST /api/goals/{id}/accept`, `POST /api/goals/{id}/close`.

`exp_earned` zostaje na 0 — bąbelkowanie dochodzi w issue‑5.

## Acceptance criteria
- [ ] Migracja tworzy `goal` z indeksem na `parent_id`; aplikacja wstaje.
- [ ] `POST /api/goals` tworzy cel STRATEGIC w stanie PROPOSED; próba podania `parentId` dla
      STRATEGIC jest odrzucona (niezmiennik).
- [ ] `GET /api/goals` zwraca drzewo z poprawnym zagnieżdżeniem i stanami (tylko dane usera).
- [ ] `accept`/`close` realizują dozwolone przejścia; niedozwolone (np. close z PROPOSED, jeśli
      tak ustalono) zwracają błąd, stan w bazie się nie zmienia.
- [ ] Testy: integracyjny przez REST (utworzenie → akceptacja → close) + serwisowy z Mockito na
      niezmienniki i walidację przejść.

## Blocked by
`issue-2`
