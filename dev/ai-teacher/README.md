# AI Teacher

A small Vue app to study by answering questions and getting them graded by an agent.

Loop:
1. **Agent writes questions** into a section JSON (`/aiteacher-questions`).
2. **You answer** in the UI and click **Save answers**.
3. **Agent grades** the answers and adds a follow-up (`/aiteacher-evaluate`).
4. Click **Reload** in the UI to see the evaluation and follow-up.

Each section is a separate JSON file in `data/`. The sidebar lets you jump between them.

## Run (Docker)

```bash
docker compose up --build
```

Open http://localhost:5173

`./data` is mounted into the container, so the agents (run on the host via the console)
and the UI read and write the same files.

## Agents (run manually in the console)

- `/aiteacher-questions` — generate a question set for a topic.
- `/aiteacher-evaluate` — grade your answers and add follow-ups.

Both are skills in `.claude/skills/` and operate on `dev/ai-teacher/data/*.json`.

## Section JSON format

```json
{
  "id": "javascript-basics",
  "title": "JavaScript Basics",
  "description": "optional",
  "questions": [
    {
      "id": "q1",
      "question": "text",
      "answer": "",
      "evaluation": { "score": "8/10", "verdict": "Good", "feedback": "..." },
      "followUp": "a deeper question, or null"
    }
  ]
}
```

- The UI only ever writes the `answer` field (merged in, never clobbering agent output).
- Agents write `question`, `evaluation`, and `followUp`.

## Local dev without Docker

```bash
npm install
npm run build && npm start   # server on :3000
# or, with hot reload:
npm start & npm run dev      # UI on :5173, proxies /api to :3000
```
