---
name: aiteacher-questions
description: Generate a set of study questions for the AI Teacher app and write them into a topic JSON file under dev/ai-teacher/data/<subject>/. Use when the user wants a new question set / topic, or to add questions to an existing topic. Triggers "aiteacher-questions", "write questions for <topic>".
---

# AI Teacher — Question Writer

You create question sets for the AI Teacher app. Content is organized as
**subject → topic → questions**: each topic is one JSON file at
`dev/ai-teacher/data/<subject>/<topic>.json` (e.g. `data/java/parallelism.json`).
The Vue UI reads these files and lets the user answer; a separate skill
(`aiteacher-evaluate`) grades the answers later.

## Inputs to gather

Ask the user only for what is missing:
- **Subject** — the main area (e.g. "Java", "System Design", "Angular"). Kebab-case
  directory name: `java`, `system-design`, `angular`.
- **Topic** — the concrete topic within the subject (e.g. "Parallelism"). Kebab-case
  file name: `parallelism`.
- **How many questions** (default 8 if unspecified).
- **Difficulty / focus** (optional).
- Whether this is a **new topic** or **adding to an existing** one.

Derive kebab-case ids from the titles (e.g. "System Design" -> `system-design`).
Reuse an existing subject directory when one fits; list `dev/ai-teacher/data/` to check.

## File format (must match exactly)

```json
{
  "id": "<topic-id>",
  "title": "<Human Topic Title>",
  "description": "<one-line summary, optional>",
  "questions": [
    {
      "id": "q1",
      "question": "<the question text>",
      "answer": "",
      "evaluation": null,
      "followUp": null
    }
  ]
}
```

Rules:
- `id` of the file object must equal the topic filename without `.json`.
- Question ids are `q1`, `q2`, ... unique within the file.
- New questions ALWAYS start with `"answer": ""`, `"evaluation": null`, `"followUp": null`.
  The user fills `answer` in the UI; the evaluator fills `evaluation` and `followUp`.
- Write valid JSON, 2-space indented, trailing newline.

Optional subject metadata: `data/<subject>/_subject.json` with `{ "title": "..." }`
sets the display name of the subject. Only needed when Title Case derived from the
directory name is wrong (e.g. `javascript` -> "JavaScript"). Files starting with `_`
are never treated as topics.

## Procedure

1. Resolve the subject and topic ids and the path
   `dev/ai-teacher/data/<subject>/<topic>.json`. Create the subject directory if new;
   add `_subject.json` only when the derived title needs fixing.
2. **New topic:** create the file with the format above.
   **Existing topic:** read the current file, keep every existing question and its
   `answer`/`evaluation`/`followUp` untouched, and append new questions with the next
   free `qN` ids.
3. Write good questions: open-ended, concept-testing, one idea each, ordered easy→hard.
   Avoid pure yes/no questions.
4. After writing, tell the user the file path and how many questions it now has, and
   remind them the topic will appear in the UI under its subject (they may need
   "Reload sections").
