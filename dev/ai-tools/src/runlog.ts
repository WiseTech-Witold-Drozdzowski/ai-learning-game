import { appendFileSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { runStepDir } from "./state.js";
import type { AgentResult, StageId } from "./types.js";

/**
 * Per-run, per-step activity log. Every agent action (tool call, text, final result)
 * is appended as one JSON line to `.runs/<id>/steps.jsonl`, and each agent's full
 * output text is saved under `.runs/<id>/outputs/`. This is the raw material the
 * `optimize-ai-tools` skill reads to reconstruct exactly what each agent did and
 * where time / tokens went — the run's `.json` only keeps per-stage summaries.
 */

export interface StepRecord {
  ts: string;
  stage: StageId;
  attempt: number;
  kind: "tool" | "text" | "result";
  /** For kind==="tool": the tool name (Read, Edit, Bash, …). */
  tool?: string;
  /** For kind==="tool": the salient argument (path, command, pattern). */
  detail?: string;
  /** For kind==="text": the agent's thinking/summary line. */
  text?: string;
  /** For kind==="result": final outcome + usage of this agent call. */
  ok?: boolean;
  costUsd?: number;
  totalTokens?: number;
  numTurns?: number;
  durationMs?: number;
  error?: string;
}

function nowIso(): string {
  return new Date().toISOString();
}

function stepsFile(runId: string): string {
  const dir = runStepDir(runId);
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
  return resolve(dir, "steps.jsonl");
}

function append(runId: string, rec: StepRecord): void {
  try {
    appendFileSync(stepsFile(runId), `${JSON.stringify(rec)}\n`, "utf8");
  } catch {
    // Logging must never break a run — swallow filesystem errors.
  }
}

/** Salient argument for a tool call, so the log stays readable without the full input blob. */
function toolDetail(name: string, input: Record<string, any>): string {
  switch (name) {
    case "Read":
    case "Edit":
    case "MultiEdit":
    case "Write":
      return String(input?.file_path ?? "");
    case "Bash":
      return String(input?.command ?? "").slice(0, 200);
    case "Grep":
    case "Glob":
      return String(input?.pattern ?? "").slice(0, 120);
    default:
      return "";
  }
}

/** Turn one streamed agent event into zero or more persisted step records. */
export function logAgentEvent(runId: string, stage: StageId, attempt: number, ev: any): void {
  if (!ev || ev.type !== "assistant") return;
  const content = ev.message?.content;
  if (!Array.isArray(content)) return;
  for (const b of content) {
    if (b?.type === "tool_use") {
      const tool = String(b.name ?? "?");
      append(runId, { ts: nowIso(), stage, attempt, kind: "tool", tool, detail: toolDetail(tool, b.input ?? {}) });
    } else if (b?.type === "text" && typeof b.text === "string") {
      const line = b.text.trim().split("\n").find((l: string) => l.trim().length > 0);
      if (line) append(runId, { ts: nowIso(), stage, attempt, kind: "text", text: line.trim().slice(0, 300) });
    }
  }
}

/** Persist the final result of an agent call (usage + outcome) and its full output text. */
export function logAgentResult(runId: string, stage: StageId, attempt: number, agent: AgentResult): void {
  const u = agent.usage;
  append(runId, {
    ts: nowIso(),
    stage,
    attempt,
    kind: "result",
    ok: agent.ok,
    costUsd: u.costUsd,
    totalTokens: u.inputTokens + u.outputTokens + u.cacheReadTokens + u.cacheCreationTokens,
    numTurns: u.numTurns,
    durationMs: u.durationMs,
    error: agent.error,
  });
  if (agent.text?.trim()) {
    try {
      const dir = resolve(runStepDir(runId), "outputs");
      if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
      writeFileSync(resolve(dir, `${stage}-attempt${attempt}.md`), agent.text, "utf8");
    } catch {
      // best-effort
    }
  }
}
