import { readdirSync, readFileSync } from "node:fs";
import { basename, resolve } from "node:path";
import type { AiToolsConfig } from "./config.js";
import { inRepo } from "./config.js";
import type { TaskSpec } from "./types.js";

export interface IssueFile {
  name: string;
  path: string;
}

/** Find docs/issue*.md (regex, case-insensitive) for task selection. */
export function findIssueFiles(cfg: AiToolsConfig): IssueFile[] {
  const dir = inRepo(cfg, cfg.docsDir);
  let names: string[];
  try {
    names = readdirSync(dir);
  } catch {
    return [];
  }
  const re = /issue.*\.(md|txt)$/i;
  return names
    .filter((n) => re.test(n))
    .sort()
    .map((n) => ({ name: n, path: resolve(dir, n) }));
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
