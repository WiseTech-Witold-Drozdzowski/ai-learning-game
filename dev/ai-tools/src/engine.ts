import ora from "ora";
import chalk from "chalk";
import type { AiToolsConfig } from "./config.js";
import { pickModel } from "./config.js";
import { addUsage, runAgent } from "./claude.js";
import { stageChanges } from "./git.js";
import { getStage } from "./stages.js";
import { saveRun } from "./state.js";
import { logAgentEvent, logAgentResult } from "./runlog.js";
import { buildMasterAgentPrompt, SYSTEM_PROMPT } from "./prompts.js";
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
      // The stage burned its whole attempt budget. Before failing the run, let the
      // master-agent (full permissions, strong model) try to unblock it — but only
      // up to maxMasterInterventions times per stage, so we can't loop forever.
      const used = state.masterInterventions?.[stage.id] ?? 0;
      if (used >= cfg.maxMasterInterventions) {
        return fail(
          state,
          `Stage "${STAGE_LABELS[stage.id]}" exceeded its attempt limit (${cfg.maxAttemptsPerStage}) ` +
            `and the master-agent (max ${cfg.maxMasterInterventions}) could not unblock it.`,
        );
      }
      const unblocked = await runMasterAgent(state, cfg, stage.id, used + 1);
      if (!unblocked) return state; // fail() already applied inside
      continue; // stage re-runs with a fresh attempt budget
    }
    state.attempts[stage.id] = attempt;
    state.totalIterations += 1;
    saveRun(state);

    // --- CREATE / REVIEW step (agent) ---
    const verb = stage.isReview ? "Review" : "Create";
    const live = startLiveStage(
      `${chalk.cyan(STAGE_LABELS[stage.id])} — ${verb} (attempt ${attempt}/${cfg.maxAttemptsPerStage})`,
    );

    const model = pickModel(cfg, stage.id);
    let agent: AgentResult;
    try {
      agent = await runAgent({
        prompt: stage.buildPrompt(cfg, state, attempt),
        systemPrompt: SYSTEM_PROMPT,
        allowedTools: stage.tools(cfg),
        cwd: cfg.repoRoot,
        model,
        maxTurns: cfg.claude.maxTurns,
        permissionMode: cfg.claude.permissionMode,
        onEvent: (ev) => {
          live.onEvent(ev);
          logAgentEvent(state.id, stage.id, attempt, ev);
        },
      });
    } catch (err) {
      live.fail(`${STAGE_LABELS[stage.id]} — agent error`);
      return fail(state, `Agent threw an exception: ${String(err)}`);
    }

    logAgentResult(state.id, stage.id, attempt, agent);
    state.totals = addUsage(state.totals, agent.usage);
    record(state, {
      stage: stage.id,
      attempt,
      phase: "create",
      ok: agent.ok,
      summary: agent.ok ? `${verb} done` : `Agent error: ${agent.error ?? "unknown"}`,
      usage: agent.usage,
      model,
    });

    if (agent.ok) {
      live.succeed(`${STAGE_LABELS[stage.id]} — ${verb} OK`);
    } else {
      live.fail(`${STAGE_LABELS[stage.id]} — ${verb} failed`);
    }
    renderUsageLine(`${verb}`, agent.usage);
    saveRun(state);

    if (!agent.ok) {
      // Agent itself failed (commonly the turn/time limit) — retry the same stage
      // (counts against the attempt limit), but carry forward WHY so the retry
      // doesn't repeat the same wandering. Without this, `retryNote` stays empty
      // for agent errors and the stage burns every attempt hitting the same wall.
      state.lastFailure = {
        stage: stage.id,
        summary: agent.error?.trim() || "the agent hit the turn/time limit before finishing",
        details:
          "The previous attempt did not finish within its turn budget. Work strictly " +
          "from the PLAN: go straight to the exact files it names, do NOT re-explore " +
          "the codebase, and make your edits directly.",
      };
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

/**
 * Full-permission rescue for a stage that exhausted its normal attempts. Runs a strong
 * agent that may edit code/tests AND run the validation commands itself, then resets the
 * stage's attempts so the normal pipeline re-runs it with a fresh budget. Returns true if
 * the stage was unblocked (caller should `continue`), or false if the run was failed
 * (fail() already applied — caller should return the state).
 */
async function runMasterAgent(
  state: RunState,
  cfg: AiToolsConfig,
  stage: StageId,
  intervention: number,
): Promise<boolean> {
  state.totalIterations += 1;
  // Distinct, high "attempt" number so the master's output log doesn't overwrite the stage's normal attempts.
  const logAttempt = 100 + intervention;
  const model = pickModel(cfg, "master-agent");
  const live = startLiveStage(
    `${chalk.magenta("MASTER")} — unblock ${STAGE_LABELS[stage]} (intervention ${intervention}/${cfg.maxMasterInterventions})`,
  );

  let agent: AgentResult;
  try {
    agent = await runAgent({
      prompt: buildMasterAgentPrompt(cfg, state, stage),
      systemPrompt: SYSTEM_PROMPT,
      allowedTools: cfg.claude.allowedTools["master-agent"],
      cwd: cfg.repoRoot,
      model,
      maxTurns: cfg.claude.maxTurns,
      permissionMode: "bypassPermissions",
      onEvent: (ev) => {
        live.onEvent(ev);
        logAgentEvent(state.id, stage, logAttempt, ev);
      },
    });
  } catch (err) {
    live.fail("MASTER — agent error");
    fail(state, `Master-agent threw an exception: ${String(err)}`);
    return false;
  }

  logAgentResult(state.id, stage, logAttempt, agent);
  state.totals = addUsage(state.totals, agent.usage);
  record(state, {
    stage,
    attempt: logAttempt,
    phase: "create",
    ok: agent.ok,
    summary: agent.ok ? "Master-agent rescue done" : `Master-agent error: ${agent.error ?? "unknown"}`,
    usage: agent.usage,
    model,
  });
  renderUsageLine("Master", agent.usage);

  if (!agent.ok) {
    live.fail("MASTER — failed");
    fail(state, `Master-agent errored while trying to unblock "${STAGE_LABELS[stage]}": ${agent.error ?? "unknown"}`);
    return false;
  }

  live.succeed(`MASTER — ${STAGE_LABELS[stage]} unblocked`);

  // Record the intervention, reset the stuck stage's budget, and resume it normally so its
  // own validation/review confirm the fix (the master's edits are already on disk).
  state.masterInterventions ??= {};
  state.masterInterventions[stage] = intervention;
  state.attempts[stage] = 0;
  state.lastFailure = undefined;
  state.context.push(`[master-agent → ${stage}] ${firstLine(agent.text)}`);
  state.currentStage = stage;
  state.message = `Master-agent intervened to unblock: ${STAGE_LABELS[stage]}`;
  saveRun(state);
  return true;
}

/** First non-empty line of the agent's text, capped — used for a compact context note. */
function firstLine(text: string): string {
  const line = text.split("\n").map((l) => l.trim()).find((l) => l.length > 0) ?? "unblocked";
  return line.length > 300 ? `${line.slice(0, 300)}…` : line;
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
