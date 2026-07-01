import ora, { type Ora } from "ora";
import chalk from "chalk";
import { totalTokens } from "./claude.js";
import type { RunState, StageId, UsageDelta } from "./types.js";

export const STAGE_ORDER: StageId[] = [
  "analysis",
  "interface",
  "tdd",
  "review-tdd",
  "implementation",
  "review-solution",
  "human-review",
];

export const STAGE_LABELS: Record<StageId, string> = {
  analysis: "0. Analiza / plan",
  interface: "1. Interfaces (skeleton)",
  tdd: "2. TDD — tests (red)",
  "review-tdd": "3. Test review",
  implementation: "4. Implementation (green)",
  "review-solution": "5. Full review",
  "human-review": "5.2 Human review",
};

const STATUS_COLOR: Record<RunState["status"], (s: string) => string> = {
  running: chalk.cyan,
  paused: chalk.yellow,
  done: chalk.green,
  failed: chalk.red,
};

function fmtTokens(n: number): string {
  return n >= 1000 ? `${(n / 1000).toFixed(1)}k` : String(n);
}

function fmtUsd(n: number): string {
  return `$${n.toFixed(4)}`;
}

export function banner(text: string): void {
  const line = "─".repeat(Math.max(text.length + 2, 10));
  console.log(chalk.bold.blueBright(`\n┌${line}┐`));
  console.log(chalk.bold.blueBright(`│ ${text} │`));
  console.log(chalk.bold.blueBright(`└${line}┘`));
}

/** Render the full pipeline with the current stage and per-stage attempts. */
export function renderPipeline(state: RunState): void {
  const currentIdx = STAGE_ORDER.indexOf(state.currentStage);
  console.log(chalk.bold(`\nTask ${chalk.white(state.id)}  ·  ${chalk.gray(state.task.title)}`));
  console.log(`Status: ${STATUS_COLOR[state.status](state.status.toUpperCase())}  ·  iterations: ${state.totalIterations}\n`);

  STAGE_ORDER.forEach((stage, idx) => {
    const attempts = state.attempts[stage] ?? 0;
    let marker: string;
    let label: string;
    if (state.status === "done") {
      marker = chalk.green("✔");
      label = chalk.gray(STAGE_LABELS[stage]);
    } else if (idx < currentIdx) {
      marker = chalk.green("✔");
      label = chalk.gray(STAGE_LABELS[stage]);
    } else if (idx === currentIdx) {
      marker = state.status === "failed" ? chalk.red("✘") : chalk.cyan("▶");
      label = chalk.bold.white(STAGE_LABELS[stage]);
    } else {
      marker = chalk.gray("○");
      label = chalk.gray(STAGE_LABELS[stage]);
    }
    const att = attempts > 0 ? chalk.gray(`  (attempts: ${attempts})`) : "";
    console.log(`  ${marker} ${label}${att}`);
  });
  if (state.message) console.log(chalk.gray(`\n  ${state.message}`));
}

export function renderUsageLine(label: string, usage: UsageDelta): void {
  console.log(
    chalk.gray(
      `    ↳ ${label}: ${fmtTokens(totalTokens(usage))} tok ` +
        `(in ${fmtTokens(usage.inputTokens)} / out ${fmtTokens(usage.outputTokens)} / ` +
        `cache ${fmtTokens(usage.cacheReadTokens + usage.cacheCreationTokens)}) · ` +
        `${fmtUsd(usage.costUsd)} · ${usage.numTurns} turns`,
    ),
  );
}

/** Final token / cost summary table. */
export function renderSummary(state: RunState): void {
  const t = state.totals;
  banner("Usage summary");
  const rows: [string, string][] = [
    ["Input tokens", fmtTokens(t.inputTokens)],
    ["Output tokens", fmtTokens(t.outputTokens)],
    ["Cache (read+write)", fmtTokens(t.cacheReadTokens + t.cacheCreationTokens)],
    ["Total tokens", chalk.bold(fmtTokens(totalTokens(t)))],
    ["Total cost", chalk.bold.green(fmtUsd(t.costUsd))],
    ["Agent turns", String(t.numTurns)],
    ["Agent time", `${(t.durationMs / 1000).toFixed(1)} s`],
    ["Workflow iterations", String(state.totalIterations)],
  ];
  const width = Math.max(...rows.map(([k]) => k.length));
  for (const [k, v] of rows) {
    console.log(`  ${k.padEnd(width)}  ${v}`);
  }
}

// --- Live agent activity -----------------------------------------------------

