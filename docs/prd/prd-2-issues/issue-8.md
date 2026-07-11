# issue-8 — Joby AGENT (Claude CLI, autonomiczne, wynik strukturalny)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduł `ai`; `ClaudeCliClient`, `AgentJobHandler`).

## What to build
Najcięższy, autonomiczny typ Joba: agent (Claude CLI) wykonuje zadania z narzędziami — np.
„przejrzyj moje repo i powiedz, co poprawić". Osobny byt od OpenRouter (agent-z-narzędziami, nie
chat), więc **osobny port**; wspólnym kontraktem pozostaje `JobHandler`. Ostatni plasterek —
kosztowny, obciążający i niezależny od reszty.

- `ai`: port `ClaudeCliClient` — `ProcessBuilder` → `claude -p "…" --output-format json`,
  **parsowanie strukturalne** wyniku. Osobny od `OpenRouterClient` (bez wspólnego `LlmClient`).
- `AgentJobHandler` (typ `AGENT`): in `{prompt, workdir, taskId?}`; out `{structuredFindings, raw}`.
- Współbieżność: AGENT ograniczony do **1 naraz** (mechanizm limitu per typ z issue-1) — kosztowny subprocess.
- Wynik **strukturalny (parsowalny)**, nie luźny tekst — nadający się do dalszego użycia.
- W testach `ClaudeCliClient` **mockowany** (bez realnego uruchamiania subprocesu) — adapter
  testowany płytko/kontraktowo.

## Acceptance criteria
- [ ] Port `ClaudeCliClient` jest osobnym interfejsem (nie schowany za wspólnym `LlmClient`) i jest mockowalny.
- [ ] `AgentJobHandler` mapuje `input {prompt, workdir, taskId?}` → `output {structuredFindings, raw}` przy zamockowanym porcie.
- [ ] Wynik agenta jest sparsowany do struktury (nie zwracany jako surowy tekst).
- [ ] Współbieżność AGENT = 1: przy dwóch zakolejkowanych Jobach AGENT drugi czeka na zakończenie pierwszego.
- [ ] Nieudane/niepoprawne wyjście CLI jest obsłużone przez retry/`FAILED` runnera (issue-1), nie wywala pollera.
- [ ] Testy: serwisowy handlera i limitu współbieżności (Mockito) + kontraktowy parsowania wyjścia CLI; bez realnych wywołań zewnętrznych.

## Blocked by
`issue-7`
