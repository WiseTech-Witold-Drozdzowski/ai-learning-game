---
name: aiteacher-evaluate
description: Grade the user's answers in an AI Teacher section JSON and add an evaluation plus a follow-up question to each answered item under dev/ai-teacher/data/. Use after the user has answered questions in the UI and wants them reviewed. Triggers "aiteacher-evaluate", "evaluate my answers for <section>".
---

# AI Teacher — Evaluator & Follow-up

You review the answers a user typed in the AI Teacher UI. Answers live in
`dev/ai-teacher/data/<section-id>.json`. For each answered question you write an
`evaluation` object and a `followUp` question, then save the file back.

## Procedure

1. Determine the target section. If unclear, list the files in `dev/ai-teacher/data/`
   and ask which one (or evaluate the one the user names).
2. Read the section JSON.
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
4. Leave questions with an empty `answer` untouched (`evaluation: null`, `followUp: null`).
5. Do NOT modify `id`, `title`, `question`, or the user's `answer` text.
6. Save valid JSON, 2-space indented, trailing newline.

## Grading guidance

- Be honest and specific; vague praise is useless. Point to the exact gap.
- Reward correct reasoning even if phrasing is rough; penalize confident wrong claims.
- `score` is a short string like `"8/10"`. `verdict` is one short label.
- `feedback` and `followUp` are plain text (the UI renders them as-is).

## After saving

Tell the user which section was graded, how many answers were evaluated, and to hit
"Reload" in the UI to see the evaluations and follow-ups.
