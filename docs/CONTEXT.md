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
    access level printed on it, readable by the bouncer without phoning the desk
  - **Redis = the notepad by the till** — instantly checkable, rebuildable if lost

---

## My environment

- MacBook Air (Apple Silicon), macOS
- Java 21 (Temurin) — project SDK must be **temurin-21**, never 26
- IntelliJ IDEA
- PostgreSQL 18 via Postgres.app, port 5432, database `transakt`, user `aman`, no password
- **`psql` is not on the PATH** — Postgres.app's binaries live at
  `/Applications/Postgres.app/Contents/Versions/latest/bin/`
- **Redis 8.10 via Homebrew.** `brew services start redis` **fails** on this machine — run
  `redis-server` in a dedicated Terminal tab instead, and leave it open while working.
- Postman for API testing
- Project path: `~/Documents/Coding/transakt`

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

## Current state: Day 10 complete

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
| 10 | **Redis: idempotency keys and per-merchant rate limiting** |

Architecture doc is at **v1.0**. All work committed and pushed.

**Test accounts:** `test@shop.com` / `hunter2` = ADMIN · `regular@shop.com` / `hunter2` = MERCHANT.
The three `priya@` merchants predate the password column and have `NULL` passwords — they cannot log in.

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
```

---

## Key design decisions (and why)

- **Double-entry ledger** — every payment appends two balancing rows (CREDIT to merchant,
  DEBIT from gateway, equal amounts). Balances are never stored; they're the sum of entries.
  Append-only, so the trail is tamper-evident.
- **Money as integer paise** (`Long`), never decimals. ₹500 = 50000.
- **`@Transactional` on payment creation** — the payment row and both ledger entries commit
  together or not at all.
- **Server-controlled fields** — id, status, createdAt, role **and merchantId** are set by the
  server, never by the client. A client cannot choose its own identity any more than its own id.
- **Layered architecture** — controller (HTTP only) → service (business rules) → repository.
  Controllers read `Authentication` and pass plain values down, so services stay free of
  Spring Security types and testable without a security context.
- **API keys prefixed `tk_`** — like Stripe's `sk_`, Razorpay's `rzp_`.
- **Two authentication doors on purpose** — API keys for machines (permanent, one DB lookup
  per request), JWT for humans (1 hour, carries a role, verified locally with no DB hit).
  Trade-off: a JWT can't be revoked before it expires, hence the short TTL.
- **BCrypt for passwords**, cost 10 — deliberately slow, auto-salted, `WRITE_ONLY` on the field.
- **Login returns the same error for both failure modes** — "Invalid email or password"
  whether the email is unknown or the password wrong. Distinct messages leak which accounts
  exist (email enumeration).
- **Three layers of authorization** — authentication (who), roles (what kind of user),
  ownership (is this yours). Roles do nothing for the third question.
- **Foreign resources return 404, not 403** — a 403 confirms the ID is real and lets an
  attacker enumerate. Both throws use an identical message, which is what makes it work.
  Stripe and GitHub do the same.
- **Fetch-then-check for one, scope-the-query for many** — `getById` loads and compares owners;
  `getAllForCaller` picks a different query so unauthorised rows never load at all.
- **The ownership rule lives in one method** — `getLedgerForPayment` calls `getById` and
  discards the result, rather than duplicating the condition.
- **Two state stores** — Postgres for the truth, Redis for facts that expire. TTL means no
  cleanup job exists anywhere in the codebase.
- **Idempotency keys on `POST /payments`** — atomic `SET NX EX`, scoped per merchant, 24h TTL.
  A retry returns the original payment; an in-flight duplicate gets 409.
- **Idempotency orchestration is in the controller** — because self-invocation would bypass
  Spring's `@Transactional` proxy.
- **Rate limiting via `INCR`** on a key containing the current minute, so the window resets by
  itself when the key name changes.

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

- Lombok needs **annotation processing enabled** in IntelliJ or its generated methods appear missing
- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- **A correctly-spelled YAML key in the wrong place is silently ignored** — a misplaced
  `data: redis:` fell back to defaults that happened to match, so nothing appeared wrong
- `.gitignore` only blocks **untracked** files; use `git rm -r --cached` for already-tracked ones
- Repositories seemingly missing `save()`/`findById()` = **stale IntelliJ index** —
  Maven Reload + Rebuild, or Invalidate Caches
- Artifact **names** change between major Spring versions, not just numbers
- 401 = "who are you"; 403 = "not permitted"; 409 = "conflicts with current state";
  429 = "too fast"
- **Tables are plural** — `merchants`, `payments`, `ledger_entries`. `\dt` lists them.
- **`ddl-auto: update` cannot add a NOT NULL column** to a table with existing rows. Fix:
  `ADD COLUMN IF NOT EXISTS` (nullable) in psql, backfill with `UPDATE`, then restart.
  This is what Flyway exists for.
- **jjwt 0.12 was a breaking release** — `setSubject`/`setExpiration`/`setSigningKey`/
  `parseClaimsJws` became `subject`/`expiration`/`verifyWith`/`parseSignedClaims`.
- **`hasRole("ADMIN")` looks for an authority literally named `ROLE_ADMIN`** — Spring prepends
  the prefix when checking, so you must write it when creating.
- **In `authorizeHttpRequests`, first match wins.** But `@GetMapping` resolves by **pattern
  specificity**, not declaration order. Two config systems, two rules — don't confuse them.
- **Self-invocation defeats Spring's proxies** — a `@Transactional` method called from another
  method on the same bean runs with no transaction, silently.
- **Filters run before the DispatcherServlet**, so `@RestControllerAdvice` cannot catch what
  they throw — write the response by hand.
- **Two IntelliJ import traps:** `lombok.Value` above Spring's `@Value`, and `java.sql.Date`
  above `java.util.Date`. Also **"Add static import" ≠ "Import class"**.
- **A method declared inside another method's body** cascades into a huge error count.
  A big error count usually means *one* structural problem — fix the earliest and the rest go.
- **Don't keep two overloads that make different guarantees.**
- **IntelliJ shows fake errors in `notes.md`** — it injects a Java parser into ```java code
  fences and the fragments don't compile. Markdown doesn't compile; ignore them.
