import type { AiToolsConfig } from "./config.js";
import { loadStepTask } from "./state.js";
import type { NotedStep, RunState, StageId } from "./types.js";

/** System prompt shared by every agent — enforces "do your scope, return data, no chit-chat". */
export const SYSTEM_PROMPT =
  "You are an agent in an automated, multi-stage developer workflow. " +
  "You work inside the repository directory. Do EXACTLY the scope of your stage — nothing more. " +
  "Do not run tests or compilation yourself (a validation script does that after you). " +
  "Be concise in your summaries.";

function docsBlock(cfg: AiToolsConfig): string {
  const lines = [
    "## Documentation (read it with the Read tool before you start)",
    `- Test guidelines: ${cfg.testGuidelines}`,
    `- Implementation guidelines: ${cfg.implementationGuidelines}`,
    ...cfg.contextDocs.map((d) => `- ${d}`),
  ];
  return lines.join("\n");
}

function taskBlock(state: RunState): string {
  return [`## Task: ${state.task.title}`, "", state.task.body].join("\n");
}

function truncate(text: string, max = 4000): string {
  return text.length <= max ? text : `…(${text.length - max} chars trimmed)…\n${text.slice(-max)}`;
}

/**
 * Retry note shown on attempts after the first. Includes the PREVIOUS attempt's
 * validator output verbatim so the agent fixes the exact error rather than retrying
 * blind — without this, a stage can burn its whole attempt budget repeating one mistake.
 */
function retryNote(state: RunState, attempt: number): string {
  if (attempt <= 1) return "";
  const lines = [
    `\n(This is attempt #${attempt} of this stage — the previous attempt's validation FAILED. Fix EXACTLY what it reports below; do not start over.)`,
  ];
  const f = state.lastFailure;
  if (f && f.stage === state.currentStage) {
    lines.push(`Previous failure: ${f.summary}`);
    if (f.details?.trim()) {
      lines.push("Validator output (address these specific errors):", "```", truncate(f.details.trim()), "```");
    }
  }
  return lines.join("\n");
}

function contextBlock(state: RunState): string {
  if (state.context.length === 0) return "";
  return [
    "## Extra context / notes from previous iterations",
    ...state.context.map((c) => `- ${c}`),
    "",
    "Any `[human TODO] file:line — …` note above is REQUIRED work left by the human reviewer:",
    "address it and delete the corresponding `// TODO` comment from the code once done.",
  ].join("\n");
}

/** Render the task-scoped plan produced by the analysis stage. */
function planBlock(state: RunState): string {
  const p = state.plan;
  if (!p) return "";
  const lines: string[] = ["## PLAN (produced by the analysis stage — this is your source of truth)"];
  lines.push(`### Scope\n${p.scope}`);
  if (p.relevantDesign.length) {
    lines.push("### Relevant design points", ...p.relevantDesign.map((d) => `- ${d}`));
  }
  if (p.interfaces.length) {
    lines.push("### Interfaces to create");
    for (const i of p.interfaces) {
      lines.push(`- **${i.file}** — ${i.purpose}`, ...i.methods.map((m) => `    - ${m}`));
    }
  }
  if (p.tests.length) {
    lines.push("### Tests to write");
    for (const t of p.tests) {
      lines.push(`- **${t.file}**`, ...t.cases.map((c) => `    - ${c}`));
    }
  }
  if (p.implementationNotes.length) {
    lines.push("### Implementation notes", ...p.implementationNotes.map((n) => `- ${n}`));
  }
  return lines.join("\n");
}

const NO_DOCS_NOTE =
  "Do NOT read the design docs (BACKEND_DESIGN.md / TECHNICAL_DESIGN.md) — everything you need for this task is in the PLAN above. " +
  "Read only the specific code files you must touch.";

/** Header for the analysis stage — it reads the full docs to build the plan. */
function analysisHeader(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    taskBlock(state),
    "",
    docsBlock(cfg),
    "",
    contextBlock(state),
    retryNote(state, attempt),
  ].join("\n");
}

