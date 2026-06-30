import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { resolve } from "node:path";
import { TOOL_ROOT } from "./config.js";
import { zeroUsage } from "./claude.js";
import type { RunState, TaskSpec } from "./types.js";

export const RUNS_DIR = resolve(TOOL_ROOT, ".runs");

function ensureDir(): void {
  if (!existsSync(RUNS_DIR)) mkdirSync(RUNS_DIR, { recursive: true });
}

function nowIso(): string {
  return new Date().toISOString();
}

/** Short, sortable, filesystem-safe id. */
function shortId(): string {
  const stamp = nowIso().replace(/[-:T]/g, "").slice(0, 14);
  return `${stamp}-${randomUUID().slice(0, 6)}`;
}

export function newRun(task: TaskSpec): RunState {
  const ts = nowIso();
  return {
    id: shortId(),
    createdAt: ts,
    updatedAt: ts,
    task,
    currentStage: "interface",
    status: "running",
    attempts: {},
    totalIterations: 0,
    history: [],
    context: [],
    totals: zeroUsage(),
  };
}

export function saveRun(state: RunState): void {
  ensureDir();
  state.updatedAt = nowIso();
  writeFileSync(resolve(RUNS_DIR, `${state.id}.json`), JSON.stringify(state, null, 2), "utf8");
}

export function loadRun(id: string): RunState {
  return JSON.parse(readFileSync(resolve(RUNS_DIR, `${id}.json`), "utf8")) as RunState;
}

export function listRuns(): RunState[] {
  ensureDir();
  return readdirSync(RUNS_DIR)
    .filter((f) => f.endsWith(".json"))
    .map((f) => JSON.parse(readFileSync(resolve(RUNS_DIR, f), "utf8")) as RunState)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export function latestRun(): RunState | undefined {
  return listRuns()[0];
}
