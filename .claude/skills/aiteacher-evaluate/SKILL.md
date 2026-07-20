---
name: aiteacher-evaluate
description: Grade the user's answers in the AI Teacher app. Pulls every pending question (answered but not yet evaluated, or assist-flagged) from SQLite via npm scripts, grades them, and writes the results back. Use after the user has answered questions in the UI and wants them reviewed. Triggers "aiteacher-evaluate", "evaluate my answers".
---

# AI Teacher — Evaluator & Follow-up

You review the answers a user typed in the AI Teacher UI. All state lives in
SQLite (`dev/ai-teacher/data/ai-teacher.db`); you never edit topic JSON files.
Two npm scripts (run in `dev/ai-teacher/`) bracket your work:

1. `npm run export-pending` — writes `data/_pending.json` with every question that
   needs you: **answered (or re-answered) since its last evaluation**, or
   **flagged "assist required" without an explanation yet**. No topic needs to be
   named — the whole pool across all subjects is exported. If it prints
   "nothing pending", report that and stop.
2. You grade everything in `data/_pending.json` and write `data/_evaluations.json`.
3. `npm run apply-evaluations` — writes your results into SQLite and deletes both
   work files. If it reports failures, fix `_evaluations.json` and re-run it.

## Input: `data/_pending.json`

```json
{
  "exportedAt": "...",
  "count": 2,
  "questions": [
    {
      "topic": "java/core",
      "topicTitle": "Core Java",
      "subjectTitle": "Java",
      "qid": "q1",
      "question": "...",
      "answer": "...",
      "assistRequired": false,
      "previousEvaluation": { "score": "7/10", "verdict": "...", "feedback": "..." },
      "currentFollowUp": "..."
    }
  ]
}
```

`previousEvaluation` / `currentFollowUp` are `null` on first evaluation.

## Output: `data/_evaluations.json`

One entry per pending question, keyed by `topic` + `qid`:

```json
{
  "evaluations": [
    {
      "topic": "java/core",
      "qid": "q1",
      "evaluation": { "score": "7/10", "verdict": "Good", "feedback": "..." },
      "followUp": "a single probing question",
      "explanation": "<p>only for assistRequired questions</p>"
    }
  ]
}
```

- `evaluation` + `followUp` for every question with a non-empty `answer`.
- `explanation` additionally for `assistRequired: true` questions — regardless of
  score, and even when `answer` is empty (then send *only* `explanation`, no
  `evaluation`/`followUp`).
- Write valid JSON, 2-space indented, trailing newline.

## Grading

- Judge the answer **on its own merits** against what a correct, complete answer is.
- `score` is a short string like `"8/10"`; `verdict` is one short label
  (`Excellent | Good | Partially correct | Incorrect`); `feedback` says what was
  right, what was wrong or missing, and the correct point.
- Be honest and specific; vague praise is useless. Point to the exact gap.
- Reward correct reasoning even if phrasing is rough; penalize confident wrong claims.
- `feedback` and `followUp` are plain text (the UI renders them as-is).
- `followUp`: a single probing question that pushes deeper — typically "why", a
  trade-off, an edge case, or "when would this NOT hold".
- **Follow-up answers:** the UI lets the user answer `currentFollowUp` inside the
  same `answer` field, below a `----FOLLOW UP ANSWER-----` marker line. When the
  marker is present: grade the text above it against `question`, the text below it
  against `currentFollowUp`, and cover both in one `evaluation` (one combined
  `score`, feedback addressing each part explicitly). Then write a NEW `followUp`
  that builds on the follow-up answer.

## Assist required → explanation (HTML)

`assistRequired: true` means the user wants the topic explained, not just graded.
While `feedback` judges the answer, `explanation` teaches the topic itself —
centered on **clear examples**:

- Lead with a short intuition (`<p>`), then show 1–2 concrete examples: a minimal
  code snippet where code fits the subject (`<pre><code>...</code></pre>`), and/or a
  general real-world example (`<p>` or a short `<ul>`).
- Prefer a runnable-looking, minimal snippet over prose; add a one-line takeaway after it.
- Allowed tags only: `p`, `ul`, `ol`, `li`, `strong`, `em`, `code`, `pre`.
  No scripts, styles, images, links or inline event handlers. Escape `<`, `>`, `&`
  inside code as HTML entities.
- Keep it compact (roughly 5–15 lines of rendered content) and put it in the JSON as
  a single-line string with `\n` only inside `<pre>` blocks where formatting matters.

## After applying

Tell the user how many answers were evaluated (and for which topics), how many
explanations were written, and to hit "Reload" in the UI to see the results.
