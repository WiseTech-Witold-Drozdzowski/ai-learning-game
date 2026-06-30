# ai-tools — loop-based developer task runner

A small framework (TypeScript/npm) for executing developer tasks **precisely and in loops**.
It chains **Claude CLI** agents stage by stage, and after every create step it runs **programmatic
validation** (a script — not the agent — decides whether we move forward or roll back to a previous stage).

## Workflow (state machine)

Each stage = **create/agent** → **validate/script**. Validation decides the transition.

| # | Stage | Creates (agent) | Validation (programmatic) | OK → | FAIL → |
|---|-------|-----------------|----------------------------|------|--------|
| 1 | Interfaces | interfaces + method stubs (`throw NotImplemented`) | `compileJava` passes | 2 | 1 |
| 2 | TDD (red) | tests per `TEST_GUIDELINES.md` | tests compile **and fail** | 3 | 2 |
| 3 | Test review | another agent rates edge-case coverage | parse `pass/fail` verdict | 4 | 2 |
| 4 | Implementation | logic per `IMPLEMENTATION_GUIDELINES.md` | `./gradlew test` passes | 5 | 4 |
| 5 | Full review | agent rates the whole solution | parse `pass/fail` verdict | 5.2 | (agent's choice) |
| 5.2 | Human review | — | you: accept / iterate (agent decides where to roll back) | done | any stage |

Each stage has an **attempt limit** (`maxAttemptsPerStage`); the whole run has an **iteration limit**
(`maxTotalIterations`). State and context flow between stages (review notes and human notes feed back into the prompts).

## Requirements

- Node ≥ 20, the `claude` CLI on PATH (headless `claude -p`).
- Default target: `Backend/` (Gradle). Validation commands are configurable in `config/default.json`.

## Install

```bash
cd dev/ai-tools
npm install
```

## Usage (from WSL)

```bash
# task selection: docs/issue*.md files (regex) or manual paste
npm run task

# task from a specific file or pasted inline
npm run task -- --file docs/issue-12.md
npm run task -- --task "Add endpoint GET /api/jobs/{id} ..."

# status / list / resume a paused run
npm run status            # latest run
npm run status -- <id>
npm run list
npm run resume -- <id>
```

At the end (and in `status`) it prints a summary of **tokens used and cost** (aggregated across all
agent calls, from Claude CLI's `--output-format json`).

## Configuration — `config/default.json`

- `repoRoot` — cwd for agents and commands (defaults to the repo root).
- `commands.{compileMain,compileTests,test}` — validation commands (Gradle in `Backend/` by default).
- `contextDocs`, `testGuidelines`, `implementationGuidelines` — documents agents read.
- `maxAttemptsPerStage`, `maxTotalIterations`, `commandTimeoutMs`.
- `claude.{model,maxTurns,permissionMode,allowedTools}` — per-stage allowed-tools list.

Agents only get `Read/Write/Edit/Glob/Grep` (review stages: read-only). **Agents do not run tests or
compilation — the validation script does**, by design.

## State

Runs are stored in `dev/ai-tools/.runs/<id>.json` (step history, context, usage). Safe to resume.