- **A sudden 403 on a request that worked minutes ago = check the token age first.**
  Hit this twice; both times the token had passed its one-hour expiry.
- **Store the JWT in a shell variable** (`TOKEN='...'` on its own line, single quotes) rather
  than editing long curl lines — two test runs failed from mangling the token inline.
- **`cp` to a different filename case does nothing on macOS** — the filesystem is
  case-insensitive. Use `git mv` for case-only renames.
- **macOS numbers repeat downloads** — `~/Downloads/notes.md` may be weeks old while the new
  one is `notes_4.md`. Always run `ls -lt ~/Downloads/*.md | head -5` and check today's
  timestamp before copying, or you'll silently overwrite new work with old.
- Watch for **dictation landing in an open file** instead of the chat box. Happened twice.

---

## What's next

- **Day 11 — automated tests.** The biggest gap in the project. Every Postman scenario from
  Days 8–10 should be a JUnit test.
- Then: Flyway migrations, pagination, hashed API keys
- Then: Docker + docker-compose (whole stack in one command)
- Then: first real deployment — Railway or Render, managed Postgres and Redis, HTTPS
- Then: Kafka (payment events, webhook delivery with retries + DLQ), bank simulator,
  Kubernetes as a deliberate exercise, Spring AI

Also outstanding:

- **Unauthenticated traffic isn't rate limited** — the filter guards on an existing identity,
  so brute-forcing `/auth/login` hits no ceiling. Production adds an IP-keyed limiter.
- **Fixed-window rate limiting allows a boundary burst** — twenty either side of a minute
  boundary is forty in two seconds.
- **Idempotency keys aren't fingerprinted against the request body** — reusing a key with a
  different amount returns the original payment. Stripe returns 422 instead.
- **No pagination** on `GET /api/v1/payments`. Fix is `Page<Payment>` + `Pageable`.
- **API keys are still plain text.** Hashing isn't symmetric with passwords: verifying a key
  means *finding* the row, and a hash can't be indexed. Fix is a lookup prefix —
  `tk_live_<lookup>_<secret>`, index the lookup half, hash only the secret half.
- `POST /api/v1/merchants` returns 200; REST convention is 201 + `Location` header.
- Password changes have no endpoint. `MerchantService.update()` deliberately ignores the field.
- No merchant-scoped ledger listing — entries are reachable only via their parent payment.
- No `Retry-After` header on 429s.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, currently **v1.0**
- `docs/UNDERSTANDING.md` — from-scratch primer: what a gateway is, HTTP and Postman from
  zero, credentials explained, day-by-day reasoning, interview narrative, deployment roadmap
- `docs/CONTEXT.md` — this file