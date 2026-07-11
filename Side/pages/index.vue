<script setup lang="ts">
import type { Application } from '~/types/application'
import type { CreateApplicationInput } from '~/server/validation/applicationSchemas'

const {
  applications,
  stats,
  pending,
  error,
  filters,
  createApplication,
  updateApplication,
  changeStatus,
  deleteApplication,
} = useApplications()

const toast = useToast()

// --- Form suggestions (derived from existing applications) -----------------
const DEFAULT_CITIES = ['Remote', 'Warsaw', 'Kraków', 'Wrocław', 'Poznań', 'Gdańsk', 'Łódź']

/** Position of the most recently created application, to pre-fill new entries. */
const lastPosition = computed(() => {
  const list = applications.value ?? []
  if (!list.length) return ''
  return [...list].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )[0]!.position
})

/** Distinct, non-empty, sorted values, seeded with any extra defaults. */
function distinctSorted(values: (string | null)[], extra: string[] = []): string[] {
  const set = new Set<string>(extra)
  for (const value of values) if (value && value.trim()) set.add(value.trim())
  return [...set].sort((a, b) => a.localeCompare(b))
}

const citySuggestions = computed(() =>
  distinctSorted(
    (applications.value ?? []).map((app) => app.location),
    DEFAULT_CITIES,
  ),
)

const technologySuggestions = computed(() =>
  distinctSorted((applications.value ?? []).flatMap((app) => app.technologies)),
)

// --- Create / edit modal ---------------------------------------------------
const formModalOpen = ref(false)
const editingApplication = ref<Application | null>(null)
const submitting = ref(false)

function openCreate() {
  editingApplication.value = null
  formModalOpen.value = true
}

function openEdit(application: Application) {
  editingApplication.value = application
  formModalOpen.value = true
}

async function onFormSubmit(payload: CreateApplicationInput) {
  submitting.value = true
  try {
    if (editingApplication.value) {
      await updateApplication(editingApplication.value.id, payload)
      toast.add({ title: 'Application updated', color: 'success' })
    } else {
      await createApplication(payload)
      toast.add({ title: 'Application added', color: 'success' })
    }
    formModalOpen.value = false
  } catch {
    toast.add({ title: 'Could not save application', color: 'error' })
  } finally {
    submitting.value = false
  }
}

// --- Quick status change ---------------------------------------------------
async function onChangeStatus(id: number, status: Application['status']) {
  try {
    await changeStatus(id, status)
  } catch {
    toast.add({ title: 'Could not change status', color: 'error' })
  }
}

// --- Delete with confirmation ----------------------------------------------
const deleteTarget = ref<Application | null>(null)
const deleting = ref(false)

async function performDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteApplication(deleteTarget.value.id)
    toast.add({ title: 'Application deleted', color: 'success' })
    deleteTarget.value = null
  } catch {
    toast.add({ title: 'Could not delete application', color: 'error' })
  } finally {
    deleting.value = false
  }
}

// --- Sorting ---------------------------------------------------------------
const SORT_OPTIONS = [
  { label: 'Newest first', value: 'createdAt:desc' },
  { label: 'Oldest first', value: 'createdAt:asc' },
  { label: 'Applied date (newest)', value: 'appliedDate:desc' },
  { label: 'Recently updated', value: 'updatedAt:desc' },
]

const sortValue = computed({
  get: () => `${filters.sortBy}:${filters.sortDir}`,
  set: (value: string) => {
    const [sortBy, sortDir] = value.split(':') as [
      typeof filters.sortBy,
      typeof filters.sortDir,
    ]
    filters.sortBy = sortBy
    filters.sortDir = sortDir
  },
})

const hasApplications = computed(() => (applications.value?.length ?? 0) > 0)
</script>

<template>
  <UContainer class="py-8">
    <!-- Header -->
    <header class="mb-8 flex items-end justify-between gap-4">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight">Job Tracker</h1>
        <p class="text-sm text-muted">Track your job applications in one place.</p>
      </div>
      <UButton icon="i-lucide-plus" @click="openCreate">Add application</UButton>
    </header>

    <!-- Dashboard counters (also act as status filter) -->
    <section class="mb-6">
      <StatsBar v-if="stats" v-model:active="filters.status" :stats="stats" />
    </section>

    <!-- Toolbar: sort -->
    <div class="mb-4 flex items-center justify-end gap-3">
      <USelect
        v-model="sortValue"
        :items="SORT_OPTIONS"
        value-key="value"
        size="sm"
        icon="i-lucide-arrow-up-down"
        class="w-56"
      />
    </div>

    <!-- List states -->
    <UAlert
      v-if="error"
      color="error"
      variant="subtle"
      title="Failed to load applications"
      :description="String(error)"
    />

    <div v-else-if="pending" class="space-y-2">
      <USkeleton v-for="n in 4" :key="n" class="h-14 w-full" />
    </div>

    <ApplicationsTable
      v-else-if="hasApplications"
      :applications="applications ?? []"
      @change-status="onChangeStatus"
      @edit="openEdit"
      @remove="(app) => (deleteTarget = app)"
    />

    <div
      v-else
      class="rounded-lg border border-dashed border-default py-16 text-center"
    >
      <UIcon name="i-lucide-inbox" class="mx-auto mb-3 size-8 text-dimmed" />
      <p class="text-sm text-muted">
        {{
          filters.status === 'ALL'
            ? 'No applications yet. Add your first one.'
            : 'No applications with this status.'
        }}
      </p>
      <UButton class="mt-4" variant="soft" icon="i-lucide-plus" @click="openCreate">
        Add application
      </UButton>
    </div>

    <!-- Create / edit modal -->
    <UModal
      v-model:open="formModalOpen"
      :title="editingApplication ? 'Edit application' : 'Add application'"
      :ui="{ content: 'max-w-2xl' }"
    >
      <template #body>
        <ApplicationForm
          :key="editingApplication?.id ?? 'new'"
          :initial="editingApplication"
          :submitting="submitting"
          :default-position="lastPosition"
          :city-suggestions="citySuggestions"
          :technology-suggestions="technologySuggestions"
          @submit="onFormSubmit"
          @cancel="formModalOpen = false"
        />
      </template>
    </UModal>

    <!-- Delete confirmation -->
    <UModal
      :open="deleteTarget !== null"
      title="Delete application"
      @update:open="(value) => { if (!value) deleteTarget = null }"
    >
      <template #body>
        <p class="text-sm text-muted">
          Delete
          <span class="font-medium text-highlighted">
            {{ deleteTarget?.position }}
          </span>
          at
          <span class="font-medium text-highlighted">
            {{ deleteTarget?.company }}</span
          >? This cannot be undone.
        </p>
      </template>
      <template #footer>
        <div class="flex w-full justify-end gap-2">
          <UButton color="neutral" variant="ghost" @click="deleteTarget = null">
            Cancel
          </UButton>
          <UButton color="error" :loading="deleting" @click="performDelete">
            Delete
          </UButton>
        </div>
      </template>
    </UModal>
  </UContainer>
</template>
