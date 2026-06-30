import type { AiToolsConfig } from "./config.js";
import { extractJson } from "./claude.js";
import {
  buildImplementationPrompt,
  buildInterfacePrompt,
  buildReviewSolutionPrompt,
  buildReviewTddPrompt,
  buildTddPrompt,
} from "./prompts.js";
import { validateCompileMain, validateTddRed, validateTestsGreen } from "./validators.js";
import type { AgentResult, ReviewVerdict, RunState, StageId, ValidationResult } from "./types.js";

export interface StageDef {
  id: StageId;
  /** Build the create/review prompt for the producing agent. */
  buildPrompt(cfg: AiToolsConfig, state: RunState, attempt: number): string;
  tools(cfg: AiToolsConfig): string[];
  /** Programmatic validation. Review stages parse the agent verdict; others run commands. */
  validate(cfg: AiToolsConfig, state: RunState, agent: AgentResult): Promise<ValidationResult>;
  /** Stage to advance to on success. */
  next: StageId;
  /** Default stage to route back to on failure. */
  back: StageId;
  isReview: boolean;
}

function parseVerdict(agent: AgentResult, defaultBack: StageId): ValidationResult {
  const v = extractJson<ReviewVerdict>(agent.text);
  if (!v || (v.verdict !== "pass" && v.verdict !== "fail")) {
    return {
      ok: false,
      summary: "Could not read the review verdict (no valid JSON)",
      details: agent.text.slice(-600),
      goBackTo: defaultBack,
    };
  }
  const ok = v.verdict === "pass";
  return {
    ok,
    summary: ok ? "Review: PASS" : `Review: FAIL — ${(v.reasons ?? []).join("; ")}`,
    details: (v.reasons ?? []).join("\n"),
    goBackTo: ok ? undefined : (v.goBackTo ?? defaultBack),
  };
}

export const STAGES: Record<Exclude<StageId, "human-review">, StageDef> = {
  interface: {
    id: "interface",
    buildPrompt: buildInterfacePrompt,
    tools: (c) => c.claude.allowedTools.interface,
    validate: (cfg) => validateCompileMain(cfg),
    next: "tdd",
    back: "interface",
    isReview: false,
  },
  tdd: {
    id: "tdd",
    buildPrompt: buildTddPrompt,
    tools: (c) => c.claude.allowedTools.tdd,
    validate: (cfg) => validateTddRed(cfg),
    next: "review-tdd",
    back: "tdd",
    isReview: false,
  },
  "review-tdd": {
    id: "review-tdd",
    buildPrompt: buildReviewTddPrompt,
    tools: (c) => c.claude.allowedTools["review-tdd"],
    validate: async (_cfg, _state, agent) => parseVerdict(agent, "tdd"),
    next: "implementation",
    back: "tdd",
    isReview: true,
  },
  implementation: {
    id: "implementation",
    buildPrompt: buildImplementationPrompt,
    tools: (c) => c.claude.allowedTools.implementation,
    validate: (cfg) => validateTestsGreen(cfg),
    next: "review-solution",
    back: "implementation",
    isReview: false,
  },
  "review-solution": {
    id: "review-solution",
    buildPrompt: buildReviewSolutionPrompt,
    tools: (c) => c.claude.allowedTools["review-solution"],
    validate: async (_cfg, _state, agent) => parseVerdict(agent, "implementation"),
    next: "human-review",
    back: "implementation",
    isReview: true,
  },
};

export function getStage(id: StageId): StageDef {
  if (id === "human-review") throw new Error("human-review is handled by the engine, not a StageDef");
  return STAGES[id];
}
