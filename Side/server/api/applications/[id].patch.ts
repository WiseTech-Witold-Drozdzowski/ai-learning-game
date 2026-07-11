import { useApplicationService } from '../../services/applicationService'
import { updateApplicationSchema } from '../../validation/applicationSchemas'
import { parseWithSchema, readIdParam, toHttpError } from '../../utils/http'

/** PATCH /api/applications/:id — update any subset of fields. */
export default defineEventHandler(async (event) => {
  const id = readIdParam(event)
  const body = parseWithSchema(updateApplicationSchema, await readBody(event))
  try {
    return await useApplicationService().updateApplication(id, body)
  } catch (error) {
    toHttpError(error)
  }
})
