<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import {
  APPLICATION_STATUSES,
  CONTRACT_TYPE_LABELS,
  STATUS_LABELS,
  nextStatus,
  type Application,
  type ApplicationStatus,
} from '~/types/application'

defineProps<{ applications: Application[] }>()

const emit = defineEmits<{
  changeStatus: [id: number, status: ApplicationStatus]
  edit: [application: Application]
  remove: [application: Application]
}>()

// Re-exported for the template.
const contractTypeLabels = CONTRACT_TYPE_LABELS

/** Builds the "change status" dropdown items for one row. */
function statusMenuItems(app: Application): DropdownMenuItem[] {
  return APPLICATION_STATUSES.map((status) => ({
    label: STATUS_LABELS[status],
    icon: status === app.status ? 'i-lucide-check' : undefined,
    onSelect: () => emit('changeStatus', app.id, status),
  }))
}

/** Advances a row to the next step in the pipeline, if there is one. */
function advanceToNextStatus(app: Application) {
  const next = nextStatus(app.status)
  if (next) emit('changeStatus', app.id, next)
}

/**
 * Renders an ISO `YYYY-MM-DD` as a short, locale-aware label. The parts are
 * read explicitly and built as a LOCAL date; using `new Date(iso)` would parse
 * date-only strings as UTC midnight and shift the day west of UTC.
 */
function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!match) return iso
  const [, year, month, day] = match
  const date = new Date(Number(year), Number(month) - 1, Number(day))
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
}
</script>

<template>
  <div class="overflow-x-auto rounded-lg border border-default">
    <table class="w-full min-w-3xl text-sm">
      <thead class="bg-elevated/50 text-left text-xs uppercase tracking-wide text-muted">
        <tr>
          <th class="px-4 py-3 font-medium">Company / Position</th>
          <th class="px-4 py-3 font-medium">Status</th>
          <th class="px-4 py-3 font-medium">Applied</th>
          <th class="px-4 py-3 font-medium">Location</th>
          <th class="px-4 py-3 font-medium">Salary</th>
          <th class="px-4 py-3 font-medium text-right">Actions</th>
        </tr>
      </thead>

      <tbody class="divide-y divide-default">
        <tr v-for="app in applications" :key="app.id" class="hover:bg-elevated/30">
          <td class="px-4 py-3">
            <div class="font-medium text-highlighted">{{ app.company }}</div>
            <div class="text-muted">
              {{ app.position }}
              <ULink
                v-if="app.jobUrl"
                :to="app.jobUrl"
                target="_blank"
                class="ml-1 text-primary"
              >
                ↗
              </ULink>
            </div>
            <div v-if="app.technologies.length" class="mt-1.5 flex flex-wrap gap-1">
              <UBadge
                v-for="tech in app.technologies"
                :key="tech"
                color="neutral"
                variant="soft"
                size="sm"
              >
                {{ tech }}
              </UBadge>
            </div>
          </td>

          <td class="px-4 py-3">
            <div class="flex items-center gap-1.5">
              <!-- Colored badge + quick status change directly from the list. -->
              <UDropdownMenu :items="statusMenuItems(app)" :content="{ align: 'start' }">
                <button type="button" class="flex items-center gap-1 rounded-md hover:opacity-80">
                  <StatusBadge :status="app.status" />
                  <UIcon name="i-lucide-chevron-down" class="size-4 text-muted" />
                </button>
              </UDropdownMenu>
              <!-- One-click advance to the next pipeline stage. -->
              <UTooltip
                v-if="nextStatus(app.status)"
                :text="`Move to ${STATUS_LABELS[nextStatus(app.status)!]}`"
              >
                <UButton
                  icon="i-lucide-arrow-right"
                  color="primary"
                  variant="soft"
                  size="xs"
                  :aria-label="`Move to ${STATUS_LABELS[nextStatus(app.status)!]}`"
                  @click="advanceToNextStatus(app)"
                />
              </UTooltip>
            </div>
          </td>

          <td class="px-4 py-3 whitespace-nowrap text-muted">{{ formatDate(app.appliedDate) }}</td>
          <td class="px-4 py-3 text-muted">
            <div>{{ app.location || '—' }}</div>
            <UBadge
              v-if="app.remotePossible"
              color="success"
              variant="subtle"
              size="sm"
              class="mt-1"
            >
              Remote
            </UBadge>
          </td>
          <td class="px-4 py-3 text-muted">
            <div>
              {{ app.salaryRange || '—' }}
              <span v-if="app.salaryRange" class="text-dimmed">/mo</span>
            </div>
            <div v-if="app.contractType" class="text-xs text-dimmed">
              {{ contractTypeLabels[app.contractType] }}
            </div>
          </td>

          <td class="px-4 py-3">
            <div class="flex justify-end gap-1">
              <UButton
                icon="i-lucide-pencil"
                color="neutral"
                variant="ghost"
                size="sm"
                aria-label="Edit"
                @click="emit('edit', app)"
              />
              <UButton
                icon="i-lucide-trash-2"
                color="error"
                variant="ghost"
                size="sm"
                aria-label="Delete"
                @click="emit('remove', app)"
              />
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
