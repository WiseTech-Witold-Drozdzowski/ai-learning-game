import { useApplicationService } from '../../services/applicationService'
import { readIdParam, toHttpError } from '../../utils/http'

/** GET /api/applications/:id — fetch a single application. */
export default defineEventHandler(async (event) => {
  const id = readIdParam(event)
  try {
    return await useApplicationService().getApplication(id)
  } catch (error) {
    toHttpError(error)
  }
})
