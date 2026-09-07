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
- **Never leave a placeholder in a command I'm meant to paste.** `PASTE-HOST-HERE` and
  `ep-YOUR-HOST` both got run literally. Substitute the real value, or say clearly that
  one word needs replacing and what it is.
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
  - Neon = the cold store rented from a different company down the road (since Day 23) —
    same food, but the delivery van now drives on public roads, so it travels locked
  - BankClient = the card machine — the kitchen doesn't care which bank is on the other end
  - Outbox = the order slip spiked next to the till the moment the meal is paid for
  - Kafka = the runner who carries slips from the spike to whoever needs them
  - WebhookConsumer = the person who phones the customer's office to say the order is ready

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
- **Kafka 4.0 in docker-compose** since Day 21. `kafka-topics` / `kafka-console-consumer` CLI
  installed locally via Homebrew for inspection.
- **Docker Desktop** — must be *running*, not just installed (see gotchas)
- Postman for API testing
- Project path: `~/Documents/Coding/transakt` · alias `tk` in `~/.zshrc` (new tabs only)

**Local development does not use Neon.** The Mac and compose both point at a local Postgres.
That is deliberate — a container that starts in two seconds and can be thrown away beats a
shared remote database, and the parameterised config means the same image works against either.

**To run everything locally:** `docker compose up` — Postgres, Redis, Kafka and the app.
**To run the tests:** all three stores up, then `./mvnw test`. Expect 29 tests, ~25 seconds.
**Before running the app bare:** `lsof -ti :8080 | xargs kill` — a leftover instance is the usual
cause of "port already in use", and a failed startup means Flyway never ran.

**Campus network blocks `*.aivencloud.com` at DNS**, including external resolvers. An Aiven Kafka
cluster (`transakt-kafka`, Free-0, Asia Pacific) exists and is Running but is unreachable from
here, which is why Kafka runs in compose instead. **Neon is not blocked** — `nslookup` resolves it
fine, and the deployed app connects without trouble.

---

## Tech stack (versions matter!)

- **Spring Boot 3.4.1** — was 4.1.0, downgraded because no matching security starter existed
- Maven, Java 21, YAML config (`application.yaml`)
- Dependencies: spring-boot-starter-**web** (not webmvc), validation, data-jpa, security,
  **data-redis**, devtools, postgresql, lombok, spring-boot-starter-**test** (not webmvc-test),
  **flyway-core** and **flyway-database-postgresql** (the second is mandatory from Flyway 10 —
  Postgres support moved out of core), **spring-kafka**
- **jjwt 0.13.0** — three artifacts: `jjwt-api` (compile), `jjwt-impl` (runtime),
  `jjwt-jackson` (runtime). Needs explicit `<version>` tags; the parent POM doesn't manage it.
- **Apache Kafka 4.0.0** in compose, **KRaft mode** — no ZooKeeper. Most tutorials still show a
  separate `zookeeper` service; that's the pre-4.x architecture.
- **`ddl-auto: validate`** (Flyway owns the schema since Day 12a), `show-sql: true`,
  `open-in-view: false`
- `spring.flyway.baseline-on-migrate: true`, `baseline-version: 1` — for the dev database,
  which already had tables before migrations existed. The **test** profile overrides this to
  `false` on purpose, so a non-empty test schema fails loudly rather than silently skipping V1.
- `spring.data.web.pageable.max-page-size: 100` — nested inside the existing `data:` block
- `spring.kafka` — a **sibling of `datasource:` and `jpa:`**, not nested inside either.
  `bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`, String serializers,
  **`acks: all`**.
- `outbox.topic: ${OUTBOX_TOPIC:payment.settled}` — top level.
- `@EnableScheduling` on the application class; without it `@Scheduled` is silently inert.
- **Everything external is parameterised** (Day 13/14, extended Day 23):
  `${DB_HOST:localhost}`, `${DB_NAME:transakt}`, `${DB_USER:aman}`, `${DB_PASSWORD:}`,
  **`${DB_SSLMODE:prefer}`**,
  `${REDIS_HOST:localhost}`, `${REDIS_PORT:6379}`, `${REDIS_PASSWORD:}`,
  `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`,
  `${PORT:8080}` (top level, sibling of `spring:`),
  `${JWT_SECRET:default}` (32-char minimum for HS256), `jwt.expiration-ms: 3600000`,
  `ratelimit.requests-per-minute: ${RATE_LIMIT_PER_MINUTE:20}`
  The defaults keep the Mac working; compose and Render supply real values.

