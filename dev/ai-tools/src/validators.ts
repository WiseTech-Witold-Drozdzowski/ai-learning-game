import { spawn } from "node:child_process";
import type { AiToolsConfig } from "./config.js";
import type { ValidationResult } from "./types.js";

export interface CommandResult {
  code: number;
  stdout: string;
  stderr: string;
  timedOut: boolean;
}

/** Run a shell command in the repo root with a timeout. */
export function runCommand(cmd: string, cwd: string, timeoutMs: number): Promise<CommandResult> {
  return new Promise<CommandResult>((resolvePromise) => {
    const child = spawn(cmd, { cwd, shell: true, stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      child.kill("SIGKILL");
    }, timeoutMs);

    child.stdout.on("data", (d) => (stdout += d.toString()));
    child.stderr.on("data", (d) => (stderr += d.toString()));
    child.on("error", (err) => {
      clearTimeout(timer);
      resolvePromise({ code: 127, stdout, stderr: stderr + String(err), timedOut });
    });
    child.on("close", (code) => {
      clearTimeout(timer);
      resolvePromise({ code: code ?? 1, stdout, stderr, timedOut });
    });
  });
}

function tail(text: string, lines = 40): string {
  return text.split("\n").slice(-lines).join("\n").trim();
}

/** Stage 1: main code must compile. */
export async function validateCompileMain(cfg: AiToolsConfig): Promise<ValidationResult> {
  const r = await runCommand(cfg.commands.compileMain, cfg.repoRoot, cfg.commandTimeoutMs);
  return {
    ok: r.code === 0 && !r.timedOut,
    summary: r.timedOut
      ? "Compilation timed out"
      : r.code === 0
        ? "Main code compiles"
        : "Main code compilation failed",
    details: tail(r.stdout + "\n" + r.stderr),
  };
}

/**
 * Stage 2 (TDD red): tests must compile, but at least one must fail, because the
 * implementation is still NotImplemented. compile-pass + test-run-fail = a valid red phase.
 */
export async function validateTddRed(cfg: AiToolsConfig): Promise<ValidationResult> {
  const compile = await runCommand(cfg.commands.compileTests, cfg.repoRoot, cfg.commandTimeoutMs);
  if (compile.timedOut) {
    return { ok: false, summary: "Test compilation timed out", details: tail(compile.stderr) };
  }
  if (compile.code !== 0) {
    return {
      ok: false,
      summary: "Tests do not compile — fix the test code",
      details: tail(compile.stdout + "\n" + compile.stderr),
    };
  }
  const test = await runCommand(cfg.commands.test, cfg.repoRoot, cfg.commandTimeoutMs);
  if (test.timedOut) {
    return { ok: false, summary: "Test run timed out", details: tail(test.stderr) };
  }
  // Red phase: tests compile but the run fails (assertions / NotImplemented).
  const ok = test.code !== 0;
  return {
    ok,
    summary: ok
      ? "Tests compile and fail — valid red phase (TDD)"
      : "Tests pass but should fail (no implementation = no red phase)",
    details: tail(test.stdout + "\n" + test.stderr),
  };
}

/** Stage 4 (green): all tests must pass. */
export async function validateTestsGreen(cfg: AiToolsConfig): Promise<ValidationResult> {
  const r = await runCommand(cfg.commands.test, cfg.repoRoot, cfg.commandTimeoutMs);
  return {
    ok: r.code === 0 && !r.timedOut,
    summary: r.timedOut ? "Tests timed out" : r.code === 0 ? "All tests pass" : "Tests do not pass",
    details: tail(r.stdout + "\n" + r.stderr),
  };
}
