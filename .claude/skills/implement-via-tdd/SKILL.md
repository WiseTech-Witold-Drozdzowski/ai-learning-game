---
name: implement-via-tdd
description: >-
  Build the next backend issue via strict TDD — red, green, then a separate agent
  reviews. Compile and test only through Backend/run-tests.sh. Use when the user
  wants to implement the next issue from docs/prd/current-status.md, or names a
  specific issue to build test-first.
---

# implement-via-tdd

Build one backend issue end-to-end as **red → green → review**: skeleton, failing tests
(red), implement to green, then a **separate agent** reviews. Same discipline the
`dev/ai-tools` pipeline automates, run interactively here.

**One build tool.** Route every compile and test through `Backend/run-tests.sh` — there is no
local JDK, so this script is the only way to know "does it compile / do tests pass". It
`cd`s to its own dir, so call it as `Backend/run-tests.sh` from anywhere; pass a partial test
name to scope a run (`Backend/run-tests.sh Job`), omit it for the full suite.

Docs (read the one a step names): issues `docs/prd/current-status.md` +
`docs/prd/prd-*-issues/issue-*.md`; design `docs/design/BACKEND_DESIGN.md`,
`docs/design/TECHNICAL_DESIGN.md`; guidelines `docs/guidelines/TEST_GUIDELINES.md`,
`docs/guidelines/IMPLEMENTATION_GUIDELINES.md`.

## 0. Pick the issue

Read `docs/prd/current-status.md` (`[x]` done · `[~]` in progress · `[ ]` pending). Take the
issue the user named, else the **next actionable** one: top-to-bottom, prd-1 before prd-2, the
first not-`[x]` issue whose *Blocked by* are all `[x]` (resume a `[~]` before starting a `[ ]`).
Read that issue file in full, plus the parts of `BACKEND_DESIGN.md` / `TECHNICAL_DESIGN.md` for
the module it touches — this is the only step that reads the design docs; carry what you learn
forward. Mark the issue `[~]`.

Done when: you have stated the chosen issue, its scope, and the interfaces + tests you will
write. If every issue is blocked, say so and stop.

## 1. Interfaces (skeleton)

Create the interfaces/classes the issue needs, in the packages `BACKEND_DESIGN.md` dictates,
touching only files this issue introduces or must extend. Full method signatures; every body
is `throw new NotImplementedException();`.

Done when: `Backend/run-tests.sh <Scope>` compiles the main source (a compile error here is a
bad skeleton — fix before continuing).

## 2. Red — write the failing tests

Write the tests per `TEST_GUIDELINES.md`, one covering **each** acceptance criterion of the
issue (happy path, edge/boundary, error paths). Leave the skeleton at `NotImplementedException`.

Done when: `Backend/run-tests.sh <Scope>` shows the tests **compile AND at least one fails on
an assertion** — a real red. A compile error is not a red; fix the test or the signatures and
re-run. Report the cases covered and paste the failing-assertion lines.

## 3. Green — implement

Implement against the interfaces and tests per `IMPLEMENTATION_GUIDELINES.md`, keeping the
tests exactly as written. If a test is genuinely wrong, stop and say so rather than editing it.

Done when: `Backend/run-tests.sh <Scope>` passes, then a full `Backend/run-tests.sh` (no
filter) passes — the green is real only against the full suite.

## 4. Review (separate agent)

Launch a review agent (Agent tool, `subagent_type: "general-purpose"`) — a separate context so
the author never reviews itself. Give it the diff (changed files under `Backend/`), the paths
to read (the issue file, `BACKEND_DESIGN.md`, `TECHNICAL_DESIGN.md`,
`IMPLEMENTATION_GUIDELINES.md`, `TEST_GUIDELINES.md`), and this brief: *"Be strict — last gate
before a human. Judge correctness, conformance to the issue's acceptance criteria and the
design docs, test completeness (edge/error cases), and code quality (naming, layering,
duplication, style consistency with Backend/src/main/java). Read-only. Return `pass` | `fail`
with a concrete `reasons` list; for each fail name the layer (interface / test /
implementation)."*

Done when: you have relayed the reviewer's verdict and reasons. Then go to step 5 — the human
decides; the verdict is input to that decision, not the final word.

## 5. Land, iterate, or rework

Present exactly these three choices with the `AskUserQuestion` tool and act on the pick:

- **Green light — land it.** The solution is accepted. Commit the change (branch first if on
  `main`), mark the issue `[x]` in `docs/prd/current-status.md`, and summarize issue built +
  files changed + final `run-tests.sh` result.
- **Iteration required.** The shape is right, specific fixes remain. Scan the changed files for
  `// TODO:` / `// FIXME:` markers the human left — each is required work; address it and delete
  the marker once done. Ask the user for any larger context to fold in, apply it, then return to
  green (step 3) and re-review (step 4) before landing.
- **Rework required.** The solution is fundamentally wrong. Invite the user's direction now, and
  make clear they can give it later — hold here until they do. Once they do, resume at the
  earliest wrong layer (interface or tdd) and rebuild from there.

Done when: the change is landed (green light), or you have looped back to the step the pick
routes to.

## Guardrails

- Every red/green claim cites an actual `Backend/run-tests.sh` run — red = compiles + assertion
  fails, green = full suite passes.
- Stay within the issue's scope; leave blocked issues and unrelated code alone.
- The human's pick at step 5 is authoritative — land only on green light.
