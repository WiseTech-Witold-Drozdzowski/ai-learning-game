import { useApplicationService } from '../../../services/applicationService'
import { updateStatusSchema } from '../../../validation/applicationSchemas'
import { parseWithSchema, readIdParam, toHttpError } from '../../../utils/http'

/**
 * PATCH /api/applications/:id/status — change only the status.
 * Dedicated endpoint for the quick status dropdown on the list.
 */
export default defineEventHandler(async (event) => {
  const id = readIdParam(event)
  const { status } = parseWithSchema(updateStatusSchema, await readBody(event))
  try {
    return await useApplicationService().changeStatus(id, status)
  } catch (error) {
    toHttpError(error)
  }
})
