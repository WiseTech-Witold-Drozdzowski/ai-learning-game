<script setup lang="ts">
import { STATUS_LABELS, type ApplicationStatus } from '~/types/application'
import { STATUS_COLORS } from '~/utils/statusMeta'

const { status } = defineProps<{ status: ApplicationStatus }>()
</script>

<template>
  <UBadge :color="STATUS_COLORS[status]" variant="subtle" size="sm">
    {{ STATUS_LABELS[status] }}
  </UBadge>
</template>
