<script setup lang="ts">
import {
  APPLICATION_STATUSES,
  STATUS_LABELS,
  type ApplicationStats,
  type ApplicationStatus,
} from '~/types/application'
import { STATUS_DOT } from '~/utils/statusMeta'
import type { StatusFilter } from '~/composables/useApplications'

const { stats } = defineProps<{ stats: ApplicationStats }>()

// Two-way bound active filter so clicking a counter filters the list.
const active = defineModel<StatusFilter>('active', { required: true })

function toggle(status: ApplicationStatus) {
  active.value = active.value === status ? 'ALL' : status
}
</script>

<template>
  <div class="flex flex-wrap items-stretch gap-2">
    <button
      type="button"
      class="rounded-lg border px-4 py-2 text-left transition-colors"
      :class="
        active === 'ALL'
          ? 'border-primary bg-primary/5'
          : 'border-default hover:bg-elevated/50'
      "
      @click="active = 'ALL'"
    >
      <span class="block text-xs text-muted">All</span>
      <span class="block text-xl font-semibold tabular-nums">{{ stats.total }}</span>
    </button>

    <button
      v-for="status in APPLICATION_STATUSES"
      :key="status"
      type="button"
      class="rounded-lg border px-4 py-2 text-left transition-colors"
      :class="
        active === status
          ? 'border-primary bg-primary/5'
          : 'border-default hover:bg-elevated/50'
      "
      @click="toggle(status)"
    >
      <span class="flex items-center gap-1.5 text-xs text-muted">
        <span class="inline-block size-2 rounded-full" :class="STATUS_DOT[status]" />
        {{ STATUS_LABELS[status] }}
      </span>
      <span class="block text-xl font-semibold tabular-nums">
        {{ stats.byStatus[status] ?? 0 }}
      </span>
    </button>
  </div>
</template>
