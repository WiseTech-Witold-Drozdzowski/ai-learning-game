<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { listSections, getSection, saveAnswers } from './api'
import QuestionCard from './components/QuestionCard.vue'

const sections = ref([])
const currentId = ref(null)
const section = ref(null)
const answers = ref({})
const loading = ref(false)
const saving = ref(false)
const status = ref('')
const statusError = ref(false)

const theme = ref('light')

function applyTheme(value) {
  theme.value = value
  document.documentElement.dataset.theme = value
  localStorage.setItem('ai-teacher-theme', value)
}

function toggleTheme() {
  applyTheme(theme.value === 'dark' ? 'light' : 'dark')
}

// ---- unsaved answer drafts (localStorage) ----

const draftsKey = (id) => `ai-teacher-drafts:${id}`

function loadDrafts(id) {
  try {
    return JSON.parse(localStorage.getItem(draftsKey(id))) ?? {}
  } catch {
    return {}
  }
}

// keep only drafts that differ from what's saved in the file
function persistDrafts() {
  if (!currentId.value || !section.value) return
  const drafts = {}
  for (const q of section.value.questions) {
    const v = answers.value[q.id] ?? ''
    if (v !== (q.answer ?? '')) drafts[q.id] = v
  }
  if (Object.keys(drafts).length) {
    localStorage.setItem(draftsKey(currentId.value), JSON.stringify(drafts))
  } else {
    localStorage.removeItem(draftsKey(currentId.value))
  }
}

watch(answers, persistDrafts, { deep: true })

// ---- stats ----

const totals = computed(() => {
  const t = { sections: sections.value.length, questions: 0, answered: 0, evaluated: 0, followUps: 0 }
  let scoreSum = 0
  let scored = 0
  for (const s of sections.value) {
    t.questions += s.total
    t.answered += s.answered
    t.evaluated += s.evaluated
    t.followUps += s.followUps
    if (s.avgScore != null && s.scored) {
      scoreSum += s.avgScore * s.scored
      scored += s.scored
    }
  }
  t.avgScore = scored ? Math.round((scoreSum / scored) * 10) / 10 : null
  return t
})

const dirty = computed(() => {
  if (!section.value) return false
  return section.value.questions.some((q) => (q.answer ?? '') !== (answers.value[q.id] ?? ''))
})

async function refreshSections() {
  sections.value = await listSections()
}

function openDashboard() {
  currentId.value = null
  section.value = null
  status.value = ''
  refreshSections()
}

async function openSection(id) {
  loading.value = true
  status.value = ''
  try {
    currentId.value = id
    const data = await getSection(id)
    section.value = data
    const merged = Object.fromEntries(data.questions.map((q) => [q.id, q.answer ?? '']))
    const drafts = loadDrafts(id)
    for (const [qid, v] of Object.entries(drafts)) {
      if (qid in merged) merged[qid] = v
    }
    answers.value = merged
  } finally {
    loading.value = false
  }
}

function setStatus(text, isError = false) {
  status.value = text
  statusError.value = isError
}

async function save() {
  if (!currentId.value) return
  saving.value = true
  setStatus('')
  try {
    const updated = await saveAnswers(currentId.value, answers.value)
    section.value = updated
    answers.value = Object.fromEntries(updated.questions.map((q) => [q.id, q.answer ?? '']))
    setStatus('Saved ✓')
    await refreshSections()
  } catch (e) {
    setStatus(`Error: ${e.message}`, true)
  } finally {
    saving.value = false
  }
}

async function reload() {
  if (currentId.value) await openSection(currentId.value)
  await refreshSections()
  setStatus('Reloaded ✓')
}

const fmtScore = (v) => (v == null ? '—' : `${v}/10`)

