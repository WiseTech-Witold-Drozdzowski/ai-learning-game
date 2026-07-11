import { useApplicationService } from '../../services/applicationService'
import { listApplicationsQuerySchema } from '../../validation/applicationSchemas'
import { parseWithSchema } from '../../utils/http'

/** GET /api/applications — list, optionally filtered by status and sorted. */
export default defineEventHandler(async (event) => {
  const query = parseWithSchema(listApplicationsQuerySchema, getQuery(event))
  return useApplicationService().listApplications(query)
})
