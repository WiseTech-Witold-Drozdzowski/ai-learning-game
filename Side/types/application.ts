/**
 * Framework-agnostic domain model for a job application.
 *
 * Nothing here imports Drizzle, SQLite, or Nuxt. The service and route layers
 * speak in these types; the repository is responsible for mapping them to and
 * from whatever storage engine is configured. This is the seam that keeps a
 * future SQLite -> Postgres migration a matter of swapping the repository
 * implementation rather than rewriting business logic.
 */

export const APPLICATION_STATUSES = [
  'SAVED',
  'APPLIED',
  'PHONE_SCREEN',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
] as const

export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]

/** Kind of employment contract offered. */
export const CONTRACT_TYPES = ['EMPLOYMENT', 'B2B'] as const
export type ContractType = (typeof CONTRACT_TYPES)[number]

export const CONTRACT_TYPE_LABELS: Record<ContractType, string> = {
  EMPLOYMENT: 'Employment (UoP)',
  B2B: 'B2B',
}

/** A job application as understood by the whole application (read model). */
export interface Application {
  id: number
  company: string
  position: string
  status: ApplicationStatus
  /** Calendar date the application was submitted, as ISO `YYYY-MM-DD`. */
  appliedDate: string | null
  /** Monthly salary, free text (e.g. "15 000 PLN / month"). */
  salaryRange: string | null
  contractType: ContractType | null
  /** Whether the role can be done fully remotely. */
  remotePossible: boolean
  location: string | null
  /** Tech stack tags (e.g. ["TypeScript", "Vue", "PostgreSQL"]). */
  technologies: string[]
  jobUrl: string | null
  notes: string | null
  createdAt: Date
  updatedAt: Date
}

/** Fields a caller may provide when creating an application. */
export interface NewApplicationData {
  company: string
  position: string
  status?: ApplicationStatus
  appliedDate?: string | null
  salaryRange?: string | null
  contractType?: ContractType | null
  remotePossible?: boolean
  location?: string | null
  technologies?: string[]
  jobUrl?: string | null
  notes?: string | null
}

/** Fields a caller may change on an existing application (all optional). */
export type UpdateApplicationData = Partial<NewApplicationData>

/** Number of applications currently sitting in each status. */
export type StatusCounts = Record<ApplicationStatus, number>

export interface ApplicationStats {
  total: number
  byStatus: StatusCounts
}

/** Human-readable label for a status, for UI display. */
export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  PHONE_SCREEN: 'Phone screen',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

/**
 * The linear "happy path" a candidate moves through. REJECTED and WITHDRAWN are
 * off-pipeline outcomes and intentionally excluded, so they have no next step.
 */
export const STATUS_FLOW: ApplicationStatus[] = [
  'SAVED',
  'APPLIED',
  'PHONE_SCREEN',
  'INTERVIEW',
  'OFFER',
]

/** The next status in the pipeline, or null if there is none. */
export function nextStatus(status: ApplicationStatus): ApplicationStatus | null {
  const index = STATUS_FLOW.indexOf(status)
  if (index === -1 || index === STATUS_FLOW.length - 1) return null
  return STATUS_FLOW[index + 1] ?? null
}
