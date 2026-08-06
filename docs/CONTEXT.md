# Transakt — Context File

Paste this into a new chat to restore full project context.

---

## What this project is

Transakt is a payment gateway built from scratch in Java — my own Razorpay.
Online shops call one simple API instead of dealing with banks directly.
The bank is simulated; no real money moves.

**Repo:** github.com/ARV0007/transakt

---

## Who I am and how I want to be taught

- Aman, M.Tech CSE student. Building this to be placement- and industry-ready.
- I am a beginner with the advanced topics. Explain in plain English **before** the code.
- Give me step-by-step instructions with exact clicks. I get lost in jargon otherwise.
- I type every line myself — no code dumps I can't explain. Quiz me.
- At the end of each day, write my notes.md, WORKLOG.md, and architecture.md updates for me.
- Running analogy we use: **Transakt is a restaurant.**
    - Tomcat = front door · DispatcherServlet = head waiter · Controller = waiter
    - Service = chef · Repository = stock-keeper · PostgreSQL = the fridge
    - Spring IoC container = the manager who hires staff before opening
    - ApiKeyFilter = the ID-card reader at the staff entrance
    - JWT = the wristband the front desk issues after you show ID — expires at closing,
      has your access level printed on it, and the bouncer reads it without phoning the desk

---

## My environment

- MacBook Air (Apple Silicon), macOS
- Java 21 (Temurin) — project SDK must be **temurin-21**, never 26
- IntelliJ IDEA
- PostgreSQL 18 via Postgres.app, port 5432, database `transakt`, user `aman`, no password
- **`psql` is not on the PATH** — Postgres.app's binaries live at
  `/Applications/Postgres.app/Contents/Versions/latest/bin/`
- Postman for API testing
- Project path: `~/Documents/Coding/transakt`

---

## Tech stack (versions matter!)

- **Spring Boot 3.4.1** — was 4.1.0, downgraded because no matching security starter existed
- Maven, Java 21, YAML config (`application.yaml`)
- Dependencies: spring-boot-starter-**web** (not webmvc), validation, data-jpa, security,
  devtools, postgresql, lombok, spring-boot-starter-**test** (not webmvc-test)
- **jjwt 0.13.0** — three artifacts: `jjwt-api` (compile), `jjwt-impl` (runtime),
  `jjwt-jackson` (runtime). Needs explicit `<version>` tags; the parent POM doesn't manage it.
- `ddl-auto: update`, `show-sql: true`, `open-in-view: false`

---

## Current state: Day 7 complete, Day 8 in progress (steps 1-3 of 7)

| Day | What was built |
|-----|----------------|
| 1 | Tooling, repo, README, .gitignore, docs/ |
| 2 | Spring Boot running, embedded Tomcat, `GET /api/v1/health` |
| 3 | Merchant domain — controller, service, in-memory HashMap, full CRUD |
| 4 | PostgreSQL + Spring Data JPA — data survives restarts |
| 5 | Payments + **double-entry ledger**, atomic `@Transactional` writes |
| 6 | DTO validation + `GlobalExceptionHandler` (clean 400s and 404s) |
| 7 | API key authentication via a custom Spring Security filter |
| 8 | **In progress.** BCrypt passwords, jjwt on the classpath, `JwtService` written |

**Day 8 plan — 7 steps, 3 done:**

| # | Step | Status |
|---|------|--------|
| 1 | BCrypt password storage on Merchant | done |
| 2 | jjwt 0.13.0 dependencies | done |
| 3 | `jwt:` config block + `JwtService` | done |
| 4 | `POST /api/v1/auth/login` — email + password in, token out | next |
| 5 | `JwtAuthFilter` — read and verify `Authorization: Bearer` | to do |
| 6 | Wire both filters into `SecurityConfig` so they coexist | to do |
| 7 | Roles — `ROLE_MERCHANT` vs `ROLE_ADMIN`, endpoint RBAC | to do |

Architecture doc is at **v0.7**, with a v0.8 section marked *in progress* — it stays at v0.7
until step 6, because nothing in the running app issues or accepts a token yet.

---

## Package structure

```
com.transakt.transakt
├── auth/       JwtService
│               (AuthController, LoginRequest/Response, JwtAuthFilter — steps 4-5)
├── common/     GlobalExceptionHandler, ResourceNotFoundException,
│               ApiKeyFilter, SecurityConfig, PasswordConfig
├── merchant/   Merchant (entity), MerchantRepository,
│               MerchantService, MerchantController
├── payment/    Payment, PaymentStatus (enum), CreatePaymentRequest (DTO),
│               PaymentRepository, PaymentService, PaymentController
├── ledger/     LedgerEntry, EntryDirection (enum), LedgerEntryRepository
├── HealthController
└── TransaktApplication
```

---

## Key design decisions (and why)

