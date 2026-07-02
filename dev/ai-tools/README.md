# ai-tools — loop-based developer task runner

A small framework (TypeScript/npm) for executing developer tasks **precisely and in loops**.
It chains **Claude CLI** agents stage by stage, and after every create step it runs **programmatic
validation** (a script — not the agent — decides whether we move forward or roll back to a previous stage).

## Workflow (state machine)

Each stage = **create/agent** → **validate/script**. Validation decides the transition.

| # | Stage | Creates (agent) | Validation (programmatic) | OK → | FAIL → |
|---|-------|-----------------|----------------------------|------|--------|
| 0 | Analysis / plan | reads the docs **once** → compact task plan (interfaces + tests + impl notes) | JSON plan with ≥1 interface & ≥1 test | 1 | 0 |
| 1 | Interfaces | interfaces + method stubs (`throw NotImplemented`) | `compileJava` passes | 2 | 1 |
| 2 | TDD (red) | tests per `TEST_GUIDELINES.md` | tests compile **and fail** | 3 | 2 |
| 3 | Test review | another agent rates edge-case coverage | parse `pass/fail` verdict | 4 | 2 |
| 4 | Implementation | logic per `IMPLEMENTATION_GUIDELINES.md` | `./gradlew test` passes | 5 | 4 |
| 5 | Full review | agent rates the whole solution | parse `pass/fail` verdict | 5.2 | (agent's choice) |
| 5.2 | Human review | — | you: accept / iterate (agent decides where to roll back) | done | any stage |

**Stage 0 (analysis)** is the only stage that reads the full design docs. It emits a task-scoped plan that
every later stage follows *without* re-reading the docs — this is the main speed lever (fewer agent turns).
It also writes a **focused task note per step** to `.runs/<id>/{1-interface,2-tdd,4-implementation}.md` (naming
the exact files each step should touch, and warning it not to delete/rewrite existing code). Each step reads
only its own note; if you edit the file before the step runs, your version wins over the generated one.

**Human review & `// TODO` feedback:** just before pausing at 5.2 the tool runs `git add -A -- <stagePaths>`
(default `Backend/`), so any `// TODO`/`// FIXME` you then add to the code shows up as an *unstaged* diff.
On **iterate**, those markers are extracted (`file:line — text`), shown to you, and injected into every
downstream stage's context as required work (the agent removes the comment once addressed). You also get a
multi-line editor to describe *what is generally wrong*. **By default iterate resumes from Implementation**
(the shortest loop, and it skips the routing agent). Only when your note mentions a rewrite/redesign
(`przepisz`, `od nowa`, `interfejs`, `kontrakt`, `rewrite`, `redesign`, …) does the routing agent decide
whether to go further back (interface / tdd / implementation).

Each stage has an **attempt limit** (`maxAttemptsPerStage`); the whole run has an **iteration limit**
(`maxTotalIterations`). State and context flow between stages (review notes and human notes feed back into the prompts).

**Model tiering** (`claude.models`): Opus writes the plan (0) and the implementation (4); Sonnet handles
interfaces (1), tests (2), reviews (3, 5) and routing.

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

At the end (and in `status`) it prints a **per-agent usage breakdown** — one row per pipeline stage
(model, tokens, cost, turns, time, with a `(xN)` marker when a stage retried) plus the aggregated
total, all from Claude CLI's streamed usage.

## Configuration — `config/default.json`

- `repoRoot` — cwd for agents and commands (defaults to the repo root).
- `commands.{compileMain,compileTests,test}` — validation commands (Gradle in `Backend/` by default).
- `contextDocs`, `testGuidelines`, `implementationGuidelines` — documents agents read.
- `maxAttemptsPerStage`, `maxTotalIterations`, `commandTimeoutMs`.
- `stagePaths` — paths staged before human review / scanned for `// TODO` (default `["Backend"]`).
- `todoMarkers` — comment markers extracted from your edits on iterate (default `["TODO","FIXME"]`).
- `claude.{model,models,maxTurns,permissionMode,allowedTools}` — `model` is the fallback; `models` sets the
  per-stage model; `allowedTools` sets the per-stage allowed-tools list.

Agents only get `Read/Write/Edit/Glob/Grep` (review stages: read-only). **Agents do not run tests or
compilation — the validation script does**, by design.

## State

Runs are stored in `dev/ai-tools/.runs/<id>.json` (step history, context, per-stage usage & model).
Safe to resume. Alongside it, each run gets a `.runs/<id>/` directory with:

- `1-interface.md` / `2-tdd.md` / `4-implementation.md` — the per-step task notes (editable before a step runs).
- `steps.jsonl` — every agent action (tool calls, text, per-call result) as one JSON line each.
- `outputs/<stage>-attempt<N>.md` — each agent's full final output (plan JSON, review verdicts, summaries).

## Optimizing the pipeline

The `steps.jsonl` + `outputs/` logs are what the **`optimize-ai-tools`** skill consumes to analyze a run
and propose cost/time optimizations (prompt edits, per-stage model/tool/limit changes). Run it after a
run — e.g. ask *"optimize ai-tools"* or *"why was that run so expensive"* — and it reports a per-stage
cost breakdown, the concrete inefficiencies it found (with evidence lines), and the changes to apply.
