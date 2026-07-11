import { useApplicationService } from '../../services/applicationService'

/** GET /api/applications/stats — total and per-status counts for the dashboard. */
export default defineEventHandler(async () => {
  return useApplicationService().getStats()
})
