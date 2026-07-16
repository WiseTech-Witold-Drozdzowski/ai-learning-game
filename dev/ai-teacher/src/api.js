async function json(res) {
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

export function listSections() {
  return fetch('/api/sections').then(json)
}

export function getSection(id) {
  return fetch(`/api/sections/${id}`).then(json)
}

export function saveAnswers(id, answers) {
  return fetch(`/api/sections/${id}/answers`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answers }),
  }).then(json)
}
