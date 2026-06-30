# issue-4 — Zadania pod celem + cykl życia (bez exp)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `tasks`).

## What to build
Encja `Task` jako liść drzewa oraz jej maszyna stanów — bez naliczania exp (to issue‑6).

- Flyway: `task(id, goal_id FK, type_key FK→task_type_definition, title, description, state,
  skill_keys[], artifact, exp_awarded, scheduled_for, verification_job_id, created_at,
  updated_at)`. Indeksy `task(state)`, `task(goal_id)`.
- Maszyna stanów `TODO → IN_PROGRESS → DONE | REJECTED`, z `REJECTED → TODO` (retry).
  Stan końcowy (DONE/REJECTED) ustawia **wyłącznie backend** — klient go nie podaje.
  W PRD‑1 wyzwalacz przejścia do DONE/REJECTED przychodzi z issue‑6 (HONOR); tu modelujemy
  samą zdolność maszyny stanów + retry, bez ścieżki AI.
- Niezmiennik: zadanie powstaje tylko pod istniejącym celem; `type_key`/`skill_keys[]` muszą
  wskazywać na istniejące wpisy katalogu (issue‑2).
- API:
  - `POST /api/tasks` — manualne utworzenie zadania pod celem (poza pierwotnym kontraktem PRD;
    konieczne, by ćwiczyć/testować submit zanim AI‑planning z PRD‑2 zacznie tworzyć zadania).
  - `POST /api/tasks/{id}/start` — `TODO → IN_PROGRESS`.
  - `GET /api/tasks?due=&state=&goalId=` — filtrowanie.
  - `GET /api/tasks/{id}`.

## Acceptance criteria
- [ ] Migracja tworzy `task` z indeksami `state` i `goal_id`; aplikacja wstaje.
- [ ] `POST /api/tasks` waliduje istnienie celu oraz `type_key`/`skill_keys[]` w katalogu; zadanie
      powstaje w stanie TODO.
- [ ] `POST /api/tasks/{id}/start` przenosi TODO → IN_PROGRESS; powtórny start lub start z innego
      stanu jest odrzucony.
- [ ] `GET /api/tasks` filtruje po `state` i `goalId` (i `due`, jeśli dotyczy); zwraca tylko dane usera.
- [ ] Maszyna stanów dopuszcza `REJECTED → TODO`; klient nie potrafi ustawić DONE/REJECTED bezpośrednio.
- [ ] Testy: integracyjny (create → start → filter → get) + serwisowy z Mockito na przejścia i niezmienniki.

## Blocked by
`issue-3`