function truncate(s: string, n: number): string {
  return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}

/** Last two path segments — enough to identify a file without flooding the line. */
function shortPath(p?: string): string {
  if (!p) return "";
  return p.split(/[\\/]/).slice(-2).join("/");
}

function formatTool(name: string, input: Record<string, any>): string {
  switch (name) {
    case "Read":
      return `📖 Read ${shortPath(input.file_path)}`;
    case "Edit":
    case "MultiEdit":
      return `✏️  Edit ${shortPath(input.file_path)}`;
    case "Write":
      return `📝 Write ${shortPath(input.file_path)}`;
    case "Bash":
      return `❯ ${truncate(String(input.command ?? ""), 70)}`;
    case "Grep":
      return `🔎 Grep ${truncate(String(input.pattern ?? ""), 50)}`;
    case "Glob":
      return `🔎 Glob ${truncate(String(input.pattern ?? ""), 50)}`;
    default:
      return `🔧 ${name}`;
  }
}

/** Turn one streamed event into zero or more human-readable activity lines. */
export function describeEvent(ev: any): string[] {
  if (!ev || ev.type !== "assistant") return [];
  const content = ev.message?.content;
  if (!Array.isArray(content)) return [];
  const out: string[] = [];
  for (const b of content) {
    if (b?.type === "tool_use") {
      out.push(formatTool(String(b.name ?? "?"), b.input ?? {}));
    } else if (b?.type === "text" && typeof b.text === "string") {
      const line = b.text.trim().split("\n").find((l: string) => l.trim().length > 0);
      if (line) out.push(`💬 ${truncate(line.trim(), 80)}`);
    }
  }
  return out;
}

export interface LiveStage {
  /** Feed a streamed agent event; updates the spinner (and verbose log if toggled). */
  onEvent(ev: any): void;
  succeed(text: string): void;
  fail(text: string): void;
  stop(): void;
}

/**
 * Spinner that shows the agent's latest action live. Press `v` to toggle a verbose
 * mode that also prints every action as a persistent line (full trace).
 */
export function startLiveStage(baseText: string): LiveStage {
  const spinner = ora({ text: baseText, spinner: "dots" }).start();
  let verbose = false;
  let last = "";

  const stdin = process.stdin;
  const isTty = Boolean(stdin.isTTY);

  // Print a persistent line without corrupting the spinner's current line.
  const note = (line: string): void => {
    const keep = spinner.text;
    spinner.stop();
    console.log(line);
    spinner.start(keep);
  };

  const onData = (b: Buffer): void => {
    const k = b.toString();
    if (k === "v" || k === "V") {
      verbose = !verbose;
      note(chalk.gray(`      › verbose ${verbose ? "ON" : "OFF"} (toggle with "v")`));
    } else if (k.charCodeAt(0) === 3) {
      cleanup();
      spinner.stop();
      process.exit(130); // Ctrl+C while in raw mode
    }
  };

  function cleanup(): void {
    if (!isTty) return;
    stdin.off("data", onData);
    stdin.setRawMode?.(false);
    stdin.pause();
  }

  if (isTty) {
    stdin.setRawMode?.(true);
    stdin.resume();
    stdin.on("data", onData);
    spinner.suffixText = chalk.gray('  (press "v" for live detail)');
  }

  const render = (): void => {
    spinner.text = last ? `${baseText}  ${chalk.gray("· ▸")} ${chalk.white(last)}` : baseText;
  };

  return {
    onEvent(ev: any): void {
      for (const line of describeEvent(ev)) {
        last = line;
        if (verbose) note(chalk.gray(`      │ ${line}`));
      }
      render();
    },
    succeed(text: string): void {
      cleanup();
      spinner.suffixText = "";
      spinner.succeed(text);
    },
    fail(text: string): void {
      cleanup();
      spinner.suffixText = "";
      spinner.fail(text);
    },
    stop(): void {
      cleanup();
      spinner.stop();
    },
  };
}

export function renderHistoryTail(state: RunState, n = 8): void {
  const tail = state.history.slice(-n);
  if (tail.length === 0) return;
  console.log(chalk.bold("\nRecent steps:"));
  for (const h of tail) {
    const mark = h.ok ? chalk.green("✔") : chalk.red("✘");
    const time = h.ts.slice(11, 19);
    console.log(`  ${mark} ${chalk.gray(time)} [${STAGE_LABELS[h.stage]} · ${h.phase} #${h.attempt}] ${h.summary}`);
  }
}
