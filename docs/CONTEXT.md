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
  - ApiKeyFilter = the ID-card reader at the staff entrance — it now reads the employee
    number printed on the card to pull one personnel file, then checks the magnetic strip
    against the fingerprint on record. It no longer keeps a copy of the strip.
  - JWT = the wristband the front desk issues after you show ID
  - Redis = the notepad by the till — instantly checkable, rebuildable if lost
  - Flyway = the renovation logbook — every change to the building is a numbered, dated entry

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
**Before running the app:** `lsof -ti :8080 | xargs kill` — a leftover instance is the usual
cause of "port already in use", and a failed startup means Flyway never ran.

---

## Tech stack (versions matter!)

- **Spring Boot 3.4.1** — was 4.1.0, downgraded because no matching security starter existed
- Maven, Java 21, YAML config (`application.yaml`)
- Dependencies: spring-boot-starter-**web** (not webmvc), validation, data-jpa, security,
  **data-redis**, devtools, postgresql, lombok, spring-boot-starter-**test** (not webmvc-test),
  **flyway-core** and **flyway-database-postgresql** (the second is mandatory from Flyway 10 —
  Postgres support moved out of core)
- **jjwt 0.13.0** — three artifacts: `jjwt-api` (compile), `jjwt-impl` (runtime),
  `jjwt-jackson` (runtime). Needs explicit `<version>` tags; the parent POM doesn't manage it.
  Spring starters need no version tag.
- **`ddl-auto: validate`** (was `update` until Day 12a — Flyway owns the schema now),
  `show-sql: true`, `open-in-view: false`
- `spring.flyway.baseline-on-migrate: true`, `baseline-version: 1` — for the dev database,
  which already had tables before migrations existed. The **test** profile overrides this to
  `false` on purpose, so a non-empty test schema fails loudly rather than silently skipping V1.
- `spring.data.web.pageable.max-page-size: 100` — nested inside the existing `data:` block,
  as a sibling of `redis:`
- `jwt.secret` via `${JWT_SECRET:default}` (32-char minimum for HS256), `jwt.expiration-ms: 3600000`
- `ratelimit.requests-per-minute: ${RATE_LIMIT_PER_MINUTE:20}`

---

## Current state: Day 12 complete

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
| 11 | Integration test suite — 19 tests covering auth, ownership, idempotency, rate limiting |
| 12a | **Flyway migrations replace `ddl-auto`** — V1 initial schema, V2 query indexes |
| 12b | **Pagination on `GET /payments`** — `Page` + `Pageable`, capped size, stable JSON shape |
| 12c | **API keys stored hashed** — lookup prefix + SHA-256, migrated via expand/contract |

Architecture doc is at **v1.2**. All work committed and pushed.

**Test accounts (dev database):** `test@shop.com` / `hunter2` = ADMIN ·
`regular@shop.com` / `hunter2` = MERCHANT. The remaining `priya@` merchants predate the
password column and cannot log in. The test suite creates and rolls back its own merchants.

**API keys are now unrecoverable.** They exist only in the response to the request that created
the merchant. There is no way to look one up. To test the API-key path, create a merchant and
keep the key from that response.

---

## Package structure

```
com.transakt.transakt
├── auth/       JwtService, JwtAuthFilter, AuthService, AuthController,
│               LoginRequest, LoginResponse
├── common/     GlobalExceptionHandler, ResourceNotFoundException,
│               InvalidCredentialsException, IdempotencyConflictException,
│               ApiKeyFilter, ApiKeyHasher, RateLimitFilter, SecurityConfig,
│               PasswordConfig, WebConfig, IdempotencyService, RateLimitService
├── merchant/   Merchant (entity), MerchantRole (enum), MerchantRepository,
│               MerchantService, MerchantController
├── payment/    Payment, PaymentStatus (enum), CreatePaymentRequest (DTO),
│               PaymentRepository, PaymentService, PaymentController
├── ledger/     LedgerEntry, EntryDirection (enum), LedgerEntryRepository
├── HealthController
└── TransaktApplication

src/main/resources/db/migration
├── V1__initial_schema.sql          the schema as it stood after Day 11
├── V2__add_query_indexes.sql       idx_payments_merchant_id, idx_ledger_entries_payment_id
├── V3__hash_api_keys.sql           expand: prefix + hash columns, backfilled
└── V4__drop_plaintext_api_key.sql  contract: NOT NULL, then DROP COLUMN api_key

src/test/java/com/transakt/transakt
├── TransaktApplicationTests      smoke test — the context loads
├── AuthIntegrationTest           6 tests
├── OwnershipIntegrationTest      5 tests
├── IdempotencyIntegrationTest    5 tests
└── RateLimitIntegrationTest      2 tests

src/test/resources/application-test.yaml    the `test` profile
```

