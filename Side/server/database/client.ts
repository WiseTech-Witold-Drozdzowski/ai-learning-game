import { mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import Database from 'better-sqlite3'
import { drizzle } from 'drizzle-orm/better-sqlite3'
import { useRuntimeConfig } from '#imports'
import * as schema from './schema'

/**
 * Single, lazily-created database connection for the process.
 *
 * This module is the one place bound to SQLite / better-sqlite3. Every other
 * server module goes through the repository, which receives this `db` handle.
 * To move to Postgres: swap this file for the `drizzle-orm/node-postgres`
 * driver and update drizzle.config.ts — no caller changes required.
 */

export type Database = ReturnType<typeof drizzle<typeof schema>>

let cachedDb: Database | null = null

function resolveDatabaseFilePath(): string {
  return useRuntimeConfig().databaseUrl
}

function ensureParentDirectoryExists(filePath: string): void {
  mkdirSync(dirname(filePath), { recursive: true })
}

/** Returns the shared Drizzle database instance, creating it on first use. */
export function useDatabase(): Database {
  if (cachedDb) return cachedDb

  const filePath = resolveDatabaseFilePath()
  ensureParentDirectoryExists(filePath)

  const sqlite = new Database(filePath)
  sqlite.pragma('journal_mode = WAL')
  sqlite.pragma('foreign_keys = ON')

  cachedDb = drizzle(sqlite, { schema })
  return cachedDb
}