The full JDBC URL, since Day 23:

```
jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:transakt}?sslmode=${DB_SSLMODE:prefer}
```

`prefer` tries SSL and falls back if unavailable, so local and compose are untouched.
Render sets `DB_SSLMODE=require`, which removes the fallback. Neon refuses plaintext outright.

**Test profile** (`src/test/resources/application-test.yaml`) — the whole file, because it has
been wrong twice and indentation is the entire story:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/transakt_test
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  data:
    redis:
      database: 1
  flyway:
    baseline-on-migrate: false
  kafka:
    listener:
      auto-startup: false

ratelimit:
  requests-per-minute: 10000

bank:
  decline-rate: 0
  latency:
    min-ms: 0
    max-ms: 0
```

`bank:` is **top level**, a sibling of `ratelimit:` and `spring:`. It was indented two spaces
until Day 23, which nested it under `ratelimit:` and made it invisible.

---

## Current state: Day 23 complete — feature work done, infrastructure settled

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
| 15 | **CI on GitHub Actions** — Postgres and Redis service containers, green on push |
| 16 | **`/error` permitAll** (a 403 was masking every 500); **rate limiter fails open** by design, with the project's first unit test |
| 17 | **Idempotency moved into Postgres** — `UNIQUE (merchant_id, idempotency_key)`, written inside the payment transaction (V5) |
| 18 | **`BankClient` port + `FakeBankClient`**; payment creation split into **two transactions** with the bank call in the gap; ledger entries only on approval; `PENDING` becomes a real state |
| 19 | **`PaymentReconciler`** — sweeps stranded `PENDING` payments, asks the bank, calls the same `settle` (V6 partial index) |
| 20 | **Transactional outbox** — `outbox_events` written in the same transaction as settlement (V7) |
| 21 | **Kafka in compose (KRaft)**, `OutboxPublisher`, `WebhookConsumer`, `KafkaConfig` with retries and a DLT, `merchants.webhook_url` (V8) |
| 22 | **End-to-end verification** of the retry and dead-letter path; docs to v1.6 |
| 23 | **Kafka listener disarmed in tests** so CI no longer needs a broker; **misplaced `bank:` block** in the test profile found and fixed (suite twice as fast, decisions deterministic); **Postgres moved to Neon**, `sslmode` parameterised |

Architecture doc is at **v1.7**.

**Verified working in production (7 Sep, on Neon):**
`GET /health` returns `{"status":"UP","service":"transakt"}` ·
`POST /api/v1/merchants` returns a full merchant with a fresh API key ·
Flyway logs show it connected to `...neon.tech:5432/neondb?sslmode=require`, found no
`flyway_schema_history` table, and created the schema from scratch.

**Verified working locally (6 Sep):** a payment created through the API produced an event on
`payment.settled` keyed by the payment id. With `webhook_url` pointed at a server that rejects
POST, the consumer retried three times two seconds apart and **`payment.settled-dlt` was created**.
Payment `8fbe4ddf-c296-4978-817b-74c42d3a469a`, CAPTURED, 75000 paise.

**The event pipeline does not run on Render** — there is no broker there.

**Test account (Neon / production):** `neon@shop.com` / `hunter2`, MERCHANT,
id `f16de540-0d8a-4119-945a-70219aafeba2`. Its API key is **not recorded here** — this file is
in a public repo. It exists in the local note only. There is **no ADMIN on the deployed
instance** — role is server-controlled at signup, so `/api/v1/merchants/**` returns 403 there.
That is correct, not a bug.

**The pre-Neon Render accounts are gone.** `third@shop.com` and `keytest@shop.com` lived on the
Render database, which was replaced. Recreate with a curl if a demo needs them.

**Test accounts (local dev database, untouched by the migration):**
`test@shop.com` / `hunter2` = ADMIN · `regular@shop.com` / `hunter2` = MERCHANT ·
`outbox-demo@shop.com` / `hunter2` = MERCHANT, with `webhook_url` set to a local endpoint for the
webhook demo. The `priya@` merchants predate the password column and cannot log in. The test
suite creates and rolls back its own merchants.

**API keys are unrecoverable.** They exist only in the response to the request that created
the merchant. To test the API-key path, create a merchant and keep the key from that response.

---

## Deployment

| Resource | Name | Details |
|---|---|---|
| Web Service | `transakt` | Render, `srv-da2dip3m8hqs73em2g70`, Docker, Free, Singapore, `main`, Auto-Deploy on |
| Postgres | **Neon** `transact` | PG 18.6, Singapore, Free — **no expiry**, 0.5 GB, 100 CU-hrs/month, scales to zero after 5 min idle. Host `ep-falling-block-azdxla7q-pooler.c-3.ap-southeast-1.aws.neon.tech`, db `neondb`, user `neondb_owner` |
| Key Value | `transakt-redis` | Render, **Valkey 8**, Singapore, Free, `allkeys-lru`, `red-da4tkjrncjis73f3t9q0` |
| Kafka | — | **none.** No managed Kafka on any Render tier; Aiven is DNS-blocked from campus |

**Env vars on the web service:** `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`,
**`DB_SSLMODE=require`**, `JWT_SECRET`, `REDIS_HOST`, `REDIS_PORT`. **No `REDIS_PASSWORD`** —
internal authentication is off, so internal traffic needs none. **No `KAFKA_BOOTSTRAP_SERVERS`**,
so the deployed app falls back to `localhost:9092`, finds nothing, and accumulates unpublished
outbox rows. Health check path `/api/v1/health`.

**Why the database moved (Day 23).** Render's free Postgres expires 30 days after creation with
a 14-day grace period. Recreating it monthly is a chore with a deletion at the end if forgotten.
Neon's free tier does not expire. The migration was five environment variables, one line of YAML,
and a redeploy — **no data was moved**, because the schema is eight files in version control and
the rows were demo merchants. Flyway rebuilt everything on first boot.

Neon was chosen over Supabase because it is plain Postgres with a connection string. Supabase
bundles auth, storage and a REST layer that this project neither uses nor wants to explain.

**What the move cost:** every query now crosses the public internet under TLS rather than
staying inside Render's Singapore network — safe, slightly slower. And Neon scales compute to
zero after five minutes idle, so the first query after a quiet spell pays a wake-up on top of
Render's own cold start.

**Things to know about the Render free tier:**
- The web service **spins down after 15 minutes idle**. First request after that has taken up
  to **2 minutes**, not the 50 seconds Render advertises. Say so when sharing the link.
- One Key Value instance per workspace. This matters (see gotchas).
- Render's Postgres defaulted to inbound `0.0.0.0/0`; the Key Value store defaults to blocking
  all external traffic. Two data stores, two security postures, neither chosen deliberately.
  Moot now for the database, still true for the Key Value store.

**Dashboard layout:** the web service sits under **Ungrouped Services** on the workspace
overview. The Key Value store lives **inside the "My project" card**. Click the project card
to find it. Neon has its own console at `console.neon.tech`.

---

## Package structure

```
com.transakt.transakt
├── auth/       JwtService, JwtAuthFilter, AuthService, AuthController,
│               LoginRequest, LoginResponse
├── bank/       BankClient (interface: authorize + lookup), BankResult (enum),
│               FakeBankClient (configurable latency + decline rate, remembers
│               its decisions in a ConcurrentHashMap, reset() for tests)
├── common/     GlobalExceptionHandler, ResourceNotFoundException,
│               InvalidCredentialsException, ApiKeyFilter, ApiKeyHasher,
│               RateLimitFilter, SecurityConfig, PasswordConfig, WebConfig,
│               RateLimitService, IdempotencyService, IdempotencyKey (entity),
│               IdempotencyKeyRepository
│               IdempotencyConflictException was DELETED in v1.4 — unreachable
├── merchant/   Merchant (entity, has webhookUrl since V8), MerchantRole (enum),
│               MerchantRepository, MerchantService, MerchantController
├── payment/    Payment, PaymentStatus (PENDING / CAPTURED / FAILED),
│               CreatePaymentRequest (DTO), PaymentRepository,
│               PaymentService (createPending + settle), PaymentProcessor,
│               PaymentReconciler, PaymentController
├── ledger/     LedgerEntry, EntryDirection (enum), LedgerEntryRepository
├── outbox/     OutboxEvent, OutboxEventRepository, OutboxPublisher
├── webhook/    WebhookConsumer, KafkaConfig
├── HealthController
└── TransaktApplication          (@EnableScheduling)

project root
├── Dockerfile              multi-stage: maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre
├── .dockerignore           target/, .git/, .idea/, *.iml, .DS_Store, docs/
└── docker-compose.yml      postgres:18 + redis:8-alpine + apache/kafka:4.0.0 + app

src/main/resources/db/migration
├── V1__initial_schema.sql              the schema as it stood after Day 11
├── V2__add_query_indexes.sql           idx_payments_merchant_id, idx_ledger_entries_payment_id
├── V3__hash_api_keys.sql               expand: prefix + hash columns, backfilled
├── V4__drop_plaintext_api_key.sql      contract: NOT NULL, then DROP COLUMN api_key
├── V5__add_idempotency_keys.sql        table + UNIQUE (merchant_id, idempotency_key), FK to payments
├── V6__add_pending_payments_index.sql  partial: payments (created_at) WHERE status = 'PENDING'
├── V7__add_outbox_events.sql           table + partial index WHERE published_at IS NULL
└── V8__add_merchant_webhook_url.sql    merchants.webhook_url VARCHAR(512), nullable

src/test/java/com/transakt/transakt
├── TransaktApplicationTests           smoke test — the context loads       (1)
├── AuthIntegrationTest                                                     (6)
├── OwnershipIntegrationTest                                                (5)
├── IdempotencyIntegrationTest                                              (5)
├── RateLimitIntegrationTest                                                (2)
├── RateLimitServiceTest               unit — mocked StringRedisTemplate    (1)
├── ApprovedPaymentIntegrationTest                                          (1)
├── DeclinedPaymentIntegrationTest                                          (1)
├── ReconcilerIntegrationTest                                               (2)
├── OutboxIntegrationTest                                                   (2)
└── OutboxPublisherTest                unit — mocked KafkaTemplate          (3)
                                                                    total = 29

src/test/resources/application-test.yaml    the `test` profile
```

**Never edit an applied migration.** Flyway stores a checksum; editing V1 after it has run
breaks every subsequent startup. The schema is wrong? Write V9.

**There are eight migration files, not nine.** Flyway on the *dev* database reports nine
because the baseline marker row counts as a version. `ls` the folder to check reality.
The Neon database, built empty on Day 23, reports eight.

---

## Key design decisions (and why)

- **Double-entry ledger** — every approved payment appends two balancing rows. Balances are the
  sum of entries, never stored. Append-only, so the trail is tamper-evident.
- **Money as integer paise** (`Long`), never decimals. ₹500 = 50000.
- **Ledger entries are written only on approval** (Day 18). A ledger row asserts that money
  moved; a declined payment moved nothing, so its ledger is legitimately empty.
- **Server-controlled fields** — id, status, createdAt, role and **merchantId** are set by the
  server. A client cannot choose its own identity any more than its own id.
- **Layered architecture** — controller (HTTP only) → service (rules) → repository (data).
- **Two authentication doors** — API keys for machines, JWT for humans (1 hour, carries a role,
  no DB hit). Trade-off: JWTs can't be revoked early.
- **Three layers of authorization** — authentication, roles, ownership.
- **Foreign resources return 404, not 403** — a 403 confirms the ID is real. Both cases use an
  identical message, which is what makes it work.
- **Fetch-then-check for one, scope-the-query for many.**
- **Three stores** — Postgres for the truth (including idempotency keys and outbox events),
  Redis for facts that expire, Kafka for events in transit. Kafka is **not** the source of truth;
  the outbox row is what makes losing a message survivable.
- **Idempotency is a unique constraint, not a lock** (Day 17). The key row commits inside the
  same transaction as the payment. Two simultaneous retries race at the database: the second
  blocks, fails with `DataIntegrityViolationException`, and the controller re-reads and returns
  the winner's payment. No `IN_PROGRESS` state, no `release()`, no 409 — all three were
  compensating machinery for a two-phase commit hand-rolled across Redis and Postgres.
- **The catch for that violation must be OUTSIDE the transactional method** — once a constraint
  fires the transaction is rollback-only, so catching inside poisons every subsequent query.
- **Rate limiting via `INCR`** on a key containing the current minute, so the window resets by
  itself when the key name changes. Per merchant per minute across **all** authenticated
  endpoints, not per endpoint.
- **Fail open for the rate limiter, fail safe for idempotency** (Day 16). Same infrastructure,
  same failure mode, opposite correct answers. The catch is narrow —
  `RedisConnectionFailureException`, not `Exception` — so a bug in the key-building code doesn't
  silently disable the limiter.
- **The bank call sits between two transactions** (Day 18). A `@Transactional` method holds a
  pooled connection until commit; an 800ms third-party call inside one drains the pool, and a
  slow bank becomes an unavailable database on endpoints that have nothing to do with banks.
- **`PaymentProcessor` is a separate bean**, not a third method on `PaymentService` —
  self-invocation bypasses Spring's proxy and the `@Transactional` annotations would do nothing.
- **A timeout is not a decline** (Day 19). When `authorize` throws you know you sent the request
  but not whether the bank acted. The payment stays `PENDING`; `PENDING` already means "the
  outcome is not known", so no fourth status was needed.
- **`lookup` returning null means leave it alone.** A transient lookup failure is
  indistinguishable from a genuine absence, so doing nothing is the correct reconciler outcome.
- **The reconciler calls the existing `settle`**, not a copy. Two code paths that both write
  ledger entries is how a ledger comes to disagree with itself.
- **The outbox event commits with the payment** (Day 20). Publishing after commit leaves a window
  where the payment settled and the event was never sent — which here means a merchant is never
  told, so they don't ship, and nothing looks wrong.
- **`published_at` nullable instead of a status column** — null means unpublished, a timestamp
  means published, and you get *when* for free.
- **`aggregate_id`, not `payment_id`** — the outbox doesn't know it holds payment events, so a
  later refund or merchant event uses the same table. No FK, because a log shouldn't break
  because a row was deleted elsewhere.
- **Stamp `published_at` only after the broker confirms** — `send(...).get(10s)` with `acks: all`.
  Stamping first would let a failed send look published, the exact loss the outbox prevents.
- **Key Kafka records by `aggregateId`** — ordering is per partition, not per topic. Without a
  key, records round-robin and a settled-then-refunded sequence could arrive backwards.
- **A failed send breaks the sweep rather than skipping**, for the same ordering reason.
- **At-least-once, knowingly.** If the send succeeds and the process dies before the stamp
  commits, the next sweep republishes. Two systems can't be made atomic; sending twice beats
  losing it.
- **The event carries `merchantId`** so the consumer never queries the producer's database — a
  consumer reading the producer's tables is exactly what events exist to avoid.
- **The listener throws; the error handler owns retries.** `DefaultErrorHandler` +
  `FixedBackOff(2000, 2)` + `DeadLetterPublishingRecoverer`. Retries must be **bounded** (one
  record retrying forever blocks the partition) and failures must **not be dropped** (silence
  means nobody knows) — a DLT satisfies both.
- **`sslmode` is `prefer` by default, `require` in production** (Day 23). `prefer` tries SSL and
  falls back, so one artifact still runs on the Mac, in compose and on Render. A hardcoded
  `require` would break local development; a hardcoded `disable` would break Neon. The variable
  is what keeps both true.
- **The Kafka listener is disarmed in tests** (Day 23). `auto-startup: false` creates the
  listener container and leaves it stopped. Nothing loses coverage — the only publishing test is
  a unit test with a mocked template, and no test covers the consumer at all.
- **Tests are integration tests, deliberately** — the interesting behaviour lives in the wiring.
  The two unit tests exist because both pin behaviour that needs a dependency to **fail on
  demand**, which a mock does cleanly and a real store does not.
- **Decline rate is set explicitly in tests**, never left random. Random is a feature by hand and
  a defect in CI. That explicitness is also what kept the two payment tests correct through three
  days of a silently broken test profile.
- **The schema is versioned, not inferred** — Flyway owns it, Hibernate only validates. Day 23
  proved the value in production: an entire database was rebuilt on a different provider by
  changing environment variables.
- **API keys are split into a public prefix and a private hash** — you cannot look up a salted
  hash. An indexed prefix plus one SHA-256 comparison is constant time. Same shape as Stripe's
  `sk_live_` and GitHub's `ghp_`.
- **SHA-256 rather than BCrypt for keys** — slow hashing defends against guessable inputs, and
  a 256-bit random key is not guessable at any speed.
- **Shown once** — `Merchant.apiKey` is `@Transient`, serialised into the creation response
  and stored nowhere.
- **Schema changes use expand/contract** — add nullable, dual-write, switch readers, then
  enforce and drop. Writers switch **before** readers; `NOT NULL` belongs to contract.
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

**There is no endpoint to set `webhook_url`.** It is set directly in the database:
`UPDATE merchants SET webhook_url = '...' WHERE email = '...';`

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

**Config — the most expensive category in this project**

- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- **A correctly-spelled YAML key in the wrong place is silently ignored. Four occurrences:**
  1. `server.port` nested inside `flyway:` **from Day 14 to Day 22** — never read at all.
     Invisible because Tomcat defaults to 8080 anyway and Render auto-detects the port.
  2. `spring.kafka` pasted inside `jpa.properties.hibernate` — Hibernate ignored it silently.
  3. `bank:` indented under `ratelimit:` in the test profile — Spring read it as
     `ratelimit.bank.decline-rate`, nothing consumed it, and `FakeBankClient` fell back to a
     **random** decline rate and 200–800ms latency on every test. Cost: ~20 seconds a run and
     non-deterministic bank decisions in tests that don't assert on them.
  4. (the same class of thing, watch for the fifth)
- **What hid #3 is the real lesson: a test that doesn't assert on a value won't tell you the
  value is wrong.** The two tests that care about decline rate set it themselves, so they stayed
  correct. Every other test absorbed the randomness without failing.
- **Rule: when adding a key to a YAML file, verify the value is actually read** — not merely
  that the application starts.
- New keys go **inside** existing blocks: `web:` is a sibling of `redis:` under `data:` —
  but `kafka:` is a sibling of `datasource:` under `spring:`, and `outbox:` and `bank:` are
  top level.

**JPA and Spring**

- **Tables are plural** — `merchants`, `payments`, `ledger_entries`, `idempotency_keys`,
  `outbox_events`
- **`ddl-auto: update` cannot add a NOT NULL column** to a populated table
- **`save()` calls `merge()`, not `persist()`, when the entity already has an ID** — and
  `merge()` copies only *persistent* state onto a new instance. `@Transient` fields silently
  do not survive. Re-set them on the returned object.
- **`Payment.id` is application-assigned** (`UUID.randomUUID().toString()` in a field
  initialiser), not `@GeneratedValue`. Tests that build a Payment by hand must set it.
- **`@Enumerated(EnumType.STRING)` is load-bearing for the partial indexes.** Under the ORDINAL
  default the status column would hold integers and `WHERE status = 'PENDING'` would match
  nothing.
- **A partial index is only used when the query's WHERE implies the index's.** A sweep query
  without `status = 'PENDING'` silently gets a sequential scan.
- **Self-invocation defeats Spring's proxies** — the reason `PaymentProcessor` is its own bean.
- **Catching `DataIntegrityViolationException` inside the transactional method poisons it.**
  Once a constraint fires the transaction is rollback-only. Catch in the controller.
- **Narrow that catch without string-matching** — don't parse constraint names out of the
  message. Ask the database whether the key is present now; if not, rethrow.
- **Filters run before the DispatcherServlet**, so `@RestControllerAdvice` cannot catch what
  they throw — write the response by hand
- **`@Transactional` rolls back Postgres, not Redis — and not a field on a singleton bean.**
  `FakeBankClient`'s decision map survives between tests in the same context, so `reset()` in
  `@BeforeEach` is required for the same reason `flushDb()` is.
- **Serialising `Page` directly publishes `PageImpl`'s internals** as your API contract
- **A `CommandLineRunner` that throws kills the application.** It runs *after* the context
  refreshes and Tomcat binds, so everything looks like it worked — then
  `SpringApplication.run` fails, closes the context and exits 1. Six lines of debug
  scaffolding that pinged Redis at startup cost three days of failed deploys.
- **`@EnableScheduling` is required** or `@Scheduled` methods never run and nothing warns.

**Security**

- 401 = "who are you"; 403 = "not permitted"; 409 = "conflicts with current state";
  429 = "too fast"
- **A 403 can hide a 500.** Spring Boot registers the security filter chain for the `ERROR`
  dispatch too, while `OncePerRequestFilter.shouldNotFilterErrorDispatch()` returns true by
  default — so the forward to `/error` arrives unauthenticated and `anyRequest().authenticated()`
  denies it, overwriting the real status. **`.requestMatchers("/error").permitAll()` as the first
  matcher.** Any app with a catch-all `authenticated()` rule needs it.
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

**Kafka**

- **`apache/kafka:4.0.0` runs KRaft — there is no ZooKeeper service.** Tutorials showing one are
  pre-4.x.
- **Two listeners, because a broker hands clients its *advertised* address.** Containers reach it
  at `kafka:29092`, the Mac at `localhost:9092`. One address can't serve both — get it wrong and
  the client connects, is told to go somewhere unreachable, and **hangs** rather than erroring.
- **Spring's dead-letter suffix is `-dlt`, lowercase** — the topic is `payment.settled-dlt`, not
  `.DLT`. Worth knowing when you go looking for it.
- **`@KafkaListener` connects to a real broker in tests.** Fixed Day 23 with
  `spring.kafka.listener.auto-startup: false` in the test profile. Note that CI stayed *green*
  before the fix — a listener that can't reach a broker retries in the background without
  failing the context. It was one timeout from going red, not already red.
- **`acks: all` is what makes the confirmation meaningful.** Without it, `.get()` returns on a
  send the broker may not have durably held.

**Databases and connections**

- **Neon refuses unencrypted connections.** `sslmode=require` in the JDBC URL, or the connection
  is rejected before authentication is even attempted.
- **`sslmode=prefer` is the value that lets one artifact serve three environments** — tries SSL,
  falls back if unavailable.
- **A `HikariPool.checkFailFast` stack trace means the pool couldn't get a *single* connection.**
  Everything below it is "how we got here"; the real reason is the `Caused by:` line above, which
  is usually scrolled off the top. `grep -A2 "Caused by"` rather than scrolling.
- **Postgres 18 changed its data directory layout.** A volume mounted at
  `/var/lib/postgresql/data` is reported as an "unused mount/volume" and the container exits 1.
  Correct config for 18+ is a single mount at `/var/lib/postgresql`, then `docker compose down -v`
  to clear the half-initialised volume.
- **Flyway warns that PostgreSQL 18.x is newer than it officially supports** — on the Mac and on
  Neon. It validates and migrates anyway. Wants a Flyway bump eventually, not a downgrade.

**Docker**

- **Docker CLI ≠ Docker daemon.** `docker --version` and `docker compose version` are local
  binaries that never contact the engine, so they pass while Docker Desktop isn't running.
  `docker info` is the command that proves the daemon is up.
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
- **Push the code change before saving the env var change**, or the redeploy runs the old code
  against the new configuration.
- **Docker Build Context Directory is a folder (`.`), Dockerfile Path is a file
  (`./Dockerfile`).** Pointing the context at a file gives `invalid local: ... not a directory`.
- **The free tier allows one Key Value instance per workspace.** An instance stuck on
  "Creating" holds the slot, which is why a third attempt won't even start. **Delete first,
  then create** — that alone fixed a blocker that had lasted days.
- **Both auth doors failing identically points downstream of both**, not at a coincidence in
  each. Testing the API-key path and the JWT path separately is what split the problem.
- **`REDIS_PASSWORD` should be absent, not blank**, when internal authentication is off.
- **The campus network blocks `*.aivencloud.com` at DNS**, including external resolvers. Test
  with `nslookup` before assuming a managed service is misconfigured.
- **Render shows env var values in plaintext when the eye toggle is open.** Close it before
  screen-sharing that page.

**CI**

- **`application-test.yaml` sets only the JDBC URL**, so username and password fall through to
  `application.yaml`'s defaults. Postgres.app trusts local connections without a password;
  the CI container demands one. A step-level `DB_PASSWORD` closes it.
- **The runner's database is empty and the test profile sets `baseline-on-migrate: false`**, so
  every push executes V1–V8 as real SQL. That's the proof the migrations work on a machine that
  has never seen the project.

**Terminal and workflow**

- **Don't use `read -s` for secrets you're pasting.** It captures silently, so you can't tell
  whether it worked, and if a stray Enter arrives first the next line goes to *zsh as a command* —
  which is how a Neon password ended up echoed on screen and written to `~/.zsh_history` in
  plaintext. Set the secret where it's actually needed (Render's environment page) instead.
- **Clearing zsh history properly:** `rm ~/.zsh_history && unset HISTFILE && exec zsh`. Without
  `unset HISTFILE` the exiting shell writes its in-memory copy straight back.
- **`echo ${#VAR}` prints a variable's length without revealing it.** A length of 0 means empty;
  a length roughly double what you expect means you pasted a whole connection string.
- **A hostname is the part between `@` and `/` in a connection string.** Everything before the
  `@` is `user:password`.
- **`nslookup <host>` before debugging a connection.** One second, versus six for a failed boot.
- **`openssl rand -base64 48 | tr -d '\n' | pbcopy`** — the `tr -d '\n'` matters; a trailing
  newline makes a secret look right on screen and silently not match.
- **`curl -sS -D - -o /dev/null <url>`** shows headers only. A flooded 80×24 window pushes the
  status line off the top.
- **`curl -s ... | python3 -m json.tool` hides everything you need.** "Expecting value: line 1
  column 1" means the body was *empty*. Use `curl -i` with no pipe.
- **Name your terminal tabs** when running three at once: `echo -ne "\033]0;APP\007"`.
- **A shell reads `~/.zshrc` once, at startup.** A tab opened before an alias was added won't
  know it. Cmd+T, or `source ~/.zshrc`.
- **A variable set in one tab does not exist in another.** Same reason.
- **Paste one command at a time.** Pasting a block makes zsh interleave the output.
- **Check the shell prompt before any relative path.**
- **`lsof -ti :8080 | xargs kill`** before starting the app
- **`git rm --cached a b` is atomic** — if one path isn't tracked, *neither* is removed.
- **`git add` photographs a file at that instant.** Editing afterwards doesn't update the index.
- **`cp` to a different filename case does nothing on macOS** — use `git mv`
- **macOS numbers repeat downloads** — `ls -lt ~/Downloads/*.md | head -5` first.
- **IntelliJ shows fake errors in `notes.md`** — it injects a Java parser into ```java fences.
- Watch for **dictation landing in an open file or on the command line** instead of the chat box

---

## What's next

**Feature work is done and the infrastructure is settled.** Payment gateway, two auth doors,
double-entry ledger, ownership, idempotency, rate limiting, bank simulator, two-transaction
settlement, reconciler, outbox, Kafka publisher, webhook consumer with retries and a DLQ.
29 tests, CI green in under a minute, database on a tier that doesn't expire.

**Nothing is currently broken.** No dates on the calendar.

**In order of value:**

- Tests for `WebhookConsumer` and the dead-letter path — the only proof today is a manual run
- An event id in the webhook payload so merchants can deduplicate an at-least-once delivery
- Scheduled cleanup for published outbox rows and expired idempotency keys (two slow leaks)
- `PATCH /api/v1/merchants/me` to set `webhook_url` through the API instead of psql
- `SELECT ... FOR UPDATE SKIP LOCKED` or ShedLock, so the two schedulers survive a second instance

**Smaller, still outstanding:**

- `POST /api/v1/merchants` returns 200, not 201; no password-change endpoint;
  no `Retry-After` header on 429s
- **Signup accepts a merchant with no password**, which can then never log in. `@NotBlank`.
- Unauthenticated traffic isn't rate limited; no IP-based limiter
- Fixed-window rate limiting allows a boundary burst
- Idempotency keys aren't fingerprinted against the request body (Stripe returns 422)
- The idempotency race path is untested — the test class is `@Transactional`, so a constraint
  violation would poison its own transaction
- Offset pagination degrades with depth; cursors are the standard fix
- **Only one FK constraint exists** (`idempotency_keys.payment_id`); everything else is scalar
- **The API key prefix carries ~20 bits of entropy** — `tk_` eats three of eight characters
- `OwnershipIntegrationTest` survived the list endpoint changing shape, so its assertions
  aren't structural
- A Spring Security warning about a generated password and an `inMemoryUserDetailsManager`
  appears at startup — harmless, but odd for an app with its own two auth doors. Unexamined.
- Five Redis repository-scanning WARNs on every boot, because `spring-boot-starter-data-redis`
  tries to claim the JPA repositories. Fixable with one annotation.
- **Dependency CVEs are unaudited.** Spring Boot 3.4.1 pulls transitive versions with known
  advisories; fixing means a parent bump with its own testing. IntelliJ has a
  "Vulnerable Dependencies" tab that will list them.
- **Neon's free tier caps at 0.5 GB and 100 compute-hours a month.** Neither is close; neither
  warns before it bites.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with Mermaid diagrams, currently **v1.7**:
  a deployment topology diagram plus the application internals
- `docs/UNDERSTANDING.md` — from-scratch primer: what a gateway is, HTTP and Postman from
  zero, credentials explained, day-by-day reasoning, interview narrative, deployment roadmap
- `docs/CONTEXT.md` — this file