- **Double-entry ledger** — every payment appends two balancing rows (CREDIT to merchant,
  DEBIT from gateway, equal amounts). Balances are never stored or edited; they're the sum
  of entries. If the books stop netting to zero, something is broken — errors are detectable.
  Entries are append-only, so the trail is tamper-evident.
- **Money as integer paise** (`Long`), never decimals. ₹500 = 50000. Floating point loses
  precision; in money that's a lawsuit.
- **`@Transactional` on payment creation** — the payment row and both ledger entries commit
  together or not at all. There can never be a payment without its accounting.
- **Server-controlled fields** — id, status, createdAt are set by the service, never the client.
  DTOs make this structural: the client literally cannot send those fields.
- **Layered architecture** — controller (HTTP only) → service (business rules) → repository
  (data). Proven when the HashMap became PostgreSQL and the controller needed zero changes.
- **API keys prefixed `tk_`** — like Stripe's `sk_`, Razorpay's `rzp_`. Recognisable, scannable
  if leaked.
- **Passwords hashed with BCrypt, cost factor 10** — deliberately slow (~100ms per attempt) so
  offline brute-forcing is impractical, and auto-salted so identical passwords produce different
  hashes. `@JsonProperty(WRITE_ONLY)` on the field means it's accepted on input and never
  serialised into a response — necessary because `/merchants` is still open.
- **`PasswordEncoder` bean lives in its own `PasswordConfig`**, not in `SecurityConfig`.
  SecurityConfig constructor-injects ApiKeyFilter, so putting the encoder there risks a bean
  dependency cycle.
- **Two authentication paths on purpose** — API keys for machines (permanent, one DB lookup per
  request), JWTs for humans (1 hour, carries a role, verified locally with no DB hit). The
  trade-off: a JWT can't be revoked before it expires, which is why the TTL is short.
- **JWT secret read as `${JWT_SECRET:default}`** — env var in production, committed dev
  throwaway locally, because the repo is public.

---

## Endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/health` | open |
| POST/GET/PUT/DELETE | `/api/v1/merchants` | open (bootstrap: need a merchant before a key) |
| POST | `/api/v1/payments` | requires `X-API-Key` header |
| GET | `/api/v1/payments/{id}` | requires key |
| GET | `/api/v1/payments/{id}/ledger` | requires key |
| POST | `/api/v1/auth/login` | **not built yet — Day 8 step 4** |

---

## Gotchas already hit (don't repeat these)

- Lombok needs **annotation processing enabled** in IntelliJ or its generated methods appear missing
- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- `.gitignore` only blocks **untracked** files; use `git rm -r --cached` for already-tracked ones
- When repositories seem to lack `save()`/`findById()`, it's a **stale IntelliJ index** —
  Maven Reload + Rebuild, or Invalidate Caches
- Artifact **names** change between major Spring versions, not just numbers
- 401 = "who are you"; 403 = "known request, not permitted"
- **Tables are plural** — `merchants`, `payments`, `ledger_entries`. Querying `merchant` gives
  "relation does not exist". `\dt` lists them.
- **Two import traps:** IntelliJ offers `lombok.Value` above Spring's `@Value`, and
  `java.sql.Date` above `java.util.Date`. The Lombok one produces nonsense errors; the SQL one
  compiles fine and silently discards the time component.
- **"Add static import" ≠ "Import class"** — picking the static one strips the `Jwts.` prefix
  from the call and produces errors that look unrelated
- **A method declared inside another method's body** (unclosed constructor brace) cascades into
  a huge error count. General rule: a big error count usually means *one* structural problem —
  fix the earliest error and the rest evaporate.
- jjwt **0.12 was a breaking release** — `setSubject`/`setExpiration`/`setSigningKey`/
  `parseClaimsJws` became `subject`/`expiration`/`verifyWith`/`parseSignedClaims`.
  Most tutorials online predate this and won't compile.

---

## What's next

- **Day 8 steps 4-7** — login endpoint, JwtAuthFilter, filter chain wiring, RBAC
- Then: Redis (idempotency — never double-charge; rate limiting)
- Then: Kafka (payment events, webhook delivery to merchants with retries + DLQ)
- Then: bank simulator, Docker, Kubernetes, Spring AI (RAG docs assistant, MCP server)

Also outstanding:

- API keys are stored in plain text. Hashing them isn't symmetric with passwords: verifying a
  key means *finding* the row, and a hash can't be indexed. Fix is a lookup prefix —
  `tk_live_<lookup>_<secret>`, index the lookup half, hash only the secret half (Stripe's
  approach). Candidate for ~Day 12.
- `POST /api/v1/merchants` returns 200; REST convention is 201 Created with a `Location` header.
- Password changes have no endpoint. `MerchantService.update()` deliberately ignores the field,
  since a naive `setPassword(updated.getPassword())` would either null it out on a PUT that
  omits it, or store it unhashed.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, v0.7 released,
  v0.8 section in progress
- `docs/CONTEXT.md` — this file