**Never edit an applied migration.** Flyway stores a checksum; editing V1 after it has run
breaks every subsequent startup. The schema is wrong? Write V5.

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
- **Two authentication doors** — API keys for machines, JWT for humans (1 hour, carries a role,
  no DB hit). Trade-off: JWTs can't be revoked early.
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
- **Tests are integration tests, deliberately** — the interesting behaviour lives in the wiring,
  and a mocked unit test would pass while the security config was wide open.
- **The schema is versioned, not inferred** — Flyway owns it, Hibernate only validates. Every
  change is a reviewable file rather than something that happened on one laptop.
- **API keys are split into a public prefix and a private hash** — you cannot look up a salted
  hash. A password lookup has an email to find the row first; an API key *is* the identity, so
  a salted hash would mean comparing against every row. An indexed prefix plus one SHA-256
  comparison is constant time. Same shape as Stripe's `sk_live_` and GitHub's `ghp_`.
- **SHA-256 rather than BCrypt for keys** — slow hashing defends against guessable inputs, and
  a 256-bit random key is not guessable at any speed.
- **Shown once** — `Merchant.apiKey` is `@Transient`, so it is serialised into the creation
  response and stored nowhere.
- **Schema changes use expand/contract** — add nullable, dual-write, switch readers, then
  enforce and drop. Writers before readers; `NOT NULL` belongs to contract, not expand.
- **Page size is capped server-side** — without `max-page-size`, `?size=999999` is a supported
  request and pagination is decorative.

---

## Endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/health` | open |
| POST | `/api/v1/auth/login` | open |
| POST | `/api/v1/merchants` | open (bootstrap: signup) — response carries the API key **once** |
| GET/PUT/DELETE | `/api/v1/merchants/**` | requires `ROLE_ADMIN` |
| POST | `/api/v1/payments` | authenticated; merchant from the token; optional `Idempotency-Key` |
| GET | `/api/v1/payments` | authenticated; **scoped to the caller**, all rows for admins; **paginated** |
| GET | `/api/v1/payments/{id}` | authenticated; **404 unless yours or admin** |
| GET | `/api/v1/payments/{id}/ledger` | authenticated; **404 unless yours or admin** |

`GET /api/v1/payments` accepts `?page=`, `?size=` (max 100) and `?sort=`, defaulting to 20 per
page sorted by `createdAt` descending. The response is
`{"content": [...], "page": {"size", "number", "totalElements", "totalPages"}}`.

All authenticated endpoints are rate limited to 20 requests per merchant per minute.

---

## Gotchas already hit (don't repeat these)

**Build and tooling**

- Lombok needs **annotation processing enabled** in IntelliJ
- **Lombok also breaks on a too-new JDK** — `TypeTag :: UNKNOWN` means Maven is using a
  different JDK from IntelliJ. Check `./mvnw -version` against the project SDK.
- **A missing `package` line is catastrophic and looks like something else entirely.** A class
  whose declared package doesn't match its folder makes javac report `duplicate class` — and
  because Lombok is an *annotation processor*, that early failure aborts processing, so every
  Lombok-generated getter in the project vanishes at once. One missing line produced ~100
  "cannot find symbol" errors in four unrelated files. **Always read the first errors:**
  `./mvnw compile 2>&1 | grep "ERROR.*\.java:" | head -20`
- **A big error count usually means *one* structural problem** — fix the earliest one
- Repositories seemingly missing `save()`/`findById()` = **stale IntelliJ index**
- Artifact **names** change between major Spring versions, not just numbers
- **IntelliJ import traps:** `lombok.Value` above Spring's `@Value`, `java.sql.Date` above
  `java.util.Date`, `java.awt.print.Pageable` above `org.springframework.data.domain.Pageable`,
  `java.beans.Transient` above `jakarta.persistence.Transient`. Also **"Add static import" ≠
  "Import class"**, and it offers `RequestEntity.post` instead of `MockMvcRequestBuilders.post`.
- **`src/main` and `src/test` are separate compilation units.** A test class under `src/main`
  fails with `cannot find symbol: class Test`.

**Config**

- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- **A correctly-spelled YAML key in the wrong place is silently ignored** — a misplaced
  `data: redis:` fell back to defaults that happened to match, so nothing appeared wrong
- New keys go **inside** existing blocks: `web:` is a sibling of `redis:` under `data:`

**JPA and Spring**

- **Tables are plural** — `merchants`, `payments`, `ledger_entries`
- **`ddl-auto: update` cannot add a NOT NULL column** to a populated table
- **`save()` calls `merge()`, not `persist()`, when the entity already has an ID** — and
  `merge()` copies only *persistent* state onto a new instance and returns that. `@Transient`
  fields silently do not survive. Re-set them on the returned object.
