import { readdirSync, readFileSync } from "node:fs";
import { basename, relative, resolve } from "node:path";
import type { AiToolsConfig } from "./config.js";
import { inRepo } from "./config.js";
import type { TaskSpec } from "./types.js";

export interface IssueFile {
  name: string;
  path: string;
}

/** Recursively find *issue*.{md,txt} (case-insensitive) under docsDir for task selection. */
export function findIssueFiles(cfg: AiToolsConfig): IssueFile[] {
  const root = inRepo(cfg, cfg.docsDir);
  const re = /issue.*\.(md|txt)$/i;
  const out: IssueFile[] = [];

  const walk = (dir: string): void => {
    let entries;
    try {
      entries = readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const e of entries) {
      const full = resolve(dir, e.name);
      if (e.isDirectory()) walk(full);
      else if (re.test(e.name)) out.push({ name: relative(root, full), path: full });
    }
  };

  walk(root);
  return out.sort((a, b) => a.name.localeCompare(b.name));
}

function titleFromBody(body: string, fallback: string): string {
  const heading = body.split("\n").find((l) => l.trim().startsWith("#"));
  if (heading) return heading.replace(/^#+\s*/, "").trim();
  const firstLine = body.split("\n").find((l) => l.trim().length > 0);
  return firstLine?.trim().slice(0, 80) ?? fallback;
}

export function taskFromFile(path: string): TaskSpec {
  const body = readFileSync(path, "utf8");
  return { title: titleFromBody(body, basename(path)), body, source: path };
}

export function taskFromText(text: string): TaskSpec {
  return { title: titleFromBody(text, "Zadanie (wklejone)"), body: text, source: "paste" };
}
