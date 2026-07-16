---
name: aiteacher-questions
description: Generate a set of study questions for the AI Teacher app and write them into a section JSON file under dev/ai-teacher/data/. Use when the user wants a new question set / section, or to add questions to an existing section. Triggers "aiteacher-questions", "write questions for <topic>".
---

# AI Teacher — Question Writer

You create question sets for the AI Teacher app. Each section is one JSON file in
`dev/ai-teacher/data/<section-id>.json`. The Vue UI reads these files and lets the
user answer; a separate skill (`aiteacher-evaluate`) grades the answers later.

## Inputs to gather

Ask the user only for what is missing:
- **Topic / section title** (e.g. "System Design", "Spring Boot").
- **How many questions** (default 8 if unspecified).
- **Difficulty / focus** (optional).
- Whether this is a **new section** or **adding to an existing** one.

If the target section id is not obvious, derive a kebab-case id from the title
(e.g. "System Design" -> `system-design`).

## File format (must match exactly)

```json
{
  "id": "<section-id>",
  "title": "<Human Title>",
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
- `id` of the file object must equal the filename without `.json`.
- Question ids are `q1`, `q2`, ... unique within the file.
- New questions ALWAYS start with `"answer": ""`, `"evaluation": null`, `"followUp": null`.
  The user fills `answer` in the UI; the evaluator fills `evaluation` and `followUp`.
- Write valid JSON, 2-space indented, trailing newline.

## Procedure

1. Resolve the section id and the path `dev/ai-teacher/data/<section-id>.json`.
2. **New section:** create the file with the format above.
   **Existing section:** read the current file, keep every existing question and its
   `answer`/`evaluation`/`followUp` untouched, and append new questions with the next
   free `qN` ids.
3. Write good questions: open-ended, concept-testing, one idea each, ordered easy→hard.
   Avoid pure yes/no questions.
4. After writing, tell the user the file path and how many questions it now has, and
   remind them the section will appear in the UI (they may need "Reload sections").
