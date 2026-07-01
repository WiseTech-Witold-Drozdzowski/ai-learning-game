# TEST_GUIDELINES

Guidelines for the **TDD** phase (stage 2). Tests are written BEFORE the implementation and must fail first.

## General rules

- Framework: **JUnit 5** + **AssertJ** (assertions), **Mockito** for dependencies.
- **Spring Boot 4 uses Jackson 3** — the `tools.jackson.*` namespace, NOT the old Jackson 2
  `com.fasterxml.jackson.*` (which is not on the classpath and will fail to compile).
  Import `ObjectMapper` from `tools.jackson.databind.ObjectMapper`. Likewise, web-slice tests
  use the Boot 4 modularised packages: `@WebMvcTest` from
  `org.springframework.boot.webmvc.test.autoconfigure`, and `@MockitoBean` from
  `org.springframework.test.context.bean.override.mockito`.
- Keep unit tests next to the class under test: `src/test/java/<same package>`.
- Naming: `ClassTest` for units, methods `should<Behaviour>_when<Condition>()`.
- One test = one behaviour. Clearly separate Arrange / Act / Assert with comments.
- Tests MUST compile (`compileTestJava` passes) and MUST fail on assertions, because the
  implementation is still `throw new NotImplementedException()` from the interface skeleton.

## Coverage — what we expect

1. **Happy path** — the basic correct scenario for every public method of the interface.
2. **Edge cases**:
   - boundary values (0, 1, max, empty collections, `null` where allowed),
   - empty / blank / overly long strings,
   - duplicates, ordering, idempotency.
3. **Error paths** — expected exceptions (`assertThatThrownBy(...)`), input validation.
4. **Contract** — conformance to the types and signatures of the stage-1 interfaces and to `BACKEND_DESIGN.md`.

## What to avoid

- Do not test private implementation / details — test behaviour through the public API.
- Do not write tests that pass despite a missing implementation (e.g. with no assertions).
- Do not mock the type under test.

## Definition of done (stage 2)

- Tests compile.
- At least one test fails (red phase) — because there is no implementation yet.
- Covered: happy path + edge cases + error paths for every method of the interface.
