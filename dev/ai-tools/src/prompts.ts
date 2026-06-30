import type { AiToolsConfig } from "./config.js";
import type { RunState, StageId } from "./types.js";

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

function contextBlock(state: RunState): string {
  if (state.context.length === 0) return "";
  return ["## Extra context / notes from previous iterations", ...state.context.map((c) => `- ${c}`)].join("\n");
}

function header(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    taskBlock(state),
    "",
    docsBlock(cfg),
    "",
    contextBlock(state),
    attempt > 1
      ? `\n(This is attempt #${attempt} of this stage — the previous validation failed. Fix what failed.)`
      : "",
  ].join("\n");
}

export function buildInterfacePrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    header(cfg, state, attempt),
    "",
    "## Your stage: 1. INTERFACES (skeleton)",
    "Create the interfaces / classes and method signatures needed for the task, following the architecture in BACKEND_DESIGN.md.",
    "- Every method must have a full signature (parameter and return types) and a short JavaDoc describing its contract.",
    "- Method bodies: `throw new NotImplementedException();` (or equivalent) — NO business logic.",
    "- Create files in the correct packages (`Backend/src/main/java/...`).",
    "- Do not write tests and do not implement logic — those are later stages.",
    "Validation after you: the main code must compile.",
    "At the end, list the files you created/changed.",
  ].join("\n");
}

export function buildTddPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    header(cfg, state, attempt),
    "",
    "## Your stage: 2. TDD — tests (red phase)",
    `Write tests following the guidelines in ${cfg.testGuidelines}, against the interfaces from stage 1.`,
    "- Cover the happy path, edge cases, and error paths for every public method of the interface.",
    "- Tests must compile, but they MUST fail (the implementation is still NotImplemented) — this is the red phase.",
    "- Do not implement production logic. Do not remove the `NotImplementedException` from the skeleton.",
    "Validation after you: tests compile AND at least one test fails.",
    "At the end, list the test files you created and briefly state which cases you covered.",
  ].join("\n");
}

export function buildImplementationPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    header(cfg, state, attempt),
    "",
    "## Your stage: 4. IMPLEMENTATION (green phase)",
    `Implement the logic so that ALL tests from stage 2 pass, following ${cfg.implementationGuidelines}.`,
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
    header(cfg, state, attempt),
    "",
    "## Your stage: 3. TEST REVIEW (read-only)",
    "Assess the tests from stage 2 for COMPLETENESS, especially edge-case coverage.",
    "Check: boundary values, empty/invalid inputs, error paths/exceptions, and conformance to the interface contracts and BACKEND_DESIGN.md.",
    'Do not edit files. If coverage is insufficient → verdict=fail, goBackTo="tdd".',
    "",
    VERDICT_FORMAT,
  ].join("\n");
}

export function buildReviewSolutionPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string {
  return [
    header(cfg, state, attempt),
    "",
    "## Your stage: 5. FULL SOLUTION REVIEW (read-only)",
    "Assess the whole solution: implementation correctness, conformance to the tests, architecture and guidelines, code quality, and gaps in error handling.",
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
