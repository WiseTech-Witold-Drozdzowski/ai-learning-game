// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  devtools: { enabled: true },

  modules: ['@nuxt/ui'],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    // Server-only. Override with NUXT_DATABASE_URL in the environment.
    // Kept here (not in the DB client) so a SQLite -> Postgres switch is a
    // configuration change, not a code change.
    databaseUrl: './data/tracker.db',
  },

  typescript: {
    typeCheck: false, // run explicitly via `npm run typecheck`
    strict: true,
  },
})
