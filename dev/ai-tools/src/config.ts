import { readFileSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { StageId } from "./types.js";

const HERE = dirname(fileURLToPath(import.meta.url));
/** dev/ai-tools */
export const TOOL_ROOT = resolve(HERE, "..");

/** Keys addressable for per-agent model / tool config: the pipeline stages plus the router and the master-agent rescue. */
export type AgentKey = StageId | "router" | "master-agent";

export interface AiToolsConfig {
  /** Absolute cwd for Claude agents and validation commands. */
  repoRoot: string;
  docsDir: string;
  guidelinesDir: string;
  testGuidelines: string;
  implementationGuidelines: string;
  contextDocs: string[];
  commands: {
    compileMain: string;
    compileTests: string;
    test: string;
  };
  /** Paths (relative to repoRoot) to stage before human review / scan for TODOs. */
  stagePaths: string[];
  /** Comment markers the human leaves in code for the next iteration. */
  todoMarkers: string[];
  maxAttemptsPerStage: number;
  maxTotalIterations: number;
  /** How many times the master-agent may be invoked to unblock a single stuck stage before the run fails. */
  maxMasterInterventions: number;
  commandTimeoutMs: number;
  claude: {
    /** Fallback model when no per-stage override is set. */
    model?: string;
    /** Per-agent model overrides (stages + router + master-agent). */
    models?: Partial<Record<AgentKey, string>>;
    maxTurns: number;
    permissionMode: string;
    allowedTools: Record<AgentKey, string[]>;
  };
}

interface RawConfig extends Omit<AiToolsConfig, "repoRoot"> {
  repoRoot: string;
}

/** Resolve a path that is either absolute or relative to the ai-tools dir. */
function abs(p: string): string {
  return isAbsolute(p) ? p : resolve(TOOL_ROOT, p);
}

export function loadConfig(overridePath?: string): AiToolsConfig {
  const path = overridePath ? abs(overridePath) : resolve(TOOL_ROOT, "config/default.json");
  const raw = JSON.parse(readFileSync(path, "utf8")) as RawConfig;
  return {
    ...raw,
    repoRoot: abs(raw.repoRoot),
  };
}

/** Absolute path inside the repo root (where agents read docs / code). */
export function inRepo(cfg: AiToolsConfig, ...parts: string[]): string {
  return resolve(cfg.repoRoot, ...parts);
}

/** Model for a given agent (per-agent override, falling back to claude.model). */
export function pickModel(cfg: AiToolsConfig, key: AgentKey): string | undefined {
  return cfg.claude.models?.[key] ?? cfg.claude.model;
}
