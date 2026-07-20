import express from 'express'
import path from 'node:path'
import * as store from './db.js'

// API contract is identical to the old JSON-file server; only the storage
// behind it changed to SQLite.
export function createApp({ db, distDir = null }) {
  const app = express()
  app.use(express.json({ limit: '4mb' }))

  app.get('/api/sections', (_req, res) => {
    res.json(store.listSections(db))
  })

  // :id is the URL-encoded section path, e.g. "java%2Fparallelism"
  app.get('/api/sections/:id', (req, res) => {
    const section = store.getSection(db, req.params.id)
    if (!section) return res.status(404).json({ error: 'Section not found' })
    res.json(section)
  })

  // Merge answers only; never overwrite agent-written questions/evaluations/follow-ups.
  app.put('/api/sections/:id/answers', (req, res) => {
    const id = req.params.id
    if (!store.getSection(db, id)) return res.status(404).json({ error: 'Section not found' })
    store.saveAnswers(db, id, req.body?.answers ?? {})
    res.json(store.getSection(db, id))
  })

  // Reset questions whose evaluation scored below the threshold (0-10 scale)
  // so they can be answered again from scratch.
  app.post('/api/sections/:id/clear-low-scores', (req, res) => {
    const threshold = Number(req.body?.threshold ?? 6)
    if (!Number.isFinite(threshold)) return res.status(400).json({ error: 'Invalid threshold' })
    const id = req.params.id
    if (!store.getSection(db, id)) return res.status(404).json({ error: 'Section not found' })
    const clearedIds = store.clearLowScores(db, id, threshold)
    res.json({ clearedIds, section: store.getSection(db, id) })
  })

  // Toggle a question's "assistRequired" flag — the evaluator writes an HTML
  // explanation only for flagged questions. Flagging clears the previous
  // explanation, which marks the question as pending for the evaluator.
  app.put('/api/sections/:id/assist', (req, res) => {
    const { questionId, assistRequired } = req.body ?? {}
    if (typeof questionId !== 'string' || typeof assistRequired !== 'boolean') {
      return res
        .status(400)
        .json({ error: 'questionId (string) and assistRequired (boolean) required' })
    }
    const id = req.params.id
    if (!store.getSection(db, id)) return res.status(404).json({ error: 'Section not found' })
    if (!store.setAssistRequired(db, id, questionId, assistRequired)) {
      return res.status(404).json({ error: 'Question not found' })
    }
    res.json(store.getSection(db, id))
  })

  // hidden=true hides questions scored at or above the threshold (0-10 scale);
  // hidden=false unhides every question.
  app.put('/api/sections/:id/hidden', (req, res) => {
    const hidden = req.body?.hidden
    const threshold = Number(req.body?.threshold ?? 6)
    if (typeof hidden !== 'boolean') return res.status(400).json({ error: 'hidden must be a boolean' })
    if (!Number.isFinite(threshold)) return res.status(400).json({ error: 'Invalid threshold' })
    const id = req.params.id
    if (!store.getSection(db, id)) return res.status(404).json({ error: 'Section not found' })
    const changedIds = store.setHidden(db, id, hidden, threshold)
    res.json({ changedIds, section: store.getSection(db, id) })
  })

  if (distDir) {
    app.use(express.static(distDir))
    app.get('*', (_req, res) => res.sendFile(path.join(distDir, 'index.html')))
  }

  return app
}
