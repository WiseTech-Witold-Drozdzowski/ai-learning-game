import chalk from "chalk";
import { totalTokens } from "./claude.js";
import type { RunState, StageId, UsageDelta } from "./types.js";

export const STAGE_ORDER: StageId[] = [
  "interface",
  "tdd",
  "review-tdd",
  "implementation",
  "review-solution",
  "human-review",
];

export const STAGE_LABELS: Record<StageId, string> = {
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
