import { defineConfig } from 'drizzle-kit'

// Drizzle Kit only handles migrations/introspection. Switching to Postgres
// later means changing `dialect` + `dbCredentials` here and the driver in
// server/database/client.ts — the query/service/route code stays untouched.
export default defineConfig({
  dialect: 'sqlite',
  schema: './server/database/schema.ts',
  out: './server/database/migrations',
  dbCredentials: {
    url: process.env.NUXT_DATABASE_URL ?? './data/tracker.db',
  },
})
