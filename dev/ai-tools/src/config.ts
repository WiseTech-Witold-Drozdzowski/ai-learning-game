import { readFileSync } from "node:fs";
import { dirname, isAbsolute, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import type { StageId } from "./types.js";

const HERE = dirname(fileURLToPath(import.meta.url));
/** dev/ai-tools */
export const TOOL_ROOT = resolve(HERE, "..");

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
  maxAttemptsPerStage: number;
  maxTotalIterations: number;
  commandTimeoutMs: number;
  claude: {
    model?: string;
    maxTurns: number;
    permissionMode: string;
    allowedTools: Record<StageId | "router", string[]>;
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