/** Header for downstream work stages — they consume the plan, not the full docs. */
function workHeader(cfg: AiToolsConfig, state: RunState, attempt: number, guideline?: string): string {
  // With a plan (the normal path) stages work from it and skip the design docs.
  // Without one (e.g. a legacy run resumed before the analysis stage existed) fall back to the docs.
  const hasPlan = Boolean(state.plan);
  const source = hasPlan ? planBlock(state) : docsBlock(cfg);
  const docNote = hasPlan
    ? guideline
      ? `Guidelines you may consult if needed: ${guideline}. ${NO_DOCS_NOTE}`
      : NO_DOCS_NOTE
    : "No plan available — read the documentation above before you start.";
  return [
    taskBlock(state),
    "",
    source,
    "",
    contextBlock(state),
    "",
    docNote,
    retryNote(state, attempt),
  ].join("\n");
}

const PLAN_FORMAT = [
  "Return the plan as a ```json``` block with this exact shape:",
  "{",
  '  "scope": "one paragraph: what this issue requires",',
  '  "relevantDesign": ["only the design points that matter for THIS issue"],',
  '  "interfaces": [{ "file": "Backend/src/main/java/...", "purpose": "...", "methods": ["signature — one-line contract"] }],',
  '  "tests": [{ "file": "Backend/src/test/java/...", "cases": ["case description"] }],',
  '  "implementationNotes": ["concrete note guiding the green phase"],',
  '  "stepNotes": {',
  '    "interface": ["exactly which files to create/touch; keep it fast and focused; do NOT delete or rewrite existing code"],',
  '    "tdd": ["exactly which test files/cases to add; do NOT delete or modify existing code"],',
  '    "implementation": ["which existing methods to fill in; keep changes minimal"]',
  "  }",
  "}",
  "`interfaces` and `tests` must be non-empty.",
].join("\n");

export function buildAnalysisPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    analysisHeader(cfg, state, attempt),
    "",
    "## Your stage: 0. ANALYSIS / PLAN (read-only)",
    "You are the ONLY stage that reads the full design docs. Read the task and the docs above, explore the",
    "existing code enough to ground the plan, then produce a compact, task-scoped plan that the later stages",
    "(interfaces, tests, implementation, reviews) will follow WITHOUT re-reading the design docs.",
    "- List every interface/class to create with full method signatures + one-line contracts.",
    "- List every test file and the cases it must cover (happy path, edge cases, error paths).",
    "- Keep `relevantDesign` to the few points that actually constrain this issue — do not summarise the whole doc.",
    "- In `stepNotes`, write focused, actionable notes for each later step: name the exact files it should touch",
    "  so it can work fast, and explicitly warn it NOT to delete or rewrite existing code (only add what's new).",
    "Do not create or edit any files.",
    "",
    PLAN_FORMAT,
  ].join("\n");
}

/** Append the analysis stage's focused, per-step notes (from the run's step file) to a work prompt. */
function stepNotesBlock(state: RunState, step: NotedStep): string {
  const note = loadStepTask(state, step);
  return note ? `## Focused notes for this step (from the analysis stage)\n${note}` : "";
}

export function buildInterfacePrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    workHeader(cfg, state, attempt),
    "",
    stepNotesBlock(state, "interface"),
    "",
    "## Your stage: 1. INTERFACES (skeleton)",
    "Create EXACTLY the interfaces / classes listed under `Interfaces to create` in the PLAN — nothing more. Work fast and focused.",
    "- Touch ONLY the files named in the plan / focused notes. Do not wander the codebase.",
    "- Do NOT delete or rewrite existing code — ADD new files/members only.",
    "- Every method must have the full signature from the plan",
    "- Method bodies: `throw new NotImplementedException();` (or equivalent) — NO business logic.",
    "- Create files in the packages given by the plan (`Backend/src/main/java/...`).",
    "- Do not write tests and do not implement logic — those are later stages.",
    "Validation after you: the main code must compile.",
    "At the end, list the files you created/changed.",
  ].join("\n");
}

