import type { H3Event } from 'h3'
import { z } from 'zod'
import { ApplicationNotFoundError } from '../services/applicationService'

/**
 * Shared HTTP glue for route handlers: turning route params into typed values
 * and turning domain/validation errors into the right status codes. Keeping it
 * here means every route stays a thin parse-delegate-serialize handler.
 */

/** Reads and validates the `:id` route param as a positive integer. */
export function readIdParam(event: H3Event): number {
  const raw = getRouterParam(event, 'id')
  const id = Number(raw)
  if (!raw || !Number.isInteger(id) || id <= 0) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid application id' })
  }
  return id
}

/**
 * Validates a value against a Zod schema, throwing a 400 with field-level
 * details on failure. Returns the parsed (and transformed) value on success.
 *
 * Inferring `z.output<S>` (rather than a single `T`) is what lets schemas with
 * transforms — where the input and output types differ — resolve correctly.
 */
export function parseWithSchema<S extends z.ZodTypeAny>(
  schema: S,
  data: unknown,
): z.output<S> {
  const result = schema.safeParse(data)
  if (!result.success) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Validation failed',
      data: { issues: result.error.flatten().fieldErrors },
    })
  }
  return result.data
}

/** Maps a caught domain error to an HTTP error, re-throwing anything else. */
export function toHttpError(error: unknown): never {
  if (error instanceof ApplicationNotFoundError) {
    throw createError({ statusCode: 404, statusMessage: error.message })
  }
  throw error
}
