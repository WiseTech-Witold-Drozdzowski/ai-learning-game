import { spawn } from "node:child_process";
import type { AgentResult, UsageDelta } from "./types.js";

export function zeroUsage(): UsageDelta {
  return {
    inputTokens: 0,
    outputTokens: 0,
    cacheReadTokens: 0,
    cacheCreationTokens: 0,
    costUsd: 0,
    numTurns: 0,
    durationMs: 0,
  };
}

export function addUsage(a: UsageDelta, b: UsageDelta): UsageDelta {
  return {
    inputTokens: a.inputTokens + b.inputTokens,
    outputTokens: a.outputTokens + b.outputTokens,
    cacheReadTokens: a.cacheReadTokens + b.cacheReadTokens,
    cacheCreationTokens: a.cacheCreationTokens + b.cacheCreationTokens,
    costUsd: a.costUsd + b.costUsd,
    numTurns: a.numTurns + b.numTurns,
    durationMs: a.durationMs + b.durationMs,
  };
}

export function totalTokens(u: UsageDelta): number {
  return u.inputTokens + u.outputTokens + u.cacheReadTokens + u.cacheCreationTokens;
}

export interface RunAgentOptions {
  prompt: string;
  systemPrompt?: string;
  allowedTools: string[];
  cwd: string;
  model?: string;
  maxTurns: number;
  permissionMode: string;
}

/**
 * Run a single Claude CLI agent in headless mode and collect its result + token usage.
 * The prompt is fed via stdin so large prompts don't hit argv limits.
 */
export function runAgent(opts: RunAgentOptions): Promise<AgentResult> {
  const args = [
    "-p",
    "--output-format",
    "json",
    "--permission-mode",
    opts.permissionMode,
    "--max-turns",
    String(opts.maxTurns),
  ];
  if (opts.allowedTools.length) {
    args.push("--allowedTools", opts.allowedTools.join(" "));
  }
  if (opts.model) {
    args.push("--model", opts.model);
  }
  if (opts.systemPrompt) {
    args.push("--append-system-prompt", opts.systemPrompt);
  }

  return new Promise<AgentResult>((resolvePromise) => {
    const child = spawn("claude", args, {
      cwd: opts.cwd,
      stdio: ["pipe", "pipe", "pipe"],
    });

    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (d) => (stdout += d.toString()));
    child.stderr.on("data", (d) => (stderr += d.toString()));

    child.on("error", (err) => {
      resolvePromise({
        ok: false,
        text: "",
        usage: zeroUsage(),
        error: `spawn claude failed: ${String(err)}`,
      });
    });

    child.on("close", (code) => {
      const json = tryParse(stdout);
      if (!json) {
        resolvePromise({
          ok: false,
          text: stdout,
          usage: zeroUsage(),
          error: `could not parse claude JSON (exit ${code}). stderr: ${stderr.slice(0, 800)}`,
        });
        return;
      }
      const u = json.usage ?? {};
      const usage: UsageDelta = {
        inputTokens: num(u.input_tokens),
        outputTokens: num(u.output_tokens),
        cacheReadTokens: num(u.cache_read_input_tokens),
        cacheCreationTokens: num(u.cache_creation_input_tokens),
        costUsd: num(json.total_cost_usd),
        numTurns: num(json.num_turns),
        durationMs: num(json.duration_ms),
      };
      const isError = Boolean(json.is_error) || code !== 0;
      resolvePromise({
        ok: !isError,
        text: typeof json.result === "string" ? json.result : "",
        usage,
        sessionId: json.session_id,
        error: isError ? String(json.result ?? stderr).slice(0, 800) : undefined,
      });
    });

    child.stdin.write(opts.prompt);
    child.stdin.end();
  });
}

function num(v: unknown): number {
  return typeof v === "number" && Number.isFinite(v) ? v : 0;
}

function tryParse(s: string): any | null {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

/** Extract the last JSON object from an agent's free-form text (fenced or bare). */
export function extractJson<T>(text: string): T | null {
  const fenced = [...text.matchAll(/```(?:json)?\s*([\s\S]*?)```/gi)].map((m) => m[1] ?? "");
  const candidates = [...fenced];
  // Fallback: last top-level {...} block.
  const firstBrace = text.indexOf("{");
  const lastBrace = text.lastIndexOf("}");
  if (firstBrace !== -1 && lastBrace > firstBrace) {
    candidates.push(text.slice(firstBrace, lastBrace + 1));
  }
  for (const c of candidates.reverse()) {
    try {
      return JSON.parse(c.trim()) as T;
    } catch {
      // try next candidate
    }
  }
  return null;
}