onMounted(async () => {
  const saved = localStorage.getItem('ai-teacher-theme')
  const preferred = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  applyTheme(saved || preferred)
  await refreshSections()
})
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">
        <h1><span class="logo">🎓</span> AI Teacher</h1>
        <button class="theme-toggle" :title="theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'" @click="toggleTheme">
          {{ theme === 'dark' ? '☀️' : '🌙' }}
        </button>
      </div>

      <nav>
        <button class="section-item home" :class="{ active: !currentId }" @click="openDashboard">
          <span class="section-title">📊 Dashboard</span>
        </button>

        <button
          v-for="s in sections"
          :key="s.id"
          class="section-item"
          :class="{ active: s.id === currentId }"
          @click="openSection(s.id)"
        >
          <span class="section-title">{{ s.title }}</span>
          <span class="progress">
            <span class="progress-bar" :style="{ width: s.total ? (s.answered / s.total) * 100 + '%' : '0%' }"></span>
          </span>
          <span class="section-meta">{{ s.answered }}/{{ s.total }} answered · {{ s.evaluated }} evaluated</span>
        </button>
        <p v-if="!sections.length" class="empty">No sections yet. Run the question-writer agent.</p>
      </nav>

      <div class="sidebar-footer">
        <button class="btn ghost" style="width: 100%" @click="reload">↻ Reload sections</button>
      </div>
    </aside>

    <main class="content" :class="{ wide: !section && !loading }">
      <div v-if="loading" class="empty">Loading…</div>

      <template v-else-if="section">
        <header class="toolbar">
          <div>
            <h2>{{ section.title }}</h2>
            <p v-if="section.description" class="desc">{{ section.description }}</p>
          </div>
          <div class="actions">
            <span v-if="status" class="status" :class="{ error: statusError }">{{ status }}</span>
            <button class="btn ghost" @click="reload">Reload</button>
            <button class="btn" :disabled="!dirty || saving" @click="save">
              {{ saving ? 'Saving…' : 'Save answers' }}
            </button>
          </div>
        </header>

        <p v-if="dirty" class="draft-note">Unsaved changes — kept locally until you save.</p>

        <QuestionCard
          v-for="(q, i) in section.questions"
          :key="q.id"
          :question="q"
          :index="i"
          v-model="answers[q.id]"
        />
      </template>

      <template v-else>
        <header class="toolbar">
          <div>
            <h2>Dashboard</h2>
            <p class="desc">Your progress across all technologies.</p>
          </div>
          <div class="actions">
            <button class="btn ghost" @click="refreshSections">Refresh</button>
          </div>
        </header>

        <div class="stats-grid">
          <div class="stat-tile">
            <span class="stat-label">Technologies</span>
            <span class="stat-value">{{ totals.sections }}</span>
          </div>
          <div class="stat-tile">
            <span class="stat-label">Questions</span>
            <span class="stat-value">{{ totals.questions }}</span>
          </div>
          <div class="stat-tile">
            <span class="stat-label">Answered</span>
            <span class="stat-value">{{ totals.answered }}<span class="stat-sub">/{{ totals.questions }}</span></span>
          </div>
          <div class="stat-tile">
            <span class="stat-label">Evaluated</span>
            <span class="stat-value">{{ totals.evaluated }}</span>
          </div>
          <div class="stat-tile">
            <span class="stat-label">Average mark</span>
            <span class="stat-value">{{ fmtScore(totals.avgScore) }}</span>
          </div>
        </div>

        <div class="table-wrap">
          <table class="stats-table">
            <thead>
              <tr>
                <th>Technology</th>
                <th>Questions</th>
                <th>Answered</th>
                <th>Evaluated</th>
                <th>Follow-ups</th>
                <th>Avg mark</th>
                <th class="col-progress">Progress</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in sections" :key="s.id" @click="openSection(s.id)">
                <td class="cell-title">{{ s.title }}</td>
                <td>{{ s.total }}</td>
                <td>{{ s.answered }}</td>
                <td>{{ s.evaluated }}</td>
                <td>{{ s.followUps }}</td>
                <td>
                  <span v-if="s.avgScore != null" class="mark">{{ fmtScore(s.avgScore) }}</span>
                  <span v-else class="mark-empty">—</span>
                </td>
                <td class="col-progress">
                  <span class="progress">
                    <span class="progress-bar" :style="{ width: s.total ? (s.answered / s.total) * 100 + '%' : '0%' }"></span>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-if="!sections.length" class="empty">No sections yet. Run the question-writer agent.</p>
        </div>
      </template>
    </main>
  </div>
</template>
