# issue-3 — Czat strategiczny z coachem (streaming SSE)

## Parent
`docs/prd/prd-2-ai-jobs.md` (moduły `coach`, `ai`; streaming odpowiedzi LLM).

## What to build
Synchroniczna (nie-Jobowa) rozmowa z coachem przy ustalaniu celów strategicznych, z
odpowiedzią pojawiającą się na żywo token po tokenie. Plasterek ustanawia **seam streamingu**
OpenRouter SSE przepuszczanego przez Spring do klienta — ten sam mechanizm ponownie użyty przez
mock interview (issue-6).

- `ai`: rozszerzenie portu `OpenRouterClient` o tryb **streaming SSE** (strumień tokenów).
  W testach mockowany — stub emituje ustaloną sekwencję fragmentów.
- `coach`: budowa promptu czatu przez assembler kontekstu (z issue-2), z historią bieżącej
  rozmowy strategicznej.
- `POST /api/coach/messages` — zwraca `SseEmitter`; tokeny OpenRouter są przepuszczane przez
  Spring do klienta w miarę napływania (bez buforowania całości).

## Acceptance criteria
- [ ] `POST /api/coach/messages` zwraca strumień SSE; fragmenty odpowiedzi docierają inkrementalnie, nie jednorazowo na końcu.
- [ ] Prompt czatu jest budowany przez assembler kontekstu (rady nie są generyczne).
- [ ] Przy zamockowanym porcie stub emituje sekwencję fragmentów, a klient odbiera je w kolejności.
- [ ] Błąd portu w połowie strumienia jest sygnalizowany na SSE (zamiast cichego urwania).
- [ ] Test integracyjny przez REST konsumuje strumień i weryfikuje inkrementalne dostarczanie.

## Blocked by
`issue-2`
