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
  - JWT = the wristband the front desk issues after you show ID
  - Redis = the notepad by the till — instantly checkable, rebuildable if lost

---

## My environment

- MacBook Air (Apple Silicon), macOS
- Java 21 (Temurin) — project SDK must be **temurin-21**, never 26.
  `JAVA_HOME` is pinned in `~/.zshrc` because Maven otherwise picks up Java 26 and Lombok breaks.
- IntelliJ IDEA
- PostgreSQL 18 via Postgres.app, port 5432, databases `transakt` and `transakt_test`
- **`psql` is not on the PATH** — binaries at `/Applications/Postgres.app/Contents/Versions/latest/bin/`
- **Redis 8.10 via Homebrew.** `brew services start redis` **fails** on this machine — run
  `redis-server` in a dedicated Terminal tab instead, and leave it open while working.
- Postman for API testing
- Project path: `~/Documents/Coding/transakt`

**To run the tests:** Redis and Postgres up, then `./mvnw test`. Expect 19 tests, ~20 seconds.

---

## Tech stack (versions matter!)

- **Spring Boot 3.4.1** — was 4.1.0, downgraded because no matching security starter existed
- Maven, Java 21, YAML config (`application.yaml`)
- Dependencies: spring-boot-starter-**web** (not webmvc), validation, data-jpa, security,
  **data-redis**, devtools, postgresql, lombok, spring-boot-starter-**test** (not webmvc-test)
- **jjwt 0.13.0** — three artifacts: `jjwt-api` (compile), `jjwt-impl` (runtime),
  `jjwt-jackson` (runtime). Needs explicit `<version>` tags; the parent POM doesn't manage it.
  Spring starters need no version tag.
- `ddl-auto: update`, `show-sql: true`, `open-in-view: false`
- `jwt.secret` via `${JWT_SECRET:default}` (32-char minimum for HS256), `jwt.expiration-ms: 3600000`
- `ratelimit.requests-per-minute: ${RATE_LIMIT_PER_MINUTE:20}`

---

## Current state: Day 11 complete

| Day | What was built |
|-----|----------------|
| 1 | Tooling, repo, README, .gitignore, docs/ |
| 2 | Spring Boot running, embedded Tomcat, `GET /api/v1/health` |
| 3 | Merchant domain — controller, service, in-memory HashMap, full CRUD |
| 4 | PostgreSQL + Spring Data JPA — data survives restarts |
| 5 | Payments + **double-entry ledger**, atomic `@Transactional` writes |
| 6 | DTO validation + `GlobalExceptionHandler` (clean 400s and 404s) |
| 7 | API key authentication via a custom Spring Security filter |
| 8 | BCrypt passwords, JWT login, second auth filter, role-based access control |
| 9 | Ownership authorization — identity from the token, 404 on foreign resources |
| 10 | Redis: idempotency keys and per-merchant rate limiting |
| 11 | **Integration test suite — 19 tests covering auth, ownership, idempotency, rate limiting** |

Architecture doc is at **v1.0**. All work committed and pushed.

**Test accounts (dev database):** `test@shop.com` / `hunter2` = ADMIN ·
`regular@shop.com` / `hunter2` = MERCHANT. The three `priya@` merchants predate the password
column and cannot log in. The test suite creates and rolls back its own merchants.

---

## Package structure

```
com.transakt.transakt
├── auth/       JwtService, JwtAuthFilter, AuthService, AuthController,
│               LoginRequest, LoginResponse
├── common/     GlobalExceptionHandler, ResourceNotFoundException,
│               InvalidCredentialsException, IdempotencyConflictException,
│               ApiKeyFilter, RateLimitFilter, SecurityConfig, PasswordConfig,
│               IdempotencyService, RateLimitService
├── merchant/   Merchant (entity), MerchantRole (enum), MerchantRepository,
│               MerchantService, MerchantController
├── payment/    Payment, PaymentStatus (enum), CreatePaymentRequest (DTO),
│               PaymentRepository, PaymentService, PaymentController
├── ledger/     LedgerEntry, EntryDirection (enum), LedgerEntryRepository
├── HealthController
└── TransaktApplication

src/test/java/com/transakt/transakt
├── TransaktApplicationTests      smoke test — the context loads
├── AuthIntegrationTest           6 tests
├── OwnershipIntegrationTest      5 tests
├── IdempotencyIntegrationTest    5 tests
└── RateLimitIntegrationTest      2 tests

src/test/resources/application-test.yaml    the `test` profile
```

---

## Key design decisions (and why)

- **Double-entry ledger** — every payment appends two balancing rows. Balances are the sum of
  entries, never stored. Append-only, so the trail is tamper-evident.
- **Money as integer paise** (`Long`), never decimals. ₹500 = 50000.
- **`@Transactional` on payment creation** — payment row and both ledger entries commit
  together or not at all.
- **Server-controlled fields** — id, status, createdAt, role and **merchantId** are set by the
  server. A client cannot choose its own identity any more than its own id.
- **Layered architecture** — controller (HTTP only) → service (rules) → repository (data).
  Controllers read `Authentication` and pass plain values down.
- **Two authentication doors** — API keys for machines (permanent, one DB lookup per request),
  JWT for humans (1 hour, carries a role, no DB hit). Trade-off: JWTs can't be revoked early.
- **Three layers of authorization** — authentication, roles, ownership. Roles do nothing for
  the third question.
- **Foreign resources return 404, not 403** — a 403 confirms the ID is real. Both cases use an
  identical message, which is what makes it work.
- **Fetch-then-check for one, scope-the-query for many.**
- **Two state stores** — Postgres for the truth, Redis for facts that expire. TTL means no
  cleanup job exists anywhere in the codebase.
