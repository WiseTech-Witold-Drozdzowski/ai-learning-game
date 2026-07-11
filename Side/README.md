# Job Tracker

A small, single-user, **locally-run** app for tracking job applications. Add
entries by hand, change their status by hand, see how many sit in each stage.

Built as a Nuxt 3 full-stack app: Vue 3 (Composition API) on the front, Nuxt
server routes on the back, SQLite via Drizzle ORM for storage.

## Stack

| Concern        | Choice                                  |
| -------------- | --------------------------------------- |
| Framework      | Nuxt 3 + TypeScript                     |
| UI components  | Nuxt UI (v3) + Tailwind CSS (v4)        |
| Backend        | Nuxt server routes (`/server/api`)      |
| Database       | SQLite (single file in `./data`)        |
| ORM / access   | Drizzle ORM + `better-sqlite3`          |
| Validation     | Zod (server-side)                       |

## Getting started

Requirements: Node.js 20+ and npm.

```bash
# 1. Install dependencies
npm install

# 2. Create the SQLite database and apply migrations
#    (generates SQL from the schema, then runs it against ./data/tracker.db)
npm run db:init

# 3. Start the dev server
npm run dev
```

Then open http://localhost:3000.

The database lives in `./data/tracker.db` and is **git-ignored** — it never
leaves your machine.

## Scripts

| Command               | What it does                                              |
| --------------------- | -------------------------------------------------------- |
| `npm run dev`         | Start the dev server (hot reload)                        |
| `npm run build`       | Production build                                         |
| `npm run preview`     | Preview the production build                             |
| `npm run db:generate` | Generate a new migration from schema changes             |
| `npm run db:migrate`  | Apply pending migrations to the database                 |
| `npm run db:init`     | `db:generate` + `db:migrate` (first-time setup)          |
| `npm run db:studio`   | Open Drizzle Studio to browse the data                   |
| `npm run typecheck`   | Type-check the whole project                             |

After you change `server/database/schema.ts`, run `npm run db:generate` to
create a migration, then `npm run db:migrate` to apply it.

## Features

- **List** of all applications with a colored status badge per row, plus tech
  tags, a "Remote" badge, and monthly salary / contract type.
- **Add / edit** through a modal form.
- **Quick status change** from the list via a per-row dropdown, plus a
  one-click **"advance to next status"** button (SAVED → APPLIED → … → OFFER).
- **Delete** with a confirmation dialog.
- **Filter** by status (click a counter) and **sort** by date.
- **Dashboard** counters: how many applications sit in each status.

Form conveniences:

- **Position** is pre-filled from your most recent application.
- **Applied date** defaults to today.
- **Location** offers known cities as suggestions but accepts any new city.
- **Technologies** is a tag input (type + Enter), with suggestions from your
  existing entries.
- **Monthly salary**, **contract type** (Employment / B2B), and a **full-remote**
  toggle.

## Data model

Table `applications`:

| Column        | Type      | Notes                                              |
| ------------- | --------- | -------------------------------------------------- |
| `id`          | integer   | Primary key                                        |
| `company`     | text      | Required                                           |
| `position`    | text      | Required                                           |
| `status`      | enum      | `SAVED`, `APPLIED`, `PHONE_SCREEN`, `INTERVIEW`, `OFFER`, `REJECTED`, `WITHDRAWN` |
| `appliedDate` | text      | Optional, ISO `YYYY-MM-DD` (validated as a real date) |
| `salaryRange` | text      | Optional, interpreted as a monthly figure          |
| `contractType`| enum      | Optional: `EMPLOYMENT`, `B2B`                      |
| `remotePossible` | boolean | Whether the role can be fully remote (default false) |
| `location`    | text      | Optional                                           |
| `technologies`| json      | String array of tech tags (default `[]`)          |
| `jobUrl`      | text      | Optional, validated as an http(s) URL              |
| `notes`       | text      | Optional                                           |
| `createdAt`   | timestamp | Set automatically                                  |
| `updatedAt`   | timestamp | Updated automatically on every change              |

## API

Base path `/api/applications`.

| Method + path                       | Purpose                              |
| ----------------------------------- | ------------------------------------ |
| `GET /api/applications`             | List (query: `status`, `sortBy`, `sortDir`) |
| `GET /api/applications/stats`       | Totals + per-status counts           |
| `GET /api/applications/:id`         | Fetch one                            |
| `POST /api/applications`            | Create                               |
| `PATCH /api/applications/:id`       | Update any subset of fields          |
| `PATCH /api/applications/:id/status`| Change only the status               |
| `DELETE /api/applications/:id`      | Delete                               |

All request bodies are validated server-side with Zod; failures return
`400` with field-level details.

## Architecture

The code is layered so that the two planned extensions — **reading a mailbox
and parsing recruitment emails**, and **migrating SQLite → Postgres** — can be
added by changing one layer, not rewriting the app.

```
types/application.ts                  Framework-agnostic domain model
                                      (shared by client and server)

server/
├─ api/applications/                  HTTP routes — thin: parse, delegate, serialize
├─ services/applicationService.ts     Business logic. Future email-parsing logic
│                                      hooks in HERE, reusing the same service.
├─ repositories/applicationRepository Data access behind an interface. The only
│                                      place that knows about Drizzle queries.
├─ database/
│  ├─ schema.ts                        Drizzle table definition
│  └─ client.ts                        The ONLY module bound to SQLite/better-sqlite3
├─ validation/applicationSchemas.ts    Zod input schemas
└─ utils/http.ts                       Route glue (id parsing, error mapping)

components/                           Presentational Vue components
composables/useApplications.ts        Client-side state + API calls
pages/index.vue                       The single screen
```

**Swapping to Postgres** is a configuration change, not a logic change:

1. Change `dialect` and `dbCredentials` in `drizzle.config.ts`.
2. Swap the driver in `server/database/client.ts`
   (`drizzle-orm/better-sqlite3` → `drizzle-orm/node-postgres`).
3. Adjust the two column helpers in `server/database/schema.ts` if needed.

Routes, services, validation, and the entire frontend are untouched, because
they depend on the `ApplicationRepository` interface and the domain model —
never on Drizzle or SQLite directly.

## Project layout

```
.
├─ components/           Vue components (table, form, badges, stats)
├─ composables/          useApplications() — client data layer
├─ pages/                index.vue
├─ server/               Backend (routes, services, repository, db, validation)
├─ types/                Shared domain model
├─ data/                 SQLite database file (git-ignored)
├─ assets/css/main.css   Tailwind + Nuxt UI entry
├─ drizzle.config.ts
└─ nuxt.config.ts
```
