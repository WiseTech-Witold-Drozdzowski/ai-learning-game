import type {
  Application,
  ApplicationStats,
  ApplicationStatus,
  NewApplicationData,
  UpdateApplicationData,
} from '~/types/application'
import {
  useApplicationRepository,
  type ApplicationRepository,
  type ListApplicationsQuery,
} from '../repositories/applicationRepository'

/**
 * Thrown when an operation targets an application that does not exist.
 * Route handlers map this to an HTTP 404.
 */
export class ApplicationNotFoundError extends Error {
  constructor(public readonly id: number) {
    super(`Application ${id} not found`)
    this.name = 'ApplicationNotFoundError'
  }
}

/**
 * Application (as in "job application") business logic.
 *
 * This is where domain rules live and where future features — e.g. parsing
 * recruitment emails and creating/updating applications from them — will hook
 * in, without any route needing to change. Routes stay thin: parse, delegate,
 * serialize.
 */
export class ApplicationService {
  constructor(private readonly repository: ApplicationRepository) {}

  listApplications(query?: ListApplicationsQuery): Promise<Application[]> {
    return this.repository.list(query)
  }

  async getApplication(id: number): Promise<Application> {
    const application = await this.repository.findById(id)
    if (!application) throw new ApplicationNotFoundError(id)
    return application
  }

  createApplication(data: NewApplicationData): Promise<Application> {
    return this.repository.create(data)
  }

  async updateApplication(
    id: number,
    data: UpdateApplicationData,
  ): Promise<Application> {
    const updated = await this.repository.update(id, data)
    if (!updated) throw new ApplicationNotFoundError(id)
    return updated
  }

  changeStatus(id: number, status: ApplicationStatus): Promise<Application> {
    return this.updateApplication(id, { status })
  }

  async deleteApplication(id: number): Promise<void> {
    const deleted = await this.repository.delete(id)
    if (!deleted) throw new ApplicationNotFoundError(id)
  }

  async getStats(): Promise<ApplicationStats> {
    const byStatus = await this.repository.countByStatus()
    const total = Object.values(byStatus).reduce((sum, n) => sum + n, 0)
    return { total, byStatus }
  }
}

let cachedService: ApplicationService | null = null

/** Returns the shared application service. */
export function useApplicationService(): ApplicationService {
  if (!cachedService) {
    cachedService = new ApplicationService(useApplicationRepository())
  }
  return cachedService
}
