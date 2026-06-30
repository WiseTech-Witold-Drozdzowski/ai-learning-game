export type StageId =
  | "interface"
  | "tdd"
  | "review-tdd"
  | "implementation"
  | "review-solution"
  | "human-review";

export type RunStatus = "running" | "paused" | "done" | "failed";

export interface TaskSpec {
  title: string;
  body: string;
  /** "paste" or an absolute file path the task was read from. */
  source: string;
}

export interface UsageDelta {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheCreationTokens: number;
  costUsd: number;
  numTurns: number;
  durationMs: number;
}

export interface AgentResult {
  ok: boolean;
  text: string;
  usage: UsageDelta;
  sessionId?: string;
  error?: string;
}

export interface ValidationResult {
  ok: boolean;
  summary: string;
  details?: string;
  /** Review stages may override where a failure routes back to. */
  goBackTo?: StageId;
}

export interface ReviewVerdict {
  verdict: "pass" | "fail";
  reasons: string[];
  goBackTo?: StageId;
}

export interface HistoryEntry {
  ts: string;
  stage: StageId;
  attempt: number;
  phase: "create" | "validate";
  ok: boolean;
  summary: string;
  usage?: UsageDelta;
}

export interface RunState {
  id: string;
  createdAt: string;
  updatedAt: string;
  task: TaskSpec;
  currentStage: StageId;
  status: RunStatus;
  /** Attempts per stage. */
  attempts: Partial<Record<StageId, number>>;
  totalIterations: number;
  history: HistoryEntry[];
  /** Extra context accumulated from reviews and the human. */
  context: string[];
  totals: UsageDelta;
  message?: string;
}
