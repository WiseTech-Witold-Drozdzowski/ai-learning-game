# Product Design — AI Career Coach (gamifikacja kariery)

> Status: high-level design (v1). Dokument źródłowy decyzji produktowych.
> Towarzyszący dokument techniczny: [`TECHNICAL_DESIGN.md`](./TECHNICAL_DESIGN.md).

## 1. Wizja

Osobisty **system operacyjny kariery** napędzany przez AI-coacha. To nie jest tylko trener
do interview — to narzędzie do **nauki, sprawdzania i rozwoju zawodowego**, ubrane w mechanikę
gry RPG. Użytkownik (single-user — autor) wchodzi na stronę i widzi: „na dziś masz to i to",
wykonuje zadania, AI weryfikuje wynik, użytkownik dostaje exp i rozwija skille oraz awatara.

Interview (mock) jest **jednym z typów testowania**, nie celem samym w sobie. Głównym zadaniem
systemu jest: **planować naukę → pilnować jej → weryfikować → adaptować plan**.

### Cztery równoległe cele projektu
1. **Realne narzędzie do nauki** — autor faktycznie z niego korzysta.
2. **Projekt do portfolio** — pokazuje integrację z LLM, system agentowy, Spring + Angular.
3. **Nauka technologii** — sam proces budowy jest wartością.
4. **Zabawa / motywacja** — gamifikacja napędza regularność.

Te cele się nie gryzą: dobre narzędzie *jest* dobrym portfolio, a budowanie go *jest* nauką.

## 2. Pętla coachingowa (rdzeń produktu)

```
coach planuje  →  zadania trafiają do kalendarza  →  użytkownik wykonuje
      ↑                                                      │
      └──────  coach adaptuje plan  ←  exp+  ←  weryfikacja ─┘
```

To jest serce wartości. Wszystko inne (awatar, animacje, kalendarz) to warstwy wokół tej pętli.
**Najpierw musi działać pętla; reszta to cukier.**

## 3. Struktura celów — drzewo ze stanami

Cele tworzą hierarchię z **różnym poziomem autonomii AI na różnych piętrach**:

```
🎯 Cel strategiczny       (np. "Zostać Senior Java Dev")
   └─ 📦 Level / cel wyższy (np. "Opanować system design")
        └─ ✅ Zadanie/quest  (np. "Zaprojektuj rate limiter i obroń to w mocku")
```

### Filozofia odpowiedzialności — *akceptacja skaluje się ze stawką decyzji*

| Poziom | Kto decyduje | Uzasadnienie |
|---|---|---|
| Cel strategiczny | wspólnie + **akceptacja użytkownika** | wysoka stawka, trudne do cofnięcia |
| Level / cel wyższy | wspólnie + **akceptacja użytkownika** | kierunkowe, kosztowna pomyłka |
| Zadanie / quest | **coach autonomicznie** | tanie, odwracalne, łatwe do podmiany |

To wzorzec **human-in-the-loop na poziomie strategicznym, autonomia na taktycznym**.

### Cykl życia celu

```
PROPONOWANY (przez coacha) → ZAAKCEPTOWANY (przez użytkownika) → AKTYWNY → ZAMKNIĘTY
```

Reguła: **coach może generować zadania tylko pod celami w stanie AKTYWNY.** To trzyma
autonomię w ryzach — AI nie zasypie zadaniami celu, którego użytkownik nie zatwierdził.

## 4. Typy zadań i weryfikacja („mix per typ")

Exp przyznaje **backend, nie AI** (AI tylko proponuje ocenę). Reguła przewodnia:
**exp skaluje się z twardością weryfikacji** — to rozwiązuje problem „pustego expa"
(klikanie „zrobione" bez wysiłku daje mało; realna, oceniana praca daje dużo).

| Typ | Przykład | Weryfikacja | Exp |
|---|---|---|---|
| **READING** (nauka) | „przeczytaj o CAP theorem" | Honor (klik „zrobione") | mały, ryczałt |
| **QUIZ** (wiedza) | „5 pytań o indeksach SQL" | Auto — AI generuje i ocenia | wg % poprawnych |
| **MOCK** (interview) | „obroń projekt rate-limitera" | AI-rozmowa, coach przepytuje i ocenia | wg oceny |
| **ARTIFACT** (wytwórczy) | „napisz sekcję portfolio / zadanie kodowe" | AI-recenzja artefaktu (lub agent, jeśli repo) | wg jakości |
| **EXTERNAL** (czyn) | „wyślij aplikację do firmy X" | Honor + opcjonalny dowód (link/screenshot) | ryczałt |

