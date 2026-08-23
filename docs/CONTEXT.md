# Transakt — Context File

Paste this into a new chat to restore full project context.

---

## What this project is

Transakt is a payment gateway built from scratch in Java — my own Razorpay.
Online shops call one simple API instead of dealing with banks directly.
The bank is simulated; no real money moves.

**Repo:** github.com/ARV0007/transakt
**Live:** https://transakt.onrender.com

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
  - ApiKeyFilter = the ID-card reader at the staff entrance — it reads the employee
    number printed on the card to pull one personnel file, then checks the magnetic strip
    against the fingerprint on record. It keeps no copy of the strip.
  - JWT = the wristband the front desk issues after you show ID
  - Redis = the notepad by the till — instantly checkable, rebuildable if lost
  - Flyway = the renovation logbook — every change to the building is a numbered, dated entry
  - Docker = the restaurant packed into a shipping container, kitchen and all
  - Render = the plot of land in Singapore the container was set down on

---

## My environment

- MacBook Air (Apple Silicon), macOS
- Java 21 (Temurin) — project SDK must be **temurin-21**, never 26.
  `JAVA_HOME` is pinned in `~/.zshrc` because Maven otherwise picks up Java 26 and Lombok breaks.
- IntelliJ IDEA
- PostgreSQL 18 via Postgres.app, port 5432, databases `transakt` and `transakt_test`
- **`psql` is not on the PATH** — binaries at `/Applications/Postgres.app/Contents/Versions/latest/bin/`
- **Redis 8.10 via Homebrew.** `brew services start redis` **fails** on this machine — run
  `redis-server` in a dedicated Terminal tab instead. *Or, since Day 13, just use compose.*
- **Docker Desktop** — must be *running*, not just installed (see gotchas)
- Postman for API testing
- Project path: `~/Documents/Coding/transakt` · alias `tk` in `~/.zshrc` (new tabs only)

**To run everything locally:** `docker compose up` — Postgres, Redis and the app, one command.
**To run the tests:** Redis and Postgres up, then `./mvnw test`. Expect 19 tests, ~20 seconds.
**Before running the app bare:** `lsof -ti :8080 | xargs kill` — a leftover instance is the usual
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
- **`ddl-auto: validate`** (Flyway owns the schema since Day 12a), `show-sql: true`,
  `open-in-view: false`
- `spring.flyway.baseline-on-migrate: true`, `baseline-version: 1` — for the dev database,
  which already had tables before migrations existed. The **test** profile overrides this to
  `false` on purpose, so a non-empty test schema fails loudly rather than silently skipping V1.
- `spring.data.web.pageable.max-page-size: 100` — nested inside the existing `data:` block
- **Everything external is parameterised** (Day 13/14):
  `${DB_HOST:localhost}`, `${DB_NAME:transakt}`, `${DB_USER:aman}`, `${DB_PASSWORD:}`,
  `${REDIS_HOST:localhost}`, `${REDIS_PORT:6379}`, `${REDIS_PASSWORD:}`,
  `${PORT:8080}` (top level, sibling of `spring:`),
  `${JWT_SECRET:default}` (32-char minimum for HS256), `jwt.expiration-ms: 3600000`,
  `ratelimit.requests-per-minute: ${RATE_LIMIT_PER_MINUTE:20}`
  The defaults keep the Mac working; compose and Render supply real values.

---

## Current state: Day 14 complete — deployed and live

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
| 13 | **Docker + docker-compose** — multi-stage build, three services, healthchecks |
| 14 | **Deployed to Render** — managed Postgres and Key Value, HTTPS, public URL |

Architecture doc is at **v1.3**. All four docs cover Days 13–14.

**Verified working in production (23 Aug):**
`GET /health` 200 · signup 200 · login 200 · `GET /payments` 200 (paginated, scoped) ·
`POST /payments` 201 CAPTURED · idempotent replay returns the same payment ·
25-request loop returns 19×200 then 6×429.

**Test accounts (Render database):** `third@shop.com` / `hunter2` and
`keytest@shop.com` / `hunter2`, both MERCHANT. There is **no ADMIN on Render** — role is
server-controlled at signup, so `/api/v1/merchants/**` returns 403 there. That is correct,
not a bug.

**Test accounts (local dev database):** `test@shop.com` / `hunter2` = ADMIN ·
`regular@shop.com` / `hunter2` = MERCHANT. The `priya@` merchants predate the password
column and cannot log in. The test suite creates and rolls back its own merchants.

