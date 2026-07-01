import ora from "ora";
import chalk from "chalk";
import type { AiToolsConfig } from "./config.js";
import { pickModel } from "./config.js";
import { addUsage, runAgent } from "./claude.js";
import { stageChanges } from "./git.js";
import { getStage } from "./stages.js";
import { saveRun } from "./state.js";
import { SYSTEM_PROMPT } from "./prompts.js";
import { renderUsageLine, startLiveStage, STAGE_LABELS } from "./ui.js";
import type { AgentResult, HistoryEntry, RunState, StageId, ValidationResult } from "./types.js";

function record(state: RunState, entry: Omit<HistoryEntry, "ts">): void {
  state.history.push({ ts: new Date().toISOString(), ...entry });
}

/**
 * Run the workflow from the current stage until it pauses for human review,
 * finishes, or fails (limits exceeded / agent error). Mutates and persists `state`.
 */
export async function runUntilPauseOrDone(state: RunState, cfg: AiToolsConfig): Promise<RunState> {
  state.status = "running";

  while (true) {
    if (state.currentStage === "human-review") {
      // Stage the agent's work so the human's later `// TODO` edits are an unstaged diff.
      const staged = await stageChanges(cfg.repoRoot, cfg.stagePaths, cfg.commandTimeoutMs);
      console.log(
        staged.ok
          ? chalk.gray(`  ↳ staged changes under ${cfg.stagePaths.join(", ")} for human review`)
          : chalk.yellow(`  ↳ could not stage changes: ${staged.error ?? "unknown"}`),
      );
      state.status = "paused";
      state.message = "Waiting for human review (resume / accept).";
      saveRun(state);
      return state;
    }

    if (state.totalIterations >= cfg.maxTotalIterations) {
      return fail(state, `Workflow iteration limit exceeded (${cfg.maxTotalIterations}).`);
    }

    const stage = getStage(state.currentStage);
    const attempt = (state.attempts[stage.id] ?? 0) + 1;
    if (attempt > cfg.maxAttemptsPerStage) {
      return fail(state, `Stage "${STAGE_LABELS[stage.id]}" exceeded its attempt limit (${cfg.maxAttemptsPerStage}).`);
    }
    state.attempts[stage.id] = attempt;
    state.totalIterations += 1;
    saveRun(state);

    // --- CREATE / REVIEW step (agent) ---
    const verb = stage.isReview ? "Review" : "Create";
    const live = startLiveStage(
      `${chalk.cyan(STAGE_LABELS[stage.id])} — ${verb} (attempt ${attempt}/${cfg.maxAttemptsPerStage})`,
    );

    let agent: AgentResult;
    try {
      agent = await runAgent({
        prompt: stage.buildPrompt(cfg, state, attempt),
        systemPrompt: SYSTEM_PROMPT,
        allowedTools: stage.tools(cfg),
        cwd: cfg.repoRoot,
        model: pickModel(cfg, stage.id),
        maxTurns: cfg.claude.maxTurns,
        permissionMode: cfg.claude.permissionMode,
        onEvent: live.onEvent,
      });
    } catch (err) {
      live.fail(`${STAGE_LABELS[stage.id]} — agent error`);
      return fail(state, `Agent threw an exception: ${String(err)}`);
    }

    state.totals = addUsage(state.totals, agent.usage);
    record(state, {
      stage: stage.id,
      attempt,
      phase: "create",
      ok: agent.ok,
      summary: agent.ok ? `${verb} done` : `Agent error: ${agent.error ?? "unknown"}`,
      usage: agent.usage,
    });

    if (agent.ok) {
      live.succeed(`${STAGE_LABELS[stage.id]} — ${verb} OK`);
    } else {
      live.fail(`${STAGE_LABELS[stage.id]} — ${verb} failed`);
    }
    renderUsageLine(`${verb}`, agent.usage);
    saveRun(state);

    if (!agent.ok) {
      // Agent itself failed — retry the same stage (counts against the attempt limit).
      state.currentStage = stage.back;
      state.message = `Agent did not finish the stage: ${agent.error ?? "unknown error"}`;
      saveRun(state);
      continue;
    }

    // --- VALIDATE step (programmatic) ---
    const vSpinner = ora({ text: `${chalk.cyan(STAGE_LABELS[stage.id])} — Validation`, spinner: "dots" }).start();
    let validation: ValidationResult;
    try {
      validation = await stage.validate(cfg, state, agent);
    } catch (err) {
      vSpinner.fail("Validation — error");
      return fail(state, `Validator threw an exception: ${String(err)}`);
    }

    record(state, {
      stage: stage.id,
      attempt,
      phase: "validate",
      ok: validation.ok,
      summary: validation.summary,
    });

    if (validation.ok) {
      vSpinner.succeed(`Validation OK — ${validation.summary}`);
      state.currentStage = stage.next;
      state.message = undefined;
      state.lastFailure = undefined;
    } else {
      vSpinner.fail(`Validation FAIL — ${validation.summary}`);
      if (validation.details) console.log(chalk.gray(indent(validation.details)));
      state.currentStage = validation.goBackTo ?? stage.back;
      state.message = `Rolling back to: ${STAGE_LABELS[state.currentStage]} — ${validation.summary}`;
      // Carry the exact failure forward so the next attempt sees what actually broke
      // instead of retrying blind (the root cause of stages burning all their attempts).
      state.lastFailure = { stage: stage.id, summary: validation.summary, details: validation.details };
    }
    saveRun(state);
  }
}

function fail(state: RunState, message: string): RunState {
  state.status = "failed";
  state.message = message;
  saveRun(state);
  return state;
}

function indent(text: string): string {
  return text
    .split("\n")
    .map((l) => `      │ ${l}`)
    .join("\n");
}

/**
 * Prepare a failed run to be resumed from the stage it died on with a fresh budget.
 *
 * A run fails when a stage burns through its per-stage attempt limit or the workflow
 * hits the total-iteration cap. In both cases the offending counters are still maxed
 * out, so a plain `resume` would immediately re-trip the same limit at the top of the
 * loop. Clearing the current stage's attempts and the global iteration counter lets the
 * run restart from the same stage at attempt 0. No-op for runs that aren't failed.
 */
export function restartFailedStage(state: RunState): void {
  if (state.status !== "failed") return;
  state.attempts[state.currentStage] = 0;
  state.totalIterations = 0;
  state.status = "running";
  state.message = `Restarted failed stage with a fresh budget: ${STAGE_LABELS[state.currentStage]}`;
  saveRun(state);
}

/** Mark a paused run as accepted by the human. */
export function acceptRun(state: RunState): void {
  state.status = "done";
  state.currentStage = "human-review";
  state.message = "Accepted by the human.";
  saveRun(state);
}

/** Route a paused run back to an earlier stage based on the router agent's decision. */
export function resumeAt(state: RunState, stage: StageId, note: string): void {
  state.context.push(`[human review → ${stage}] ${note}`);
  state.attempts[stage] = 0; // give the revisited stage a fresh budget
  state.currentStage = stage;
  state.status = "running";
  state.message = `Resumed from: ${STAGE_LABELS[stage]}`;
  saveRun(state);
}
