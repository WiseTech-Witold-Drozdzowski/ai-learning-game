import { useApplicationService } from '../../services/applicationService'
import { readIdParam, toHttpError } from '../../utils/http'

/** DELETE /api/applications/:id — remove an application. */
export default defineEventHandler(async (event) => {
  const id = readIdParam(event)
  try {
    await useApplicationService().deleteApplication(id)
    setResponseStatus(event, 204)
    return null
  } catch (error) {
    toHttpError(error)
  }
})
