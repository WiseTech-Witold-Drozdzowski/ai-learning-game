---
name: aiteacher-evaluate
description: Grade the user's answers in an AI Teacher topic JSON and add an evaluation plus a follow-up question to each answered item under dev/ai-teacher/data/<subject>/. Use after the user has answered questions in the UI and wants them reviewed. Triggers "aiteacher-evaluate", "evaluate my answers for <topic>".
---

# AI Teacher — Evaluator & Follow-up

You review the answers a user typed in the AI Teacher UI. Content is organized as
**subject → topic → questions**; answers live in
`dev/ai-teacher/data/<subject>/<topic>.json` (e.g. `data/java/parallelism.json`).
For each answered question you write an `evaluation` object and a `followUp`
question, then save the file back.

## Procedure

1. Determine the target topic. If unclear, list the JSON files under
   `dev/ai-teacher/data/` (recursively; ignore files starting with `_`) and ask
   which one (or evaluate the one the user names, e.g. "java/parallelism").
2. Read the topic JSON.
3. For every question where `answer` is a non-empty string:
   - Judge the answer **on its own merits** against what a correct, complete answer is.
   - Fill `evaluation` (replace whatever is there — re-grading is fine):
     ```json
     "evaluation": {
       "score": "7/10",
       "verdict": "Good | Partially correct | Incorrect | Excellent",
       "feedback": "What was right, what was wrong or missing, and the correct point."
     }
     ```
   - Fill `followUp` with a single probing question that pushes deeper — typically
     "why", a trade-off, an edge case, or "when would this NOT hold". A string.
   - **Follow-up answers:** the UI lets the user answer the current `followUp` inside
     the same `answer` field, below a `----FOLLOW UP ANSWER-----` marker line. When the
     marker is present: grade the text above it against the original `question`, grade
     the text below it against the current `followUp`, and cover both in one
     `evaluation` (one combined `score`, feedback addressing each part explicitly).
     Then write a NEW `followUp` that builds on the follow-up answer.
4. For every question flagged `"assistRequired": true` (the user marks it in the UI),
   write a top-level `explanation` field — see "Assist required" below. This is IN
   ADDITION to the normal evaluation and applies regardless of the score (a 7-8/10
   answer still gets an explanation if flagged), and even when `answer` is empty
   (then write only `explanation`; leave `evaluation`/`followUp` null).
5. Otherwise leave questions with an empty `answer` untouched
   (`evaluation: null`, `followUp: null`).
6. Do NOT modify `id`, `title`, `question`, the user's `answer` text, or the
   `assistRequired` / `hidden` flags. Do NOT write `explanation` for unflagged
   questions; leave any existing `explanation` as it is.
7. Save valid JSON, 2-space indented, trailing newline.

## Grading guidance

- Be honest and specific; vague praise is useless. Point to the exact gap.
- Reward correct reasoning even if phrasing is rough; penalize confident wrong claims.
- `score` is a short string like `"8/10"`. `verdict` is one short label.
- `feedback` and `followUp` are plain text (the UI renders them as-is).

## Assist required → explanation (HTML)

Questions can carry `"assistRequired": true`, set by the user in the UI when they
want the topic explained, not just graded. Only these questions get an `explanation`
— a top-level string field on the question (sibling of `answer`/`followUp`), rendered
as HTML on the page (via `v-html`). While `feedback` judges the user's answer,
`explanation` teaches the topic itself — centered on **clear examples**:

- Lead with a short intuition (`<p>`), then show 1–2 concrete examples: a minimal
  code snippet where code fits the subject (`<pre><code>...</code></pre>`), and/or a
  general real-world example (`<p>` or a short `<ul>`).
- Prefer a runnable-looking, minimal snippet over prose; add a one-line takeaway after it.
- Allowed tags only: `p`, `ul`, `ol`, `li`, `strong`, `em`, `code`, `pre`.
  No scripts, styles, images, links or inline event handlers. Escape `<`, `>`, `&`
  inside code as HTML entities.
- Keep it compact (roughly 5–15 lines of rendered content) and put it in the JSON as
  a single-line string with `\n` only inside `<pre>` blocks where formatting matters.

## After saving

Tell the user which topic was graded, how many answers were evaluated, and to hit
"Reload" in the UI to see the evaluations and follow-ups.