**API keys are unrecoverable.** They exist only in the response to the request that created
the merchant. To test the API-key path, create a merchant and keep the key from that response.

---

## Deployment (Render, free tier)

| Resource | Name | Details |
|---|---|---|
| Web Service | `transakt` | `srv-da2dip3m8hqs73em2g70`, Docker, Free, Singapore, `main`, Auto-Deploy on |
| Postgres | `transakt-db` | PG 18, Singapore, Free — **expires 17 Sept 2026** |
| Key Value | `transakt-redis` | **Valkey 8**, Singapore, Free, `allkeys-lru`, `red-da4tkjrncjis73f3t9q0` |

**Env vars on the web service:** `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
`JWT_SECRET`, `REDIS_HOST`, `REDIS_PORT`. **No `REDIS_PASSWORD`** — internal authentication
is off, so internal traffic needs none. Health check path `/api/v1/health`.

**Things to know about the free tier:**
- The web service **spins down after 15 minutes idle**. First request after that has taken up
  to **2 minutes**, not the 50 seconds Render advertises. Say so when sharing the link.
- Postgres **expires 30 days after creation**, then 14 days grace, then permanent deletion.
  Loss would be demo data, not the ability to run — the schema rebuilds from four migrations.
- One Key Value instance per workspace. This matters (see gotchas).
- Postgres defaults to inbound `0.0.0.0/0`; Key Value defaults to blocking all external
  traffic. Two data stores, two security postures, neither chosen deliberately.

**Dashboard layout:** the web service sits under **Ungrouped Services** on the workspace
overview, while the database and Key Value live **inside the "My project" card**. Click the
project card to find them.

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

project root
├── Dockerfile              multi-stage: maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre
├── .dockerignore           target/, .git/, .idea/, *.iml, .DS_Store, docs/
└── docker-compose.yml      postgres:18 + redis:8-alpine + app, healthchecked

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
- **Two authentication doors** — API keys for machines, JWT for humans (1 hour, carries a role,
  no DB hit). Trade-off: JWTs can't be revoked early.
- **Three layers of authorization** — authentication, roles, ownership.
- **Foreign resources return 404, not 403** — a 403 confirms the ID is real. Both cases use an
  identical message, which is what makes it work.
- **Fetch-then-check for one, scope-the-query for many.**
- **Two state stores** — Postgres for the truth, Redis for facts that expire. TTL means no
  cleanup job exists anywhere in the codebase.
- **Idempotency keys on `POST /payments`** — atomic `SET NX EX`, scoped per merchant, 24h TTL.
- **Idempotency orchestration is in the controller** — because self-invocation would bypass
  Spring's `@Transactional` proxy.
- **Rate limiting via `INCR`** on a key containing the current minute, so the window resets by
  itself when the key name changes. The counter is per merchant per minute across **all**
  authenticated endpoints, not per endpoint.
- **Tests are integration tests, deliberately** — the interesting behaviour lives in the wiring.
- **The schema is versioned, not inferred** — Flyway owns it, Hibernate only validates.
- **API keys are split into a public prefix and a private hash** — you cannot look up a salted
  hash. An indexed prefix plus one SHA-256 comparison is constant time. Same shape as Stripe's
  `sk_live_` and GitHub's `ghp_`.
- **SHA-256 rather than BCrypt for keys** — slow hashing defends against guessable inputs, and
  a 256-bit random key is not guessable at any speed.
- **Shown once** — `Merchant.apiKey` is `@Transient`, serialised into the creation response
  and stored nowhere.
- **Schema changes use expand/contract** — add nullable, dual-write, switch readers, then
  enforce and drop.
- **Page size is capped server-side** — without `max-page-size`, `?size=999999` is a supported
  request and pagination is decorative.
- **The image is built in two stages** — Maven compiles in the first, only the JRE and the jar
  survive into the second. `COPY pom.xml` and `mvn dependency:go-offline` come *before*
  `COPY src`, so the dependency layer caches; it took 154.9s once and zero since.
- **Config is environment-shaped, not environment-specific** — every external address is
  `${VAR:sensible-default}`. One artifact runs on the Mac, in compose, and on Render.

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
  "Import class"**.
- **`src/main` and `src/test` are separate compilation units.**

**Config**

- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- **A correctly-spelled YAML key in the wrong place is silently ignored**
- New keys go **inside** existing blocks: `web:` is a sibling of `redis:` under `data:`

**JPA and Spring**

- **Tables are plural** — `merchants`, `payments`, `ledger_entries`
- **`ddl-auto: update` cannot add a NOT NULL column** to a populated table
- **`save()` calls `merge()`, not `persist()`, when the entity already has an ID** — and
  `merge()` copies only *persistent* state onto a new instance. `@Transient` fields silently
  do not survive. Re-set them on the returned object.
- **Self-invocation defeats Spring's proxies**
- **Filters run before the DispatcherServlet**, so `@RestControllerAdvice` cannot catch what
  they throw — write the response by hand
- **`@Transactional` rolls back Postgres, not Redis.** Test isolation is per-store.
- **Serialising `Page` directly publishes `PageImpl`'s internals** as your API contract
- **A `CommandLineRunner` that throws kills the application.** It runs *after* the context
  refreshes and Tomcat binds, so everything looks like it worked — then
  `SpringApplication.run` fails, closes the context and exits 1. Six lines of debug
  scaffolding that pinged Redis at startup cost three days of failed deploys.

**Security**

- 401 = "who are you"; 403 = "not permitted"; 409 = "conflicts with current state";
  429 = "too fast"
- **jjwt 0.12 was a breaking release** — `verifyWith`/`parseSignedClaims` replaced the old API
- **`hasRole("ADMIN")` looks for an authority literally named `ROLE_ADMIN`**
- **`authorizeHttpRequests` is first-match-wins**, but `@GetMapping` resolves by pattern
  specificity — two config systems, two rules
- **A sudden 403 on a request that worked minutes ago = check the token age first**
- **Generate a credential once, into a variable.** Calling the generator again for a derived
  value produces a *different* credential.
- **A JSESSIONID on a 403 proves nothing on its own.** In Spring Security 6 the context is
  saved to a session when a filter *sets* an Authentication, so the cookie appears whether
  auth succeeded or failed. Don't read it as evidence either way.

**Docker**

- **Docker CLI ≠ Docker daemon.** `docker --version` and `docker compose version` are local
  binaries that never contact the engine, so they pass while Docker Desktop isn't running.
  `docker info` is the command that proves the daemon is up.
- **Postgres 18 changed its data directory layout.** A volume mounted at
  `/var/lib/postgresql/data` is reported as an "unused mount/volume" and the container exits 1.
  Correct config for 18+ is a single mount at `/var/lib/postgresql`, then `docker compose down -v`
  to clear the half-initialised volume.
- **`docker compose logs <service>` isolates one container.** Interleaved output buried a
  fatal Postgres error under Redis's startup banner.
- **IntelliJ creates files relative to the selected Project panel node** — `docker-compose.yml`
  first landed inside `target/`. Click the root node first.
- **Service names are hostnames** inside the compose network. Inside the app container,
  `localhost` means the app container.
- **`depends_on` alone waits for the container to exist, not for Postgres to accept
  connections.** Use healthchecks with `condition: service_healthy`.

**Deployment**

- **Render's "Exited with status 1" is a wrapper, never a cause.** The deploy log holds the
  reason. Search it for `ERROR`, then `Caused by` — don't scroll it.
- **Read a Spring stack trace bottom-up.** The last `Caused by:` is the truth.
- **`x-render-routing: no-deploy` means the service has never had a successful deploy.**
  That mystery HTML page was Render's own 502, not the app.
- **Render deploys from GitHub.** An unpushed change doesn't exist to it. Verify with
  `git show origin/main:path/to/file`, not the copy on disk.
- **Docker Build Context Directory is a folder (`.`), Dockerfile Path is a file
  (`./Dockerfile`).** Pointing the context at a file gives `invalid local: ... not a directory`.
- **The free tier allows one Key Value instance per workspace.** An instance stuck on
  "Creating" holds the slot, which is why a third attempt won't even start. **Delete first,
  then create** — that alone fixed a blocker that had lasted days.
- **Both auth doors failing identically points downstream of both**, not at a coincidence in
  each. Testing the API-key path and the JWT path separately is what split the problem.
- **`REDIS_PASSWORD` should be absent, not blank**, when internal authentication is off.

**Terminal and workflow**

- **Store credentials in a shell variable**, never on screen:
  `TOKEN=$(curl -s ... | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")`
  then `echo ${#TOKEN}` to check the length without revealing it.
- **`openssl rand -base64 48 | tr -d '\n' | pbcopy`** — the `tr -d '\n'` matters; a trailing
  newline makes a secret look right on screen and silently not match.
- **`curl -sS -D - -o /dev/null <url>`** shows headers only. A flooded 80×24 window pushes the
  status line off the top.
- **`curl -s ... | python3 -m json.tool` hides everything you need.** "Expecting value: line 1
  column 1" means the body was *empty*. Use `curl -i` with no pipe.
- **A shell reads `~/.zshrc` once, at startup.** A tab opened before an alias was added won't
  know it. Cmd+T, or `source ~/.zshrc`.
- **Paste one command at a time.** Pasting a block makes zsh interleave the output.
- **Check the shell prompt before any relative path.**
- **`lsof -ti :8080 | xargs kill`** before starting the app
- **`git add` photographs a file at that instant.** Editing afterwards doesn't update the index.
- **`cp` to a different filename case does nothing on macOS** — use `git mv`
- **macOS numbers repeat downloads** — `ls -lt ~/Downloads/*.md | head -5` first.
- **IntelliJ shows fake errors in `notes.md`** — it injects a Java parser into ```java fences.
- Watch for **dictation landing in an open file or on the command line** instead of the chat box

---

## What's next

**Nothing is blocking.** Days 13 and 14 are complete, verified in production, documented and
pushed. The tidy-up from that week is done too: the unused imports removed (`6f1fc81`), the
stray `main` file deleted, and GitHub authentication moved from an expired HTTPS credential to
an ed25519 SSH key — the remote is now `git@github.com:ARV0007/transakt.git`, which doesn't
expire the way a Personal Access Token does.

**Next:**

- **CI** — nineteen tests that run when someone remembers to run them. Nothing runs them on
  push. This is the largest remaining gap in the project, and the thing that makes the test
  suite load-bearing rather than ceremonial. A GitHub Actions workflow with Postgres and Redis
  as service containers.
- **The 403-that-should-be-a-500.** When Redis was unreachable, authenticated requests returned
  an empty-bodied 403 rather than a 500 or a deliberate 503 — something catches the
  `RedisConnectionException` and converts it. Find the catch, then decide deliberately whether
  the rate limiter should **fail open or fail closed**. The answer differs for idempotency,
  where failing open means charging a customer twice.
- Kafka (payment events, webhook delivery with retries + DLQ), bank simulator,
  Kubernetes as a deliberate exercise, Spring AI

**Also outstanding:**

- **The Redis failure surfaced as an empty-bodied 403, not a 500.** Something catches the
  `RedisConnectionException` and converts it — `RateLimitFilter` sits upstream of
  `ExceptionTranslationFilter`, so an uncaught throw should have escaped as a 500. Find the
  catch. The design question underneath: when the rate limiter's store is unreachable, fail
  open or fail closed? Different answer for idempotency, where failing open risks a double
  charge.
- `OwnershipIntegrationTest` survived the list endpoint changing from a JSON array to an
  object, which means its assertions aren't structural.
- **The API key prefix carries ~20 bits of entropy** — `tk_` eats three of eight characters.
  Collisions become plausible near a thousand merchants.
- **No foreign key constraints** — `merchantId` and `paymentId` are plain scalar columns.
- **Signup accepts a merchant with no password**, which can then never log in. `@NotBlank`.
- Unauthenticated traffic isn't rate limited; no IP-based limiter
- Fixed-window rate limiting allows a boundary burst
- Idempotency keys aren't fingerprinted against the request body (Stripe returns 422)
- Offset pagination degrades with depth; cursors are the standard fix
- `POST /api/v1/merchants` returns 200, not 201; no password-change endpoint;
  no `Retry-After` header on 429s
- A Spring Security warning about a generated password and an `inMemoryUserDetailsManager`
  appears at startup — harmless, but odd for an app with its own two auth doors. Unexamined.
- **Flyway warns that PostgreSQL 18.4 is newer than it officially supports.** It validated
  everything anyway. Wants a Flyway bump eventually, not a Postgres downgrade.
- **Free Postgres expires 17 September 2026.** Calendar it with a week's warning.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, currently **v1.3**:
  a deployment topology diagram plus the application internals
- `docs/UNDERSTANDING.md` — from-scratch primer: what a gateway is, HTTP and Postman from
  zero, credentials explained, day-by-day reasoning, interview narrative, deployment roadmap
- `docs/CONTEXT.md` — this file
