import { runCommand } from "./validators.js";

/** A TODO/FIXME marker the human left in the working tree for the next iteration. */
export interface TodoNote {
  file: string;
  line: number;
  text: string;
}

/** Shell-quote a path for safe interpolation into a `git` command. */
function quote(p: string): string {
  return `'${p.replace(/'/g, "'\\''")}'`;
}

/**
 * Stage the agent's work before pausing for human review, so any `// TODO` the
 * human then adds shows up as an unstaged diff (working tree vs index).
 * Best-effort: a git failure never aborts the run.
 */
export async function stageChanges(
  cwd: string,
  paths: string[],
  timeoutMs: number,
): Promise<{ ok: boolean; error?: string }> {
  const spec = paths.length ? `-- ${paths.map(quote).join(" ")}` : "";
  const r = await runCommand(`git add -A ${spec}`, cwd, timeoutMs);
  return { ok: r.code === 0 && !r.timedOut, error: r.code === 0 ? undefined : (r.stderr || r.stdout).trim() };
}

/**
 * Extract TODO/FIXME markers the human added since the last staging, by parsing
 * the unstaged diff (`git diff -U0`) for added lines matching the markers.
 */
export async function collectTodos(
  cwd: string,
  paths: string[],
  markers: string[],
  timeoutMs: number,
): Promise<TodoNote[]> {
  const spec = paths.length ? `-- ${paths.map(quote).join(" ")}` : "";
  const r = await runCommand(`git diff -U0 ${spec}`, cwd, timeoutMs);
  if (r.timedOut || r.code !== 0) return [];
  return parseTodos(r.stdout, markers);
}

const HUNK_RE = /^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@/;

/** Parse a unified diff (-U0) and return added lines that contain a marker. */
export function parseTodos(diff: string, markers: string[]): TodoNote[] {
  const markerRe = new RegExp(`\\b(?:${markers.map(escapeRe).join("|")})\\b`);
  const out: TodoNote[] = [];
  let file = "";
  let newLine = 0;

  for (const raw of diff.split("\n")) {
    if (raw.startsWith("+++ ")) {
      // "+++ b/Backend/…" — strip the "b/" prefix; "/dev/null" for deletions.
      const p = raw.slice(4).trim();
      file = p === "/dev/null" ? "" : p.replace(/^b\//, "");
      continue;
    }
    if (raw.startsWith("@@")) {
      const m = HUNK_RE.exec(raw);
      newLine = m ? Number(m[1]) : 0;
      continue;
    }
    if (raw.startsWith("+") && !raw.startsWith("+++")) {
      const content = raw.slice(1);
      if (file && markerRe.test(content)) {
        out.push({ file, line: newLine, text: content.trim() });
      }
      newLine += 1;
    }
    // context/removed lines don't occur with -U0, so no newLine tracking needed for them.
  }
  return out;
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
