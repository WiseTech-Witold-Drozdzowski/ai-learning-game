import express from 'express'
import { readFile, writeFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data')
const DIST_DIR = path.join(__dirname, 'dist')
const PORT = process.env.PORT || 3000

const app = express()
app.use(express.json({ limit: '4mb' }))

const sectionFile = (id) => path.join(DATA_DIR, `${id}.json`)

// "8/10" -> 8, "4/5" -> 8, "7" -> 7 (assumed /10); null when unparseable
function parseScore(score) {
  if (typeof score === 'number') return score
  if (typeof score !== 'string') return null
  const frac = score.match(/(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/)
  if (frac) return (Number(frac[1]) / Number(frac[2])) * 10
  const num = score.match(/\d+(?:\.\d+)?/)
  return num ? Number(num[0]) : null
}

function averageScore(questions) {
  const scores = questions
    .map((q) => parseScore(q.evaluation?.score))
    .filter((s) => s != null && Number.isFinite(s))
  if (!scores.length) return null
  return Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10
}

async function readSection(id) {
  return JSON.parse(await readFile(sectionFile(id), 'utf-8'))
}

app.get('/api/sections', async (_req, res) => {
  const files = (await readdir(DATA_DIR)).filter((f) => f.endsWith('.json'))
  const sections = []
  for (const file of files) {
    const id = file.replace(/\.json$/, '')
    try {
      const s = JSON.parse(await readFile(path.join(DATA_DIR, file), 'utf-8'))
      const questions = s.questions ?? []
      sections.push({
        id,
        title: s.title ?? id,
        total: questions.length,
        answered: questions.filter((q) => (q.answer ?? '').trim()).length,
        evaluated: questions.filter((q) => q.evaluation).length,
        followUps: questions.filter((q) => q.followUp).length,
        scored: questions.filter((q) => parseScore(q.evaluation?.score) != null).length,
        avgScore: averageScore(questions),
      })
    } catch {
      // skip malformed files
    }
  }
  sections.sort((a, b) => a.title.localeCompare(b.title))
  res.json(sections)
})

app.get('/api/sections/:id', async (req, res) => {
  try {
    res.json(await readSection(req.params.id))
  } catch {
    res.status(404).json({ error: 'Section not found' })
  }
})

// Merge answers only; never overwrite agent-written questions/evaluations/follow-ups.
app.put('/api/sections/:id/answers', async (req, res) => {
  const answers = req.body?.answers ?? {}
  try {
    const section = await readSection(req.params.id)
    for (const q of section.questions ?? []) {
      if (Object.prototype.hasOwnProperty.call(answers, q.id)) {
        q.answer = answers[q.id]
      }
    }
    await writeFile(sectionFile(req.params.id), JSON.stringify(section, null, 2) + '\n')
    res.json(section)
  } catch {
    res.status(404).json({ error: 'Section not found' })
  }
})

app.use(express.static(DIST_DIR))
app.get('*', (_req, res) => res.sendFile(path.join(DIST_DIR, 'index.html')))

app.listen(PORT, () => console.log(`ai-teacher listening on :${PORT} (data: ${DATA_DIR})`))
