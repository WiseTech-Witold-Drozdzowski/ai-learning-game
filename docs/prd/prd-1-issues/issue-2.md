# issue-2 — Katalog konfiguracji mechaniki (TaskTypeDefinition + SkillDefinition)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `config`).

## What to build
Kuratorowany, runtime‑edytowalny katalog, na którym opierają się zadania i naliczanie exp.
Idzie wcześnie, bo `Task.typeKey`/`Task.skillKeys[]` i `GamificationService` go referują.

- Flyway: `task_type_definition(key PK, display_name, verification_method, exp_base,
  exp_scale_by_score, requires_artifact)` oraz `skill_definition(key PK, display_name, category)`.
  `verification_method` ∈ {HONOR, HONOR_WITH_PROOF, AUTO_QUIZ, AI_DIALOG, AI_ARTIFACT_REVIEW}
  (w PRD‑1 realnie używane tylko HONOR / HONOR_WITH_PROOF).
- Seed: wartości domyślne z YAML (mapowane przez `@ConfigurationProperties`) ładowane do bazy
  przy starcie, jeśli wpisu brak. Baza jest źródłem prawdy; seed nie nadpisuje ręcznych edycji.
- Runtime‑edycja bez restartu: `GET/PUT /api/task-types[/{key}]`, `GET/PUT /api/skill-defs[/{key}]`.

## Acceptance criteria
- [ ] Migracje tworzą obie tabele; po pierwszym starcie istnieją sensowne wpisy domyślne (seed).
- [ ] Ponowny start nie duplikuje seedu ani nie nadpisuje wpisów zmienionych w runtime.
- [ ] `GET /api/task-types` i `GET /api/skill-defs` zwracają katalog; `GET .../{key}` pojedynczy wpis.
- [ ] `PUT .../{key}` tworzy/aktualizuje wpis i zmiana jest widoczna natychmiast (bez restartu).
- [ ] `PUT` waliduje wartości (np. nieznane `verification_method` odrzucone, `exp_base` ≥ 0).
- [ ] Testy integracyjne (Testcontainers) na seed + runtime‑edycję dla obu katalogów.

## Blocked by
`issue-1`
