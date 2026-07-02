---
name: optimize-ai-tools
description: >-
  Analyze the last ai-tools run(s) and optimize the pipeline for cost and time.
  Reads the per-run logs under dev/ai-tools/.runs/ (usage, retries, every agent
  step) and proposes concrete changes to prompts (src/prompts.ts), per-stage
  models / tools / limits (config/default.json), and workflow behavior. Use when
  the user wants to make the ai-tools task runner cheaper or faster, review why a
  run was expensive/slow, or tune stage prompts and model tiering. Triggers:
  "optimize ai-tools", "why was that run so expensive", "tune the pipeline",
  "make the agents cheaper/faster".
---

# optimize-ai-tools

Optimize the `dev/ai-tools` loop runner (analysis → interface → tdd → review-tdd →
implementation → review-solution) for **cost (USD / tokens)** and **wall-clock time**,
using evidence from what the agents actually did in the last run(s).

All paths below are relative to `dev/ai-tools/` (the tool root). Runs are gitignored.

## 1. Gather the evidence

1. **Find the run(s).** List `.runs/*.json`, newest first (filename timestamp sorts).
   Default to the most recent run; if the user names an id, use it. To compare the
   effect of a past change, analyze the two or three most recent runs together.

2. **Read the run summary** `.runs/<id>.json`:
   - `task` — what was being built (scope shapes what's reasonable).
   - `history[]` — every step: `stage`, `attempt`, `phase` (`create`/`validate`),
     `ok`, `summary`, `usage` (per-agent tokens/cost/turns/durationMs), `model`.
   - `attempts` — attempts per stage (retries = wasted cost/time).
   - `totals`, `totalIterations`, `status`, `lastFailure`.

3. **Read the step log** `.runs/<id>/steps.jsonl` — one JSON line per agent action:
   - `kind:"tool"` with `tool`+`detail` (e.g. `Read Backend/.../Foo.java`, `Bash ...`),
   - `kind:"text"` (the agent's own summary lines),
   - `kind:"result"` (per-call outcome + usage).
   Group by `stage`+`attempt`. This is where you see *how* tokens were spent:
   redundant reads, doc re-reads, wandering greps, long tool chains.

4. **Read the saved outputs** `.runs/<id>/outputs/<stage>-attempt<N>.md` — each agent's
   full final text (the plan JSON, review verdicts, summaries). Cross-check the plan
   against what later stages actually did.

If `steps.jsonl` / `outputs/` are missing, the run predates step logging — work from
`history[]` alone and note the reduced fidelity.

## 2. Diagnose

Rank stages by `costUsd` and by `durationMs` (from `history`). For the worst offenders,
look for these concrete, fixable patterns in `steps.jsonl`:

- **Retries (biggest lever).** Any stage with `attempts > 1` paid its full cost N times.
  Read `lastFailure` and the failed attempt's steps: was the prompt ambiguous? Did the
  agent lack context the plan should have carried? Could the validator's feedback be
  surfaced better? (See `retryNote` in `src/prompts.ts`.)
- **Doc re-reads.** Only `analysis` should read `BACKEND_DESIGN.md` / `TECHNICAL_DESIGN.md`
  (see `NO_DOCS_NOTE`). If a later stage's steps show a Read of a design doc, the plan is
  under-specified or the "do NOT read docs" instruction isn't landing.
- **Over-exploration.** Many `Read`/`Grep`/`Glob` before any edit → the plan's `stepNotes`
  aren't naming exact files, so the agent hunts. Tighten `stepNotes` guidance in the
  analysis prompt.
- **High `numTurns` for a small change** → prompt invites over-work, or `maxTurns` is
  loose. Compare turns to files touched.
- **Cache tokens dominating** → mostly benign (prompt caching), but a huge
  `cacheCreationTokens` on every attempt means large re-sent context; consider trimming
  what each stage prompt includes.
- **Model tiering.** `config/default.json → claude.models`. Opus runs `analysis` and
  `review-solution`. Ask per stage: did the Opus stage's output justify the ~5–10x cost
  over Sonnet? Did a Sonnet stage retry repeatedly in a way a stronger model would avoid
  in one shot (cheaper overall)? Propose up/downgrades with the cost math.

## 3. Optimization levers (where to change things)

| Lever | File | Use when |
|-------|------|----------|
| Per-stage **model** | `config/default.json` → `claude.models.<stage>` | A stage is over/under-powered for its job |
| Per-stage **prompt** | `src/prompts.ts` (`build*Prompt`, `SYSTEM_PROMPT`, `NO_DOCS_NOTE`, `stepNotes` guidance) | Retries, doc re-reads, over-exploration, verbosity |
| **allowedTools** | `config/default.json` → `claude.allowedTools.<stage>` | A stage uses tools it shouldn't need (narrow the surface) |
| **maxTurns** | `config/default.json` → `claude.maxTurns` | Agents burn turns; cap without starving legit work |
| **maxAttemptsPerStage** | `config/default.json` | Stages waste money on doomed retries |
| **Plan quality** | analysis prompt + `PLAN_FORMAT`/`stepNotes` in `src/prompts.ts` | Downstream stages re-derive or re-read — fix upstream, once |

Prefer upstream fixes: a better plan (one analysis call) removes cost from every
downstream stage and every retry. That usually beats swapping a model.

## 4. Report, then change

1. Present a short **findings report**: a per-stage cost/time table (highest first), the
   top 3–5 concrete inefficiencies with the evidence line(s) from `steps.jsonl`, and for
   each a proposed change with an **estimated saving** (e.g. "eliminating the doc re-read
   in `tdd` ≈ −$X and −Ys/run"; "one fewer implementation retry ≈ −$Z").
2. Order proposals by **impact ÷ risk**. Config tweaks (model/tools/limits) are low-risk
   and reversible; prompt edits are higher-leverage but change behavior — call that out.
3. Apply the changes the user approves. For prompt edits, keep the existing voice and the
   stage's contract (scope, JSON output shapes) intact — only change what the evidence
   targets. For config, edit `config/default.json` values in place.
4. **Verify:** run `npm run typecheck` after touching `src/`. Recommend a re-run on a
   comparable task and a before/after compare (this skill again) to confirm the saving —
   don't claim a saving you haven't measured.

## Guardrails

- Optimize cost/time **without regressing quality**: never propose removing a review
  stage, weakening validation, or letting agents run their own tests (the validator does
  that by design — see `SYSTEM_PROMPT`). Cheaper-but-wrong is not an optimization.
- Base every claim on a specific line in the run data. If the data doesn't support a
  change, say so rather than guessing.
- One run is a weak sample. Flag when a pattern needs more runs to confirm before acting.
