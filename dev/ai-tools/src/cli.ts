#!/usr/bin/env -S npx tsx
import { Command } from "commander";
import chalk from "chalk";
import { editor, select } from "@inquirer/prompts";
import { resolve } from "node:path";
import { loadConfig, inRepo, pickModel, type AiToolsConfig } from "./config.js";
import { findIssueFiles, taskFromFile, taskFromText } from "./tasks.js";
import { collectTodos, type TodoNote } from "./git.js";
import { latestRun, listRuns, loadRun, newRun, saveRun } from "./state.js";
import { acceptRun, restartFailedStage, resumeAt, runUntilPauseOrDone } from "./engine.js";
import { addUsage, extractJson, runAgent } from "./claude.js";
import { buildRouterPrompt, SYSTEM_PROMPT } from "./prompts.js";
import { banner, renderHistoryTail, renderPipeline, renderSummary, STAGE_LABELS } from "./ui.js";
import type { RunState, StageId, TaskSpec } from "./types.js";

const program = new Command();
program.name("ai-task").description("Loop-based developer task runner on Claude CLI agents");

program
  .command("run")
  .description("Run a new task through the whole workflow")
  .option("-t, --task <text>", "task content pasted directly")
  .option("-f, --file <path>", "path to a task file (e.g. docs/issue-5.md)")
  .option("-c, --config <path>", "path to a config file")
  .action(async (opts) => {
    const cfg = loadConfig(opts.config);
    const task = await resolveTask(cfg, opts);
    if (!task) return;

    const state = newRun(task);
    saveRun(state);
    banner(`New task · ${state.id}`);
    console.log(`${chalk.bold("Title:")} ${task.title}`);
    console.log(`${chalk.bold("Source:")} ${task.source}\n`);

    await driveToCompletion(state, cfg);
  });

program
  .command("resume")
  .argument("[id]", "run id (defaults to the latest)")
  .description("Resume a paused task")
  .option("-c, --config <path>", "path to a config file")
  .action(async (id: string | undefined, opts) => {
    const cfg = loadConfig(opts.config);
    const state = id ? loadRun(id) : latestRun();
    if (!state) return console.log(chalk.red("No runs to resume."));
    banner(`Resuming · ${state.id}`);
    if (state.status === "failed") {
      console.log(
        chalk.yellow(
          `  ↳ previous run failed (${state.message ?? "unknown"}).\n` +
            `  ↳ restarting from "${STAGE_LABELS[state.currentStage]}" with a fresh attempt budget.`,
        ),
      );
      restartFailedStage(state);
    }
    await driveToCompletion(state, cfg);
  });

program
  .command("status")
  .argument("[id]", "run id (defaults to the latest)")
  .description("Show a task's status")
  .action((id: string | undefined) => {
    const state = id ? loadRun(id) : latestRun();
    if (!state) return console.log(chalk.red("No runs."));
    renderPipeline(state);
    renderHistoryTail(state);
    renderSummary(state);
  });

program
  .command("list")
  .description("List all tasks")
  .action(() => {
    const runs = listRuns();
    if (runs.length === 0) return console.log(chalk.gray("No tasks."));
    banner("Tasks");
    for (const r of runs) {
      console.log(
        `  ${chalk.white(r.id)}  ${statusTag(r)}  ${chalk.gray(STAGE_LABELS[r.currentStage])}  ${chalk.gray("·")} ${r.task.title}`,
      );
    }
  });

program.parseAsync().catch((err) => {
  console.error(chalk.red(`\nError: ${String(err)}`));
  process.exit(1);
});

// ---------------------------------------------------------------------------

function statusTag(r: RunState): string {
  const c = { running: chalk.cyan, paused: chalk.yellow, done: chalk.green, failed: chalk.red }[r.status];
  return c(`[${r.status}]`.padEnd(9));
}

async function resolveTask(cfg: AiToolsConfig, opts: { task?: string; file?: string }): Promise<TaskSpec | undefined> {
  if (opts.task) return taskFromText(opts.task);
  if (opts.file) return taskFromFile(resolve(process.cwd(), opts.file));

  const mode = await select({
    message: "How do you want to provide the task?",
    choices: [
      { name: "Select issue", value: "issue" },
      { name: "Write task content", value: "paste" },
    ],
  });

  if (mode === "issue") {
    const issues = findIssueFiles(cfg);
    if (issues.length > 0) {
      const choice = await select({
        message: `Pick an issue (found under ${cfg.docsDir})`,
        choices: issues.map((i) => ({ name: i.name, value: i.path })),
      });
      return taskFromFile(choice);
    }
    console.log(chalk.gray(`No issue* files found under ${inRepo(cfg, cfg.docsDir)} — paste the task manually.`));
  }

  const body = await editor({ message: "Paste the task content (save and close the editor)", waitForUserInput: false });
  if (!body.trim()) {
    console.log(chalk.red("Empty task content — aborting."));
    return undefined;
  }
  return taskFromText(body);
}

