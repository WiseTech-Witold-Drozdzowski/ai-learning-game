import { sql } from 'drizzle-orm'
import { integer, sqliteTable, text } from 'drizzle-orm/sqlite-core'
// Relative (not the `~` alias) so drizzle-kit, which loads this file outside
// the Nuxt/Nitro context, can resolve it too.
import { APPLICATION_STATUSES, CONTRACT_TYPES } from '../../types/application'

/**
 * Physical storage schema for job applications.
 *
 * This is the ONLY place the column layout lives. Keep it close to the domain
 * model in server/domain/application.ts. Timestamps are stored as Unix epoch
 * integers (portable to Postgres `timestamptz` with a trivial column swap),
 * and `appliedDate` is a plain ISO date string because it is a calendar date
 * with no time component.
 */
export const applications = sqliteTable('applications', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  company: text('company').notNull(),
  position: text('position').notNull(),
  status: text('status', { enum: APPLICATION_STATUSES })
    .notNull()
    .default('SAVED'),
  appliedDate: text('applied_date'),
  salaryRange: text('salary_range'),
  contractType: text('contract_type', { enum: CONTRACT_TYPES }),
  remotePossible: integer('remote_possible', { mode: 'boolean' })
    .notNull()
    .default(false),
  location: text('location'),
  technologies: text('technologies', { mode: 'json' })
    .$type<string[]>()
    .notNull()
    .default(sql`'[]'`),
  jobUrl: text('job_url'),
  notes: text('notes'),
  createdAt: integer('created_at', { mode: 'timestamp' })
    .notNull()
    .default(sql`(unixepoch())`),
  updatedAt: integer('updated_at', { mode: 'timestamp' })
    .notNull()
    .default(sql`(unixepoch())`),
})

export type ApplicationRow = typeof applications.$inferSelect
export type NewApplicationRow = typeof applications.$inferInsert
