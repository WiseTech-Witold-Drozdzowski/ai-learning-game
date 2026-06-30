# issue-1 — Fundament tożsamości + auth (prefactor)

## Parent
`docs/prd/prd-1-core-domain.md` (moduł `auth`; `User`/`CareerProfile`).

## What to build
Pionowy fundament, na którym wisi reszta domeny: tożsamość pojedynczego użytkownika za
logowaniem oraz jego profil kariery.

- Flyway: tabele `users(id, email UNIQUE, google_sub, display_name, created_at)` oraz
  `career_profile(user_id PK/FK, total_exp, level, avatar_state JSONB)`. `avatar_state` przez
  natywny Hibernate 6 (`@JdbcTypeCode(SqlTypes.JSON)`) na typowany rekord `AvatarState`.
- Auth: Google OAuth2 (`oauth2Login`) + email‑whitelist (tylko adres autora). `anyRequest()`
  staje się `authenticated()` — zastępuje tymczasowy permit‑all w `SecurityConfig`.
- Plumbing „current user": rozwiązanie zalogowanego użytkownika z kontekstu Security do encji
  `User` (auto‑provisioning profilu przy pierwszym logowaniu, jeśli email na whiteliście).
- `GET /api/me` — zwraca tożsamość + zalążek profilu (exp, level, avatarState).

To greenfield‑owy plasterek: ustanawia wzorzec testowy (Testcontainers Postgres dla
integracyjnych + uwierzytelnianie w testach przez podstawiony kontekst Security / mock OAuth2).

## Acceptance criteria
- [ ] Migracja Flyway tworzy `users` i `career_profile`; aplikacja wstaje na czystym Postgresie.
- [ ] `avatar_state` zapisuje/odczytuje typowany rekord `AvatarState` jako JSONB (round‑trip test).
- [ ] Żądanie bez uwierzytelnienia do chronionego endpointu → 401/redirect; z uwierzytelnieniem → 200.
- [ ] Email spoza whitelisty jest odrzucany (brak dostępu), email z whitelisty dostaje profil.
- [ ] `GET /api/me` zwraca dane zalogowanego usera; przy pierwszym logowaniu profil jest tworzony.
- [ ] Test integracyjny przez REST (Testcontainers) potwierdza ścieżkę `/api/me` za auth.

## Blocked by
None — can start immediately.