### Typy zadań jako konfiguracja (nie statyczny enum)
Definicje typów to **dane**, nie kod: `{ klucz, metoda_weryfikacji, formuła_expa, czy_wymaga_artefaktu, ... }`.
Dodanie nowego typu (np. **HABIT** — nawyk powtarzalny) = wpis w configu, nie zmiana kodu.
Model przechowywania: YAML jako seed → baza jako źródło prawdy, edytowalna w runtime
(szczegóły w dokumencie technicznym).

> **Rozróżnienie:** *definicje typów* (rzadkie, mechanika) → config. *Instancje zadań*
> (konkretne questy generowane przez coacha) → zawsze baza.

### Priorytet implementacji
- MVP-rdzeń: **QUIZ** + **MOCK** (pokazują AI-weryfikację — sedno wartości).
- Trywialne od razu: **READING**, **EXTERNAL** (honor).
- Zaraz potem: **ARTIFACT** (czasem wymaga agent-joba).

## 5. Gamifikacja

Drzewo celów mapuje się bezpośrednio na mechanikę gry:

| Element kariery | Element gry |
|---|---|
| Cel strategiczny | Kampania |
| Level / cel wyższy | Boss / etap |
| Zadanie | Quest |

- **Exp bąbelkuje w górę drzewa** — exp z questów wypełnia paski wyższych poziomów,
  awatar rośnie z sumy. Jedna spójna ekonomia, bez osobnej waluty.
- **Skille** wyrastają naturalnie z typów/tematów zadań (np. „System Design", „Behavioral", „SQL").
- **Awatar** na MVP: statyczny + paski expa, poziomy, odznaki (stan trzymany jako dane,
  pod późniejsze wzbogacenie wizualne).

> **Otwarte (do strojenia eksperymentalnego):** krzywa expa, ile exp na level, dokładne wagi
> bąbelkowania. Kalibrowane w praktyce, nie teoretyzowane z góry.

## 6. Ekrany (architektura informacji)

Rdzeń MVP = **1–3** (pełna pętla coachingowa end-to-end). **4–5** zaraz potem.

1. **Dashboard / „dziś"** *(MVP)* — co zrobić dziś, pasek expa, awatar, szybki postęp. Ekran startowy.
2. **Drzewo celów (mapa kariery)** *(MVP)* — wizualizacja celów ze stanami; tu akceptujesz propozycje coacha.
3. **Widok zadania / sesja** *(MVP)* — pojedynczy quest: opis, miejsce na artefakt/odpowiedź,
   uruchomienie weryfikacji (mock, recenzja). Tu „dzieje się" interakcja z AI.
4. **Rozmowa z coachem** — czat do wspólnego ustalania celów wyższego poziomu (z akceptacją).
5. **Profil / awatar / skille** — karta postaci RPG: poziomy skilli, odznaki, historia.

Globalny element: **wskaźnik Jobów w toku** („coach myśli…", „ocena w toku…") — bo
planning/evaluation/agent są asynchroniczne.

## 7. Integracje

- **OpenRouter** — synchroniczne wywołania LLM (rozmowa, ocena, generowanie quizów).
- **Claude CLI (agent)** — długo-żyjące, autonomiczne zadania z narzędziami
  (np. „przejrzyj moje repo i powiedz co poprawić w portfolio", „zbadaj ogłoszenie i ułóż plan").
- **Google Calendar** *(warstwa 2)* — zadania na dziś trafiają do kalendarza.
  Logowanie Google = przy okazji autoryzacja kalendarza (jeden flow OAuth).

## 8. Pomysł wyróżniający (kierunek rozwoju)

Wgrywasz **prawdziwe ogłoszenie o pracę** → AI parsuje wymagania → generuje z nich
„firmę-bossa" z konkretnymi skillami do pokonania → prowadzi interview/przygotowanie
dopasowane do tej oferty. Wtedy gra jest realnie użyteczna (ćwiczysz pod konkretną ofertę)
i ma mocny pitch portfolio.

## 9. Zakres — czego MVP NIE robi

- Brak multi-user (single-user, email-whitelist).
- Brak generowanego AI obrazu awatara (statyczny na start).
- Brak animacji/„żywego" awatara (architektura gotowa, implementacja później).
- Google Calendar = warstwa 2, nie blokuje rdzenia.
- Ekonomia expa kalibrowana eksperymentalnie, nie zaprojektowana z góry.