/** Run the engine, then handle the human-review pause loop until done/failed/quit. */
async function driveToCompletion(state: RunState, cfg: AiToolsConfig): Promise<void> {
  while (true) {
    await runUntilPauseOrDone(state, cfg);
    renderPipeline(state);

    if (state.status === "failed") {
      renderHistoryTail(state);
      renderSummary(state);
      console.log(chalk.red(`\n✘ Task not completed: ${state.message ?? ""}`));
      return;
    }
    if (state.status !== "paused") {
      renderSummary(state);
      return;
    }

    // status === paused → human review (stage 5.2)
    banner("Human review (5.2)");
    renderHistoryTail(state);
    printLastReview(state);

    const decision = await select({
      message: "What do we do with the finished solution?",
      choices: [
        { name: "✔ Accept — finish", value: "accept" },
        { name: "↻ Iterate — add context, the agent decides where to roll back", value: "iterate" },
        { name: "⏸ Leave paused (resume later)", value: "quit" },
      ],
    });

    if (decision === "accept") {
      acceptRun(state);
      renderPipeline(state);
      renderSummary(state);
      console.log(chalk.green("\n✔ Accepted."));
      return;
    }
    if (decision === "quit") {
      console.log(chalk.yellow(`\n⏸ Paused. Resume with: npm run resume ${state.id}`));
      renderSummary(state);
      return;
    }

    // iterate
    const todos = await collectTodos(cfg.repoRoot, cfg.stagePaths, cfg.todoMarkers, cfg.commandTimeoutMs);
    if (todos.length) {
      console.log(chalk.bold(`\nFound ${todos.length} TODO marker(s) in your edits:`));
      for (const t of todos) console.log(chalk.gray(`  • ${t.file}:${t.line} — ${t.text}`));
    } else {
      console.log(chalk.gray("\nNo TODO markers found in your edits (working tree vs staged)."));
    }

    console.log(
      chalk.gray(
        "\nAdd extra context: what is generally wrong / what should change (save & close the editor; leave empty to skip).",
      ),
    );
    console.log(
      chalk.gray(
        "By default the iteration resumes from Implementation (fast). Mention a rewrite/redesign " +
          "(e.g. \"przepisz\", \"od nowa\", \"interfejs\", \"kontrakt\", \"rewrite\") to route further back.",
      ),
    );
    const note = await editor({
      message: "What's generally wrong? Extra context for the agent (optional)",
      waitForUserInput: false,
    });
    const feedback = buildFeedback(note, todos);
    // Default to the shortest loop (implementation). Only consult the router when the human
    // explicitly asks for a rewrite/redesign that may need earlier stages.
    let target: StageId = "implementation";
    if (wantsRewrite(note)) {
      target = await decideGoBack(state, cfg, feedback);
    } else {
      console.log(chalk.gray("  No rewrite requested — resuming from Implementation (skipping the router)."));
    }
    // Surface the human TODOs to every downstream stage via the run context.
    for (const t of todos) state.context.push(`[human TODO] ${t.file}:${t.line} — ${t.text}`);
    resumeAt(state, target, note || "(see human TODO notes)");
    console.log(chalk.cyan(`\n↻ Resuming from: ${STAGE_LABELS[target]}`));
  }
}

/** True when the human's note signals a rewrite/redesign that may need an earlier stage. */
function wantsRewrite(note: string): boolean {
  const re =
    /\b(przepis\w*|od nowa|od zera|przeprojekt\w*|interfejs\w*|kontrakt\w*|sygnatur\w*|architekt\w*|rewrite|redesign|re-?write|from scratch|scratch|interface|contract|signature)\b/i;
  return re.test(note);
}

/** Combine the human's free-text note with the extracted TODO markers for the router. */
function buildFeedback(note: string, todos: TodoNote[]): string {
  const parts: string[] = [];
  if (note.trim()) parts.push(note.trim());
  if (todos.length) {
    parts.push(
      "Human TODO markers left in the code:",
      ...todos.map((t) => `- ${t.file}:${t.line} — ${t.text}`),
    );
  }
  return parts.join("\n") || "(no note)";
}

function printLastReview(state: RunState): void {
  const last = [...state.history].reverse().find((h) => h.stage === "review-solution" && h.phase === "create");
  if (last) console.log(chalk.gray(`\nLast full review is available in this run's history (${state.id}).`));
}

/** Ask a router agent where to resume, based on the human's note. Falls back to interactive choice. */
async function decideGoBack(state: RunState, cfg: AiToolsConfig, note: string): Promise<StageId> {
  console.log(chalk.gray("…asking the router agent where to resume"));
  const agent = await runAgent({
    prompt: buildRouterPrompt(cfg, state, note),
    systemPrompt: SYSTEM_PROMPT,
    allowedTools: cfg.claude.allowedTools.router,
    cwd: cfg.repoRoot,
    model: pickModel(cfg, "router"),
    maxTurns: 10,
    permissionMode: cfg.claude.permissionMode,
  });
  state.totals = addUsage(state.totals, agent.usage);
  saveRun(state);

  const parsed = extractJson<{ goBackTo?: string; reason?: string }>(agent.text);
  const allowed: StageId[] = ["interface", "tdd", "implementation"];
  if (parsed?.goBackTo && (allowed as string[]).includes(parsed.goBackTo)) {
    if (parsed.reason) console.log(chalk.gray(`  router: ${parsed.reason}`));
    return parsed.goBackTo as StageId;
  }

  console.log(chalk.yellow("  Router gave no clear answer — choose manually."));
  return select({
    message: "Which stage to resume from?",
    choices: allowed.map((s) => ({ name: STAGE_LABELS[s], value: s })),
  });
}
