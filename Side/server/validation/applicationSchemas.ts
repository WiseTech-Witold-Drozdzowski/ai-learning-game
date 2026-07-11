import { z } from 'zod'
import {
  APPLICATION_STATUSES,
  CONTRACT_TYPES,
  type ContractType,
} from '~/types/application'

/**
 * Server-side input validation. Route handlers parse untrusted request data
 * through these schemas before it ever reaches the service, so the service and
 * repository can assume well-formed values.
 *
 * Optional text fields normalize empty/whitespace-only strings (which is what
 * an untouched form control sends) to `null`, so storage stays clean.
 */

/** Optional free text: trimmed, empty -> null. */
const optionalText = z
  .string()
  .trim()
  .optional()
  .nullable()
  .transform((value) => (value && value.length > 0 ? value : null))

const requiredText = (label: string) =>
  z
    .string({ required_error: `${label} is required` })
    .trim()
    .min(1, `${label} is required`)
    .max(200)

const statusSchema = z.enum(APPLICATION_STATUSES)

/** True only for a real ISO calendar date — rejects e.g. `2026-02-31`. */
function isIsoCalendarDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

/** True for an absolute http(s) URL (blocks `javascript:`, `mailto:`, etc.). */
function isHttpUrl(value: string): boolean {
  try {
    const { protocol } = new URL(value)
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}

/** ISO calendar date `YYYY-MM-DD`, or null. */
const appliedDateSchema = optionalText.refine(
  (value) => value === null || isIsoCalendarDate(value),
  'Applied date must be a valid date (YYYY-MM-DD)',
)

/** A valid absolute http(s) URL, or null. */
const jobUrlSchema = optionalText
  .refine((value) => value === null || isHttpUrl(value), 'Job URL must be a valid http(s) URL')
  .refine((value) => value === null || value.length <= 2000, 'Job URL is too long')

/** One of the contract types, or null (empty selection maps to null). */
const contractTypeSchema = z
  .union([z.enum(CONTRACT_TYPES), z.literal(''), z.null()])
  .optional()
  .transform((value): ContractType | null => (value ? value : null))

/** Tech tags: trimmed, empties dropped, de-duplicated. */
const technologiesSchema = z
  .array(z.string().max(50))
  .max(50)
  .optional()
  .transform((tags) =>
    tags === undefined
      ? undefined
      : Array.from(new Set(tags.map((tag) => tag.trim()).filter((tag) => tag.length > 0))),
  )

export const createApplicationSchema = z.object({
  company: requiredText('Company'),
  position: requiredText('Position'),
  status: statusSchema.optional(),
  appliedDate: appliedDateSchema,
  salaryRange: optionalText,
  contractType: contractTypeSchema,
  remotePossible: z.boolean().optional(),
  location: optionalText,
  technologies: technologiesSchema,
  jobUrl: jobUrlSchema,
  notes: optionalText,
})

/** All fields optional; at least one must be present. */
export const updateApplicationSchema = createApplicationSchema
  .partial()
  .refine((data) => Object.keys(data).length > 0, {
    message: 'At least one field must be provided',
  })

/** Body for the quick status-only change from the list. */
export const updateStatusSchema = z.object({ status: statusSchema })

export const listApplicationsQuerySchema = z.object({
  status: statusSchema.optional(),
  sortBy: z.enum(['appliedDate', 'createdAt', 'updatedAt']).optional(),
  sortDir: z.enum(['asc', 'desc']).optional(),
})

export type CreateApplicationInput = z.infer<typeof createApplicationSchema>
export type UpdateApplicationInput = z.infer<typeof updateApplicationSchema>
