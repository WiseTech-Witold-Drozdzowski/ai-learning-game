<script setup lang="ts">
import type { FormSubmitEvent } from '@nuxt/ui'
import {
  CONTRACT_TYPE_LABELS,
  type Application,
  type ContractType,
} from '~/types/application'
import {
  createApplicationSchema,
  type CreateApplicationInput,
} from '~/server/validation/applicationSchemas'
import { statusSelectItems } from '~/utils/statusMeta'

const {
  initial = null,
  submitting = false,
  defaultPosition = '',
  citySuggestions = [],
  technologySuggestions = [],
} = defineProps<{
  /** Existing application when editing; null when creating. */
  initial?: Application | null
  submitting?: boolean
  /** Pre-filled Position for a new application (e.g. from the last entry). */
  defaultPosition?: string
  /** Known cities offered as location suggestions. */
  citySuggestions?: string[]
  /** Known technologies offered as tag suggestions. */
  technologySuggestions?: string[]
}>()

const emit = defineEmits<{
  submit: [payload: CreateApplicationInput]
  cancel: []
}>()

const isEditing = computed(() => initial !== null)

/** Local calendar date as ISO `YYYY-MM-DD` (not UTC, so "today" is correct). */
function todayIso(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

// Local editable copy. Nulls become '' so the inputs are controlled/empty.
// New applications get convenience defaults (last position, today's date).
const state = reactive({
  company: initial?.company ?? '',
  position: initial?.position ?? defaultPosition,
  status: initial?.status ?? 'SAVED',
  appliedDate: initial ? (initial.appliedDate ?? '') : todayIso(),
  salaryRange: initial?.salaryRange ?? '',
  contractType: (initial?.contractType ?? '') as '' | ContractType,
  remotePossible: initial?.remotePossible ?? false,
  location: initial?.location ?? '',
  technologies: [...(initial?.technologies ?? [])] as string[],
  jobUrl: initial?.jobUrl ?? '',
  notes: initial?.notes ?? '',
})

const statusItems = statusSelectItems()

// No empty-string item: the underlying Select reserves '' for "no selection"
// (shown via the placeholder), and an item with value '' is disallowed.
const contractItems = [
  { label: CONTRACT_TYPE_LABELS.EMPLOYMENT, value: 'EMPLOYMENT' },
  { label: CONTRACT_TYPE_LABELS.B2B, value: 'B2B' },
]

// --- Technologies tag input ------------------------------------------------
const techDraft = ref('')

function addTechnology() {
  const value = techDraft.value.trim()
  if (value && !state.technologies.includes(value)) state.technologies.push(value)
  techDraft.value = ''
}

function removeTechnology(tech: string) {
  state.technologies = state.technologies.filter((item) => item !== tech)
}

function onSubmit(event: FormSubmitEvent<CreateApplicationInput>) {
  // event.data is validated by the shared Zod schema and normalized
  // (empty strings -> null, tags trimmed/deduped), so callers get clean data.
  emit('submit', event.data)
}
</script>

<template>
  <UForm :schema="createApplicationSchema" :state="state" class="space-y-4" @submit="onSubmit">
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <UFormField label="Company" name="company" required>
        <UInput v-model="state.company" placeholder="Acme Inc." class="w-full" />
      </UFormField>

      <UFormField label="Position" name="position" required>
        <UInput v-model="state.position" placeholder="Software Engineer" class="w-full" />
      </UFormField>

      <UFormField label="Status" name="status">
        <USelect v-model="state.status" :items="statusItems" value-key="value" class="w-full" />
      </UFormField>

      <UFormField label="Applied date" name="appliedDate">
        <UInput v-model="state.appliedDate" type="date" class="w-full" />
      </UFormField>

      <UFormField label="Monthly salary" name="salaryRange">
        <UInput v-model="state.salaryRange" placeholder="15 000 PLN / month" class="w-full" />
      </UFormField>

      <UFormField label="Contract type" name="contractType">
        <div class="flex items-center gap-1">
          <USelect
            v-model="state.contractType"
            :items="contractItems"
            value-key="value"
            placeholder="Not specified"
            class="w-full"
          />
          <UButton
            v-if="state.contractType"
            icon="i-lucide-x"
            color="neutral"
            variant="ghost"
            size="xs"
            aria-label="Clear contract type"
            @click="state.contractType = ''"
          />
        </div>
      </UFormField>

      <UFormField label="Location" name="location">
        <UInput
          v-model="state.location"
          placeholder="Warsaw"
          list="city-suggestions"
          class="w-full"
        />
        <datalist id="city-suggestions">
          <option v-for="city in citySuggestions" :key="city" :value="city" />
        </datalist>
      </UFormField>

      <UFormField label="Remote" name="remotePossible">
        <USwitch v-model="state.remotePossible" label="Full remote possible" />
      </UFormField>
    </div>

    <UFormField label="Technologies" name="technologies">
      <div class="flex gap-2">
        <UInput
          v-model="techDraft"
          placeholder="Add a technology and press Enter"
          list="tech-suggestions"
          class="flex-1"
          @keydown.enter.prevent="addTechnology"
        />
        <UButton color="neutral" variant="subtle" icon="i-lucide-plus" @click="addTechnology">
          Add
        </UButton>
        <datalist id="tech-suggestions">
          <option v-for="tech in technologySuggestions" :key="tech" :value="tech" />
        </datalist>
      </div>
      <div v-if="state.technologies.length" class="mt-2 flex flex-wrap gap-1.5">
        <UBadge
          v-for="tech in state.technologies"
          :key="tech"
          color="neutral"
          variant="subtle"
          class="gap-1"
        >
          {{ tech }}
          <button
            type="button"
            class="hover:text-error"
            :aria-label="`Remove ${tech}`"
            @click="removeTechnology(tech)"
          >
            <UIcon name="i-lucide-x" class="size-3" />
          </button>
        </UBadge>
      </div>
    </UFormField>

    <UFormField label="Job URL" name="jobUrl">
      <UInput v-model="state.jobUrl" type="url" placeholder="https://…" class="w-full" />
    </UFormField>

    <UFormField label="Notes" name="notes">
      <UTextarea v-model="state.notes" :rows="4" autoresize class="w-full" />
    </UFormField>

    <div class="flex justify-end gap-2 pt-2">
      <UButton color="neutral" variant="ghost" :disabled="submitting" @click="emit('cancel')">
        Cancel
      </UButton>
      <UButton type="submit" :loading="submitting">
        {{ isEditing ? 'Save changes' : 'Add application' }}
      </UButton>
    </div>
  </UForm>
</template>