- **Self-invocation defeats Spring's proxies** — a `@Transactional` method called from another
  method on the same bean runs with no transaction, silently
- **Filters run before the DispatcherServlet**, so `@RestControllerAdvice` cannot catch what
  they throw — write the response by hand
- **`@Transactional` rolls back Postgres, not Redis.** Test isolation is per-store.
- **Serialising `Page` directly publishes `PageImpl`'s internals** as your API contract

**Security**

- 401 = "who are you"; 403 = "not permitted"; 409 = "conflicts with current state";
  429 = "too fast"
- **jjwt 0.12 was a breaking release** — `verifyWith`/`parseSignedClaims` replaced the old API
- **`hasRole("ADMIN")` looks for an authority literally named `ROLE_ADMIN`**
- **`authorizeHttpRequests` is first-match-wins**, but `@GetMapping` resolves by pattern
  specificity — two config systems, two rules
- **A sudden 403 on a request that worked minutes ago = check the token age first**
- **Generate a credential once, into a variable.** Calling the generator again for a derived
  value produces a *different* credential — the stored prefix and hash would belong to a key
  nobody ever received, and every column would look correctly populated.

**Migrations**

- **Flyway runs at application startup**, so a failed boot performs no migration at all
- **`pg_dump` on PostgreSQL 18 emits `\restrict` and `\unrestrict`** psql meta-commands. Flyway
  isn't psql — it sends statements over JDBC, so a backslash line is a syntax error. The closing
  one sits *below* "dump complete" and looks like a footer.
- **`ls` the migration folder before running.** A file that only exists in an editor tab makes
  Flyway report success while doing nothing.
- **Never edit an applied migration** — checksums

**Terminal and workflow**

- **Store the JWT in a shell variable** rather than editing long curl lines
- **`curl -s ... | python3 -m json.tool` hides everything you need.** "Expecting value: line 1
  column 1" means the body was *empty*, not malformed — `-s` swallows curl's error and the pipe
  swallows the status line. Use `curl -i` with no pipe.
- **Check the shell prompt before any relative path.** Two windows, one at `~` and one at
  `transakt`, has cost several round trips.
- **`lsof -ti :8080 | xargs kill`** before starting the app
- **`git add` before `mv` stages the old paths** — re-run `git add -A src/`
- **`cp` to a different filename case does nothing on macOS** — use `git mv`
- **macOS numbers repeat downloads** — `~/Downloads/notes.md` may be weeks old. Run
  `ls -lt ~/Downloads/*.md | head -5` first. Better: paste file contents directly from chat.
- **IntelliJ shows fake errors in `notes.md`** — it injects a Java parser into ```java fences.
- Watch for **dictation landing in an open file or on the command line** instead of the chat box

---

## What's next

- **Day 13 — Docker + docker-compose.** Whole stack in one command; ends the manual
  `redis-server` tab.
- **Day 14 — first real deployment.** Railway or Render, managed Postgres and Redis, HTTPS.
  Flyway is what makes this possible: the schema builds itself on a fresh database.
- Then: Kafka (payment events, webhook delivery with retries + DLQ), bank simulator,
  Kubernetes as a deliberate exercise, Spring AI

Also outstanding:

- **No CI** — the suite runs locally but nothing runs it on push. This is the biggest gap now.
- `OwnershipIntegrationTest` survived the list endpoint changing from a JSON array to an object,
  which means its assertions aren't structural. Worth strengthening.
- **The API key prefix carries ~20 bits of entropy** — `tk_` eats three of eight characters.
  Collisions become plausible near a thousand merchants, and the unique index would then reject
  legitimate signups. Fix belongs in the key format: a longer dedicated random segment.
- **There are no foreign key constraints** — `merchantId` and `paymentId` are plain scalar
  columns, not JPA associations, so Hibernate never generated any. Only the service layer stops
  a payment referencing a merchant that doesn't exist.
- **Signup accepts a merchant with no password**, which can then never log in. `@NotBlank`.
- Unauthenticated traffic isn't rate limited; no IP-based limiter
- Fixed-window rate limiting allows a boundary burst
- Idempotency keys aren't fingerprinted against the request body (Stripe returns 422)
- Offset pagination degrades with depth; cursors are the standard fix
- `POST /api/v1/merchants` returns 200, not 201; no password-change endpoint;
  no `Retry-After` header on 429s
- A Spring Security warning about a generated password and an `inMemoryUserDetailsManager`
  appears at startup — harmless, but odd for an app with its own two auth doors. Unexamined.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, currently **v1.2**
- `docs/UNDERSTANDING.md` — from-scratch primer: what a gateway is, HTTP and Postman from
  zero, credentials explained, day-by-day reasoning, interview narrative, deployment roadmap
- `docs/CONTEXT.md` — this file