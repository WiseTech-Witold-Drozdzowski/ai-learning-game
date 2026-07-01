export type StageId =
  | "analysis"
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

export interface PlannedInterface {
  /** Target file path, e.g. Backend/src/main/java/... */
  file: string;
  purpose: string;
  /** Method signatures with a one-line contract each. */
  methods: string[];
}

export interface PlannedTest {
  file: string;
  cases: string[];
}

/** Steps for which the analysis stage produces a focused, separate task note. */
export type NotedStep = "interface" | "tdd" | "implementation";

/**
 * Compact, task-scoped plan produced once by the analysis stage so downstream
 * stages don't each re-read the full design docs.
 */
export interface TaskPlan {
  /** One paragraph: what this issue requires. */
  scope: string;
  interfaces: PlannedInterface[];
  tests: PlannedTest[];
  implementationNotes: string[];
  /** Only the design points that matter for THIS issue. */
  relevantDesign: string[];
  /** Per-step focused notes; also written to a separate file per step under the run dir. */
  stepNotes?: Partial<Record<NotedStep, string[]>>;
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
  /** Task-scoped plan produced by the analysis stage; consumed by later stages. */
  plan?: TaskPlan;
  totals: UsageDelta;
  message?: string;
}