export function buildTddPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    workHeader(cfg, state, attempt, cfg.testGuidelines),
    "",
    stepNotesBlock(state, "tdd"),
    "",
    "## Your stage: 2. TDD — tests (red phase)",
    "Write EXACTLY the tests listed under `Tests to write` in the PLAN. The interfaces already exist in the repo.",
    "- Add ONLY the test files/cases from the plan. Do NOT delete or modify existing code (production or tests).",
    "- Cover the cases named in the plan (happy path, edge cases, error paths).",
    "- Tests must compile, but they MUST fail (the implementation is still NotImplemented) — this is the red phase.",
    "- Do not implement production logic. Do not remove the `NotImplementedException` from the skeleton.",
    "Validation after you: tests compile AND at least one test fails.",
    "At the end, list the test files you created and briefly state which cases you covered.",
  ].join("\n");
}

export function buildImplementationPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    workHeader(cfg, state, attempt, cfg.implementationGuidelines),
    "",
    stepNotesBlock(state, "implementation"),
    "",
    "## Your stage: 4. IMPLEMENTATION (green phase)",
    "Implement the logic so that ALL tests from stage 2 pass, following the plan's implementation notes.",
    "- Fill in the methods named in the plan; keep changes minimal and do not rewrite unrelated code.",
    "- Do not weaken the tests to make them pass. If a test is clearly wrong, state that in your summary.",
    "- Remove `NotImplementedException` from the paths covered by the task and add the real logic.",
    "Validation after you: `./gradlew test` must pass entirely.",
    "At the end, list the changed files and give a short summary of your approach.",
  ].join("\n");
}

const VERDICT_FORMAT = [
  "Return your verdict as a ```json``` block with this shape:",
  '{ "verdict": "pass" | "fail", "reasons": ["..."], "goBackTo": "<stage to roll back to on fail>" }',
  'Allowed goBackTo values: "interface", "tdd", "implementation". Omit goBackTo when verdict=pass.',
].join("\n");

export function buildReviewTddPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    workHeader(cfg, state, attempt),
    "",
    "## Your stage: 3. TEST REVIEW (read-only)",
    "Assess the tests from stage 2 for COMPLETENESS, especially edge-case coverage.",
    "Check: boundary values, empty/invalid inputs, error paths/exceptions, and conformance to the interface contracts and the PLAN.",
    'Do not edit files. If coverage is insufficient → verdict=fail, goBackTo="tdd".',
    "",
    VERDICT_FORMAT,
  ].join("\n");
}

export function buildReviewSolutionPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    workHeader(cfg, state, attempt),
    "",
    "## Your stage: 5. FULL SOLUTION REVIEW (read-only)",
    "Assess the whole solution: implementation correctness, conformance to the tests and the PLAN, code quality, and gaps in error handling.",
    'Do not edit files. If something is wrong → verdict=fail and set goBackTo ("implementation" for an implementation bug, "tdd" for a missing/bad test, "interface" for a wrong contract).',
    "",
    VERDICT_FORMAT,
  ].join("\n");
}

const ROUTER_STAGES: StageId[] = ["interface", "tdd", "implementation"];

export function buildRouterPrompt(cfg: AiToolsConfig, state: RunState, userFeedback: string): string {
  return [
    taskBlock(state),
    "",
    "## Decision: where to resume the workflow",
    "A human reviewed the finished solution and asked for another iteration with this note:",
    `> ${userFeedback}`,
    "",
    "Based on this note and the history, decide which stage to resume from.",
    `Allowed stages: ${ROUTER_STAGES.join(", ")}.`,
    "",
    "Return a ```json``` block:",
    '{ "goBackTo": "interface" | "tdd" | "implementation", "reason": "..." }',
  ].join("\n");
}
