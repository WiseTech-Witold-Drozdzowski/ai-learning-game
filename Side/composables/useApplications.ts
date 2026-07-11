import type {
  Application,
  ApplicationStats,
  ApplicationStatus,
} from '~/types/application'
import type {
  CreateApplicationInput,
  UpdateApplicationInput,
} from '~/server/validation/applicationSchemas'

export type StatusFilter = ApplicationStatus | 'ALL'
export type SortField = 'createdAt' | 'appliedDate' | 'updatedAt'
export type SortDir = 'asc' | 'desc'

/**
 * Single source of client-side state and actions for job applications.
 *
 * Wraps the /api/applications endpoints, keeps the filtered list and the
 * dashboard stats in sync, and re-fetches both after any mutation so the UI
 * never drifts from the server. Components stay presentational.
 */
export function useApplications() {
  const filters = reactive({
    status: 'ALL' as StatusFilter,
    sortBy: 'createdAt' as SortField,
    sortDir: 'desc' as SortDir,
  })

  // Reactive query — useFetch re-runs whenever these values change.
  const listQuery = computed(() => ({
    ...(filters.status !== 'ALL' ? { status: filters.status } : {}),
    sortBy: filters.sortBy,
    sortDir: filters.sortDir,
  }))

  const {
    data: applications,
    pending,
    error,
    refresh: refreshList,
  } = useFetch<Application[]>('/api/applications', {
    query: listQuery,
    default: () => [],
  })

  const { data: stats, refresh: refreshStats } = useFetch<ApplicationStats>(
    '/api/applications/stats',
    { default: () => ({ total: 0, byStatus: {} as ApplicationStats['byStatus'] }) },
  )

  async function reload() {
    await Promise.all([refreshList(), refreshStats()])
  }

  async function createApplication(input: CreateApplicationInput) {
    await $fetch('/api/applications', { method: 'POST', body: input })
    await reload()
  }

  async function updateApplication(id: number, input: UpdateApplicationInput) {
    await $fetch(`/api/applications/${id}`, { method: 'PATCH', body: input })
    await reload()
  }

  async function changeStatus(id: number, status: ApplicationStatus) {
    await $fetch(`/api/applications/${id}/status`, {
      method: 'PATCH',
      body: { status },
    })
    await reload()
  }

  async function deleteApplication(id: number) {
    await $fetch(`/api/applications/${id}`, { method: 'DELETE' })
    await reload()
  }

  return {
    applications,
    stats,
    pending,
    error,
    filters,
    createApplication,
    updateApplication,
    changeStatus,
    deleteApplication,
  }
}
