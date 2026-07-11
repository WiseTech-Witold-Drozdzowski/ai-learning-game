import { useApplicationService } from '../../services/applicationService'
import { createApplicationSchema } from '../../validation/applicationSchemas'
import { parseWithSchema } from '../../utils/http'

/** POST /api/applications — create a new application. */
export default defineEventHandler(async (event) => {
  const body = parseWithSchema(createApplicationSchema, await readBody(event))
  const created = await useApplicationService().createApplication(body)
  setResponseStatus(event, 201)
  return created
})
