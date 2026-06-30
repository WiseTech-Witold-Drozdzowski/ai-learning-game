# IMPLEMENTATION_GUIDELINES

Guidelines for the **Implementation** phase (stage 4). Goal: make all stage-2 tests green
without changing them (unless a test is clearly wrong — then call it out explicitly).

## Rules

- Implement strictly against the stage-1 interface contracts and the stage-2 tests.
- **Do not weaken the tests** to make them pass. If a test is wrong — write it in the summary instead of silently removing it.
- Follow the architecture in `BACKEND_DESIGN.md` / `TECHNICAL_DESIGN.md` (layers, packages, Spring conventions).
- Small, readable methods. Validate input at the boundary (public methods / controllers).
- Error handling consistent with the tests (the same exception types / codes).

## Style

- Java + Spring Boot, constructor injection (`final` fields, no field `@Autowired`).
- No dead code, no commented-out fragments, no `System.out` — use a logger.
- Naming and formatting consistent with the existing code in `Backend/src/main/java`.

## Definition of done (stage 4)

- All tests pass (`./gradlew test` returns 0).
- No `throw new NotImplementedException()` on the paths covered by the task.
- Code compiles with no new warnings introduced by this change.