- **Idempotency keys on `POST /payments`** — atomic `SET NX EX`, scoped per merchant, 24h TTL.
- **Idempotency orchestration is in the controller** — because self-invocation would bypass
  Spring's `@Transactional` proxy.
- **Rate limiting via `INCR`** on a key containing the current minute, so the window resets by
  itself when the key name changes.
- **Tests are integration tests, deliberately** — the interesting behaviour lives in the wiring
  (filter chain, path rules, ownership, Redis), and a mocked unit test would pass while the
  security config was wide open.

---

## Endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/health` | open |
| POST | `/api/v1/auth/login` | open |
| POST | `/api/v1/merchants` | open (bootstrap: signup) |
| GET/PUT/DELETE | `/api/v1/merchants/**` | requires `ROLE_ADMIN` |
| POST | `/api/v1/payments` | authenticated; merchant from the token; optional `Idempotency-Key` |
| GET | `/api/v1/payments` | authenticated; **scoped to the caller**, all rows for admins |
| GET | `/api/v1/payments/{id}` | authenticated; **404 unless yours or admin** |
| GET | `/api/v1/payments/{id}/ledger` | authenticated; **404 unless yours or admin** |

All authenticated endpoints are rate limited to 20 requests per merchant per minute.

---

## Gotchas already hit (don't repeat these)

- Lombok needs **annotation processing enabled** in IntelliJ
- **Lombok also breaks on a too-new JDK** — `TypeTag :: UNKNOWN` at compile time means Maven is
  using a different JDK from IntelliJ. Check `./mvnw -version` against the project SDK.
- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- **A correctly-spelled YAML key in the wrong place is silently ignored** — a misplaced
  `data: redis:` fell back to defaults that happened to match, so nothing appeared wrong
- `.gitignore` only blocks **untracked** files; use `git rm -r --cached`
- Repositories seemingly missing `save()`/`findById()` = **stale IntelliJ index**
- Artifact **names** change between major Spring versions, not just numbers
- 401 = "who are you"; 403 = "not permitted"; 409 = "conflicts with current state";
  429 = "too fast"
- **Tables are plural** — `merchants`, `payments`, `ledger_entries`
- **`ddl-auto: update` cannot add a NOT NULL column** to a populated table
- **jjwt 0.12 was a breaking release** — `verifyWith`/`parseSignedClaims` replaced the old API
- **`hasRole("ADMIN")` looks for an authority literally named `ROLE_ADMIN`**
- **`authorizeHttpRequests` is first-match-wins**, but `@GetMapping` resolves by pattern
  specificity — two config systems, two rules
- **Self-invocation defeats Spring's proxies** — a `@Transactional` method called from another
  method on the same bean runs with no transaction, silently
- **Filters run before the DispatcherServlet**, so `@RestControllerAdvice` cannot catch what
  they throw — write the response by hand
- **`src/main` and `src/test` are separate compilation units.** A test class under `src/main`
  fails with `cannot find symbol: class Test`, because `spring-boot-starter-test` is
  test-scoped. Right-click the package under **`src/test/java`**, not the identical one under
  `src/main/java`.
- **`@Transactional` rolls back Postgres, not Redis.** Test isolation is per-store — anything
  outside the transaction manager needs an explicit `flushDb()` in `@BeforeEach`.
- **Two IntelliJ import traps:** `lombok.Value` above Spring's `@Value`, `java.sql.Date` above
  `java.util.Date`. Also **"Add static import" ≠ "Import class"**, and it will offer
  `RequestEntity.post` instead of `MockMvcRequestBuilders.post` — "Ambiguous method call" means
  both are imported.
- **A big error count usually means *one* structural problem** — fix the earliest one
- **IntelliJ shows fake errors in `notes.md`** — it injects a Java parser into ```java code
  fences and the fragments don't compile. Markdown doesn't compile; ignore them.
- **A sudden 403 on a request that worked minutes ago = check the token age first**
- **Store the JWT in a shell variable** (`TOKEN='...'`) rather than editing long curl lines
- **`git add` before `mv` stages the old paths** — re-run `git add -A src/` and git records
  clean renames instead of showing the same file three times
- **`cp` to a different filename case does nothing on macOS** — use `git mv`
- **macOS numbers repeat downloads** — `~/Downloads/notes.md` may be weeks old. Always run
  `ls -lt ~/Downloads/*.md | head -5` and check today's timestamp before copying.
- Watch for **dictation landing in an open file** instead of the chat box

---

## What's next

- **Day 12 — Flyway migrations, pagination, hashed API keys.** The three things standing
  between this and a deployable state.
- Then: Docker + docker-compose (whole stack in one command)
- Then: first real deployment — Railway or Render, managed Postgres and Redis, HTTPS
- Then: Kafka (payment events, webhook delivery with retries + DLQ), bank simulator,
  Kubernetes as a deliberate exercise, Spring AI

Also outstanding:

- Unauthenticated traffic isn't rate limited; no IP-based limiter
- Fixed-window rate limiting allows a boundary burst
- Idempotency keys aren't fingerprinted against the request body (Stripe returns 422)
- No pagination on `GET /api/v1/payments`
- API keys still plain text
- Schema changes are manual under `ddl-auto: update`
- `POST /api/v1/merchants` returns 200, not 201; no password-change endpoint;
  no `Retry-After` header on 429s
- No CI — the suite runs locally but nothing runs it on push

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, currently **v1.0**
- `docs/UNDERSTANDING.md` — from-scratch primer: what a gateway is, HTTP and Postman from
  zero, credentials explained, day-by-day reasoning, interview narrative, deployment roadmap
- `docs/CONTEXT.md` — this file