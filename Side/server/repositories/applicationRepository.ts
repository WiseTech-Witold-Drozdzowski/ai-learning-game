import { asc, count, desc, eq } from 'drizzle-orm'
import type { Database } from '../database/client'
import { useDatabase } from '../database/client'
import { applications, type ApplicationRow } from '../database/schema'
import {
  APPLICATION_STATUSES,
  type Application,
  type ApplicationStatus,
  type NewApplicationData,
  type StatusCounts,
  type UpdateApplicationData,
} from '~/types/application'

/** How the caller wants the list ordered. */
export type ApplicationSortField = 'appliedDate' | 'createdAt' | 'updatedAt'
export type SortDirection = 'asc' | 'desc'

export interface ListApplicationsQuery {
  status?: ApplicationStatus
  sortBy?: ApplicationSortField
  sortDir?: SortDirection
}

/**
 * Storage-agnostic contract for reading and writing applications.
 *
 * Services depend on this interface, never on Drizzle directly. Swapping the
 * database engine means writing a new implementation, not touching callers.
 */
export interface ApplicationRepository {
  list(query?: ListApplicationsQuery): Promise<Application[]>
  findById(id: number): Promise<Application | null>
  create(data: NewApplicationData): Promise<Application>
  update(id: number, data: UpdateApplicationData): Promise<Application | null>
  delete(id: number): Promise<boolean>
  countByStatus(): Promise<StatusCounts>
}

/** Maps a raw database row onto the domain read model. */
function toApplication(row: ApplicationRow): Application {
  return {
    id: row.id,
    company: row.company,
    position: row.position,
    status: row.status,
    appliedDate: row.appliedDate,
    salaryRange: row.salaryRange,
    contractType: row.contractType,
    remotePossible: row.remotePossible,
    location: row.location,
    technologies: row.technologies,
    jobUrl: row.jobUrl,
    notes: row.notes,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  }
}

const SORT_COLUMNS = {
  appliedDate: applications.appliedDate,
  createdAt: applications.createdAt,
  updatedAt: applications.updatedAt,
} as const

class DrizzleApplicationRepository implements ApplicationRepository {
  constructor(private readonly db: Database) {}

  async list(query: ListApplicationsQuery = {}): Promise<Application[]> {
    const sortColumn = SORT_COLUMNS[query.sortBy ?? 'createdAt']
    const orderBy = query.sortDir === 'asc' ? asc(sortColumn) : desc(sortColumn)

    const rows = await this.db
      .select()
      .from(applications)
      .where(query.status ? eq(applications.status, query.status) : undefined)
      .orderBy(orderBy)

    return rows.map(toApplication)
  }

  async findById(id: number): Promise<Application | null> {
    const [row] = await this.db
      .select()
      .from(applications)
      .where(eq(applications.id, id))
      .limit(1)

    return row ? toApplication(row) : null
  }

  async create(data: NewApplicationData): Promise<Application> {
    const now = new Date()
    const [row] = await this.db
      .insert(applications)
      .values({
        company: data.company,
        position: data.position,
        status: data.status ?? 'SAVED',
        appliedDate: data.appliedDate ?? null,
        salaryRange: data.salaryRange ?? null,
        contractType: data.contractType ?? null,
        remotePossible: data.remotePossible ?? false,
        location: data.location ?? null,
        technologies: data.technologies ?? [],
        jobUrl: data.jobUrl ?? null,
        notes: data.notes ?? null,
        createdAt: now,
        updatedAt: now,
      })
      .returning()

    return toApplication(row!)
  }

  async update(
    id: number,
    data: UpdateApplicationData,
  ): Promise<Application | null> {
    const [row] = await this.db
      .update(applications)
      .set({ ...data, updatedAt: new Date() })
      .where(eq(applications.id, id))
      .returning()

    return row ? toApplication(row) : null
  }

  async delete(id: number): Promise<boolean> {
    const result = await this.db
      .delete(applications)
      .where(eq(applications.id, id))
      .returning({ id: applications.id })

    return result.length > 0
  }

  async countByStatus(): Promise<StatusCounts> {
    const rows = await this.db
      .select({ status: applications.status, total: count() })
      .from(applications)
      .groupBy(applications.status)

    const counts = Object.fromEntries(
      APPLICATION_STATUSES.map((status) => [status, 0]),
    ) as StatusCounts

    for (const row of rows) counts[row.status] = row.total
    return counts
  }
}

let cachedRepository: ApplicationRepository | null = null

/** Returns the shared application repository, bound to the active database. */
export function useApplicationRepository(): ApplicationRepository {
  if (!cachedRepository) {
    cachedRepository = new DrizzleApplicationRepository(useDatabase())
  }
  return cachedRepository
}
