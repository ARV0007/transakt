# Transakt Worklog

## Day 1 — 19 Jul 2026
**Built:** Development environment (JDK 21 LTS, IntelliJ IDEA, PostgreSQL 18 via Postgres.app on port 5432, Postman) and the repository with README, .gitignore, and docs/.
**Why:** Nothing can be built or version-controlled without a reproducible local setup, and the repository history itself is part of the deliverable.
**Concepts learned:** LTS versus feature releases; commit is local and push is remote; .gitignore only blocks untracked files; OAuth authorisation flow.
**Interview line:** "I chose Java 21 because it's the LTS release the Spring ecosystem and production systems standardise on."
**Mistake & fix:** Committed `.idea/` before creating .gitignore; removed it from tracking with `git rm -r --cached .idea`.

## Day 2 — 20 Jul 2026
**Built:** Spring Boot 4.1 project (Maven, Java 21, YAML config, dependencies: Spring Web, Lombok, DevTools) merged into the repository; the application boots with embedded Tomcat on port 8080; first REST endpoint `GET /api/v1/health` returning JSON.
**Why:** The health endpoint is the smallest possible end-to-end proof that the whole chain works — server, routing, controller, and JSON serialisation. Kubernetes liveness probes will later call exactly this kind of endpoint.
**Concepts learned:** Maven and pom.xml; the parent POM managing dependency versions; starters as bundles; auto-configuration and component scanning; embedded Tomcat versus WAR deployment; the DispatcherServlet request lifecycle; Jackson serialisation; DevTools restarts.
**Interview line:** "Spring Boot's auto-configuration inspects the classpath at startup — because Tomcat and Jackson were present, it wired up the web server and JSON conversion without any configuration from me."
**Mistake & fix:** The commit panel left new files under "Unversioned Files" unticked, so the first attempt would have committed nothing; learned that Git never tracks a new file until it is explicitly added.

## Day 3 — 21 Jul 2026
**Built:** The `merchant` package — `Merchant` model (Lombok), `MerchantService` (in-memory store, server-generated id and timestamp), `MerchantController` (POST to create, GET by id). Verified end to end in Postman: created a merchant, received a generated UUID, fetched it back.
**Why:** The merchant is the first real domain object in Transakt — before any payment can be processed, the system must know which business is being paid. The three-layer split (controller, service, store) is deliberate: when PostgreSQL replaces the HashMap, only the bottom layer changes.
**Concepts learned:** Layered architecture and why layers are separated; IoC container and dependency injection; constructor injection over field injection; what a bean is; Lombok code generation and IntelliJ annotation processing; `@RequestMapping`, `@RequestBody`, `@PathVariable`; why identifiers must be server-generated.
**Interview line:** "I deliberately built the first version against an in-memory store, restarted the app and watched the data vanish — so when I migrated to PostgreSQL I understood exactly which problems persistence solves."
**Mistake & fix:** Lombok's generated methods appeared unresolved until annotation processing was enabled in IntelliJ — the code was correct, the IDE simply wasn't running the annotation processor.

## Day 4 — 22-24 Jul 2026
**Built:** Migrated merchant storage from an in-memory HashMap to PostgreSQL. Created the transakt database, added JPA and Postgres dependencies, configured the datasource, turned Merchant into a JPA entity, created a repository interface, and rewired the service to use it.
**Why:** Persistence is non-negotiable for a payment system — data must survive restarts, crashes, and run across multiple instances. This is also where the Day 3 layering paid off: only the storage layer changed; the controller was untouched.
**Concepts learned:** JPA vs Hibernate vs Spring Data JPA; entity mapping and column constraints; ddl-auto schema generation; repository interfaces with generated implementations; Optional; open-in-view; reading Hibernate's generated SQL.
**Interview line:** "I migrated from in-memory storage to PostgreSQL with Spring Data JPA — Hibernate generates the schema and SQL from my entity mappings, and because the architecture was cleanly layered, only the storage layer changed."
**Mistake & fix:** A duplicate `spring:` key in application.yml silently dropped the first block (YAML forbids duplicate keys at one level); merged into one. Also a typo `setIdud` instead of `setId` — the compiler caught it, since no such method exists.

## Day 5 — 27-29 Jul 2026
**Built:** The Payment entity and the double-entry ledger. Payment and LedgerEntry entities with enums for status and direction; repositories including a derived query; a PaymentService whose create() writes a payment plus two balancing ledger entries inside one @Transactional method; a PaymentController exposing create, get, and a nested ledger endpoint. Verified end to end: a ₹500 payment produces one CREDIT and one DEBIT of 50000 each.
**Why:** This is the core of a payment system. Double-entry makes the books self-checking; the transaction makes a payment and its accounting atomic; integer paise keeps money exact.
**Concepts learned:** double-entry bookkeeping and why it makes errors detectable; storing money as integer paise; enums over Strings; database transactions and @Transactional atomicity; derived query methods; nested resource URLs.
**Interview line:** "I built a double-entry ledger where every payment writes two equal, opposite entries in one atomic transaction — so the accounting is self-checking and tamper-evident, and a payment can never be recorded without its ledger entries."
**Mistake & fix:** After adding the repositories, IntelliJ reported that save() and findById() could not be found — the code was correct, but the IDE's index was stale; a Maven reload and rebuild resolved it. A reminder that a compile error is not always a code error.

## Day 6 — 31 Jul-1 Aug 2026
**Built:** Input validation and centralized error handling. A CreatePaymentRequest DTO with Bean Validation annotations; @Valid wiring in the controller; a GlobalExceptionHandler (@RestControllerAdvice) turning validation failures into clean 400s and a custom ResourceNotFoundException into clean 404s; services changed to throw instead of returning null.
**Why:** A real API must reject bad input clearly and never leak internal stack traces. Clear, correctly-statused error responses are how a client's code knows what went wrong.
**Concepts learned:** DTOs vs exposing entities; Bean Validation and @Valid; @RestControllerAdvice and @ExceptionHandler; ResponseEntity for status control; fail-loud over fail-silent; custom exceptions.
**Interview line:** "I used DTOs with Bean Validation to reject bad input, and a single @RestControllerAdvice to turn all exceptions into consistent JSON error responses with correct HTTP status codes — so no stack traces ever leak to clients."
**Mistake & fix:** Pasted the second @ExceptionHandler method outside the class body (after the closing brace), which IntelliJ flagged as "annotations not allowed here"; moved it inside so both handlers live in the class.

## Day 7 — 2-3 Aug 2026
**Built:** API key authentication. Added Spring Security; an apiKey field on Merchant generated at signup with a tk_ prefix; a findByApiKey derived query; an ApiKeyFilter that authenticates each request from the X-API-Key header; and a SecurityConfig declaring which paths are open and which require authentication. Verified: a payment with a valid key returns 200, with an invalid or missing key returns 403.
**Why:** A payment API that anyone can call is not a payment API. Machines authenticate with keys, and the filter is the right place to check because it runs before any controller.
**Concepts learned:** Spring Security's secure-by-default stance; the filter chain and OncePerRequestFilter; SecurityContext and authenticated requests; requestMatchers and per-path rules; why CSRF is disabled for stateless APIs; 401 vs 403; API key prefix conventions; the credential bootstrap problem.
**Interview line:** "I implemented API key authentication with a custom Spring Security filter that resolves each request's key to a merchant before it reaches any controller, so the payment API is closed by default and every call is attributable to a specific merchant."
**Mistake & fix:** Spring Boot 4.1.0 had no matching security starter; pinning the version fixed compilation but failed at runtime because the packages had moved between major versions. Resolved by aligning the whole project to Boot 3.4.1 and renaming two starters whose artifact IDs changed. Separately, an authentication test failed because the merchant's id was pasted instead of its apiKey.

## Day 8 — 4-7 Aug 2026
**Built:** JWT authentication for humans, alongside the existing API-key path for machines, plus role-based access control. Seven pieces: (1) BCrypt password storage — a `password` field on Merchant annotated `@JsonProperty(WRITE_ONLY)`, a `PasswordConfig` class exposing a `PasswordEncoder` bean, and MerchantService hashing before `save()`. (2) jjwt 0.13.0 as three artifacts, api at compile scope and impl/jackson at runtime. (3) `auth/JwtService` — mints tokens carrying subject, merchantId, role, issuedAt and a one-hour expiry, and reads them back through a private verify-then-parse method. (4) `POST /api/v1/auth/login` — email and password in, token out, with AuthService doing the credential check. (5) `JwtAuthFilter` reading the `Authorization: Bearer` header. (6) Both filters wired into one SecurityConfig chain with explicit ordering. (7) A `MerchantRole` enum (MERCHANT, ADMIN), the role carried as a token claim, converted into a Spring authority, and enforced per-path. Verified end to end: `GET /merchants` returns 403 with no credential, 200 with an admin token, 403 with a merchant token; signup still returns 200 to anyone; payments still work for ordinary merchants.
**Why:** An API key belongs to a machine — the merchant's server sends the same permanent credential forever, which is right for a server and wrong for a person. A dashboard user needs a credential that expires and that carries a role. There is also a quieter cost: ApiKeyFilter runs `findByApiKey` on every single request purely to ask "is this real?", while a JWT answers the same question by recomputing a signature in memory. Roles then close a hole that had been open since Day 7 — `/api/v1/merchants/**` was entirely `permitAll`, so anyone on the internet could list, edit or delete every merchant in the system. Signup has to stay open for the bootstrap problem, but only signup.
**Concepts learned:** why passwords are hashed rather than stored, BCrypt's deliberate slowness and cost factor, automatic salting and why there is no salt column; `@Configuration` as a source of beans versus `@Service` as a bean, and why a third-party class can only enter the container through an `@Bean` method; bean dependency cycles and how moving a bean to its own class breaks one; Jackson's WRITE_ONLY access; Maven runtime scope used as an API guardrail; JWT structure and the fact that the payload is Base64-encoded, not encrypted; standard versus custom claims; stateless verification and the revocation trade-off it forces; HS256's 256-bit key minimum; email enumeration and why both login failure paths must return the same message; the Bearer scheme and why an auth filter should record identity rather than reject; first-match-wins ordering in `authorizeHttpRequests`; Spring Security's mandatory `ROLE_` prefix; splitting a path by HTTP method so signup stays open while the rest goes admin-only.
**Interview line:** "Transakt has two authentication paths on purpose. API keys authenticate machines and cost a database lookup per request; JWTs authenticate humans, carry role claims, and verify locally against an HMAC signature, so auth cost stays flat as you scale. The trade-off is revocation — you can't invalidate a JWT before it expires, which is exactly why the TTL is one hour rather than a month. Roles ride in the token as a claim, which means a demotion doesn't take effect until the token expires; the API-key path reads the role from a fresh row, so it takes effect immediately. Same permission model, two staleness guarantees, and knowing which one you have is the point."
**Mistake & fix:** `ddl-auto: update` could not add a `NOT NULL` role column to a table that already had rows — Postgres refused with "column role of relation merchants contains null values", and the Java-side field initialiser was no help because it only runs when new objects are constructed. Fixed by adding the column as nullable in psql, backfilling with an `UPDATE`, then restarting so Hibernate could apply the constraint; in production this would be one Flyway migration rather than a manual step. Separately, IntelliJ's quick-fix menu offered "Add static import" above "Import class" for `Jwts`, which stripped the `Jwts.` prefix from the builder call and produced ten errors unrelated to the real problem — and on another occasion `generateToken` was typed inside the constructor body rather than after its closing brace, producing ten more. Both taught the same lesson: a large error count usually means one structural problem, so fix the earliest error and the rest evaporate. Also learned that keeping two overloads of `generateToken` — one with a role claim, one without — would have silently minted role-less tokens; deleting the old one turned a future unexplainable 403 into an immediate compile error naming the exact line.

## Day 9 — 7 Aug 2026
**Built:** Ownership authorization on payments. Unified the authentication principal so both filters set the merchant ID (JwtAuthFilter previously set the email, ApiKeyFilter the UUID), which required a new `extractMerchantId` on JwtService. Removed `merchantId` from `CreatePaymentRequest` entirely so a payment is always filed under the authenticated caller — the controller now takes an `Authentication` parameter and reads `getName()`. Added ownership checks to `getById` and `getLedgerForPayment`, throwing `ResourceNotFoundException` with an identical message whether the row is missing or simply not yours, with admins bypassing the check. Added `GET /api/v1/payments`, backed by a new `findByMerchantId` derived query, returning only the caller's payments for a merchant and every payment for an admin. Verified: a merchant reading another merchant's payment or ledger gets 404, an admin gets 200, and the list endpoint returns a strictly smaller array for a merchant than for an admin.
**Why:** Day 8 answered "who are you" and "what kind of user are you". Neither answers "is this your data". A merchant holding a completely valid token with a correct MERCHANT role could file a payment under someone else's account, and — far worse — walk payment IDs and read any merchant's entire transaction history: amounts, timing, volume. The create hole was a data-integrity bug; the read hole was a breach.
**Concepts learned:** the three distinct layers — authentication, role-based authorization, ownership (resource-level) authorization — and why the first two do nothing for the third; making a thing impossible versus validating against it, applied to identity rather than to fields; why 404 beats 403 for a resource you're not allowed to see, since a 403 confirms the ID is real and hands an enumerator exactly the signal they want; `Authentication` as a controller method parameter, resolved by Spring MVC straight from the SecurityContext with no annotation or injection; reading `getAuthorities()` to test for `ROLE_ADMIN`; fetch-then-check for a single resource versus scope-the-query for a collection, and why filtering a list in Java is both slower and a data-handling risk; keeping an authorization rule in exactly one method and delegating to it rather than duplicating the condition.
**Interview line:** "Authentication tells you who's calling and roles tell you what kind of user they are, but neither tells you whether a specific record is theirs. In Transakt a valid MERCHANT token used to be enough to read any payment in the system. I fixed it in two shapes: for a single resource, load it and compare owners, returning 404 rather than 403 so an attacker can't distinguish 'not yours' from 'doesn't exist' and can't enumerate IDs; for a collection, scope the query itself so rows the caller can't see never leave the database. And for writes I deleted the merchantId field from the request DTO entirely — the server already knows who's calling, so asking the client and then validating the answer is work you don't need to do."
**Mistake & fix:** Twice during testing a request that had worked minutes earlier came back 403, and both times the instinct was to suspect the new code. Both times the token had simply passed its one-hour expiry — the second occasion was 89 minutes after the token was issued. Worth internalising: on this API a sudden 403 means check the token age before checking anything else. Separately, an attempt to create an admin-owned payment for the ownership test produced a payment owned by the merchant instead, because the `Authorization` header still held the old token — a reminder that after Day 9 the request body no longer carries identity at all, so the header is the only thing that decides who a payment belongs to.

## Day 10 — 9-10 Aug 2026
**Built:** Redis, and two things on top of it. **Idempotency:** an `IdempotencyService` wrapping `SET NX EX` under keys shaped `idem:<merchantId>:<clientKey>` with a 24-hour TTL, and an optional `Idempotency-Key` header on `POST /api/v1/payments`. The controller reserves the key, creates the payment, then overwrites the reservation with the payment ID; a retry carrying the same key gets the original payment back rather than creating a second one, an in-flight duplicate gets 409 Conflict, and a failed creation releases the reservation so the merchant isn't locked out for 24 hours. **Rate limiting:** a `RateLimitService` using `INCR` on keys shaped `rate:<merchantId>:<epochMinute>` with a 60-second TTL, and a `RateLimitFilter` added to the security chain after the authentication filters, returning 429 past twenty requests per minute. Verified end to end: the same idempotency key sent a dozen times produced exactly one payment with an identical id and createdAt, a different key produced a new one, and 25 rapid requests returned exactly twenty 200s followed by 429s.
**Why:** A merchant's server sends a payment request, the payment is created and committed, and the connection drops before the response arrives. The merchant now has no idea whether it worked, so it retries — and the customer is charged twice. This is not an edge case: timeouts are constant at scale, retrying is correct client behaviour, and a double charge is the single most damaging bug a payment gateway can ship. `POST` is the only HTTP method that isn't naturally safe to repeat, which is exactly why it needs help. Rate limiting solves a different failure of the same kind: without a ceiling, one buggy integration in a retry loop can saturate the database and degrade the service for every other merchant.
**Concepts learned:** what an in-memory key-value store is and how it differs from a relational database; TTL as self-cleaning storage rather than a scheduled job; `SET NX` as an atomic check-and-set, and why the obvious `EXISTS` then `SET` has a race condition that fails under exactly the concurrency it exists to handle; the reserve/create/store two-phase pattern and why a placeholder is needed before the payment ID exists; 409 Conflict for an in-flight duplicate and why neither 400 nor 500 fits; scoping keys by merchant because clients choose their own key strings; `INCR` returning 1 on a missing key, so the counter creates itself; setting a TTL only on the first increment, because overwriting a Redis value clears its expiry; `@RequestHeader` with `required = false`; that a Spring `@Transactional` method calling another method on the same bean bypasses the proxy entirely and silently runs with no transaction; that a filter runs before the DispatcherServlet, so `@RestControllerAdvice` cannot catch anything it throws and the response must be written by hand; and `addFilterAfter` versus `addFilterBefore` when a filter depends on identity that an earlier filter established.
**Interview line:** "The most damaging bug a payment gateway can have is charging twice, and it doesn't need an attacker — a network timeout plus a correct client retry is enough. I implemented idempotency keys on Redis: the client sends a unique key per logical operation, the server reserves it with an atomic `SET NX`, and a retry with the same key gets the original payment back instead of creating a new one. The atomicity matters — checking whether a key exists and then setting it is two operations, and two simultaneous retries can both pass the check. The trade-off I made knowingly is that Redis isn't durable by default, so a Redis restart could let a retry through; systems that can't tolerate that store idempotency keys in the database with a unique index and accept the latency. Rate limiting reuses the same infrastructure with a different pattern — `INCR` on a key that embeds the current minute, so the counter resets by itself when the key name changes."
**Mistake & fix:** `brew services start redis` failed with an unhelpful exit code 1; running `redis-server` in a foreground terminal tab worked immediately and showed everything it was doing, which is the better setup for local development anyway. The `data: redis:` block was first indented under `spring.jpa.properties.hibernate` rather than directly under `spring:` — and crucially that produces no error at all, because Spring simply doesn't find `spring.data.redis.*` and falls back to its localhost:6379 defaults, which happen to be the intended values. The config would have been silently inert and the problem would only have surfaced at deployment, when setting `REDIS_HOST` changed nothing. A misspelled YAML key fails loudly; a correctly-spelled key in the wrong place is ignored. Separately, two rate-limit test runs failed because the token was mangled while editing a long `curl` line — once leaving the literal placeholder in place, once eating the `er ` out of `Bearer `. The fix is to store the token in a shell variable on its own line and reference `$TOKEN` afterwards, which removes the editing step entirely.

## Day 11 — 10-12 Aug 2026
**Built:** An integration test suite. A `test` Spring profile in `src/test/resources/application-test.yaml` pointing at a separate `transakt_test` database with `ddl-auto: create-drop`, Redis database 1, and a rate limit high enough not to trip during a fast run. Then four test classes, all `@SpringBootTest` with `@AutoConfigureMockMvc` and `@Transactional`: `AuthIntegrationTest` (signup stays open and never serialises the password, login succeeds, and both failure paths return an identical message), `OwnershipIntegrationTest` (a merchant reads its own payment, gets 404 on another merchant's payment and ledger, the list endpoint is scoped to the caller, and a forged `merchantId` in the body is ignored), `IdempotencyIntegrationTest` (the same key returns the same payment, different keys create different payments, a reused key ignores a changed body, and two merchants can use the same key independently), and `RateLimitIntegrationTest` (429 past the limit, and one merchant hitting the ceiling does not affect another). Nineteen tests in total, running in about twenty seconds.
**Why:** The point of tests is not to find bugs today — every one of these scenarios had already been verified by hand in Postman. The point is knowing when something breaks tomorrow. Refactor `SecurityConfig` next week and accidentally make `/api/v1/payments` public and nothing currently tells you; you find out when a stranger reads someone's transaction history. A test suite is a claim made once that a machine re-checks forever. It is also the fastest thing an interviewer looks for: nine days of careful architecture with zero tests reads as someone who has not worked on a team.
**Concepts learned:** the test pyramid, and why this project is almost entirely integration tests — a unit test of `PaymentService` with mocked repositories would pass happily while the security config was wide open, because it never touches the filter chain; `MockMvc` sending real requests through the full stack without opening a network port; Spring profiles as config layered on top of `application.yaml` rather than replacing it; `ddl-auto: create-drop` giving every run an empty, correctly-shaped schema and doubling as a free check on entity mappings; `@Transactional` on a test class rolling back after each test so tests cannot see each other's data; that this rollback covers Postgres and **not** Redis, so idempotency keys and rate counters survive between tests and need an explicit `flushDb()` in `@BeforeEach` — test isolation is per-store, not automatic; Redis's sixteen numbered databases sharing one server with separate keyspaces, so tests use database 1 and no second install is needed; `@TestPropertySource` overriding one property for a single class, at the cost of a separate Spring context; extracting values from a response with `.andReturn().getResponse().getContentAsString()` rather than only asserting with `jsonPath`, which is what makes two-actor scenarios possible; and naming tests as sentences, because the name is the first thing read when one fails.
**Interview line:** "I wrote the suite as integration tests rather than unit tests, deliberately. Almost everything interesting in this project lives in the wiring — a filter chain, per-path rules, ownership checks that depend on who authenticated, idempotency that depends on Redis. A mocked unit test of the service layer would pass while the security config was wide open. So the tests send real HTTP requests through the whole stack against a real database. The ones I care about most are the ones that would fail silently in production: delete the `WRITE_ONLY` annotation on the password field and a test goes red; change one login failure message and the email-enumeration protection breaks a test; drop the merchant ID out of an idempotency or rate-limit key and two merchants start colliding. Those are single-word changes that look harmless in a diff and are nearly impossible to catch by hand."
**Mistake & fix:** `./mvnw test` failed with `Fatal error compiling: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`, which is Lombok hooking into a compiler it does not understand. IntelliJ had been compiling fine because it uses the project SDK, temurin-21; Maven was using the shell's `JAVA_HOME`, which pointed at Java 26 — the exact thing my own context file warns about. Fixed permanently by appending `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` to `~/.zshrc`. Separately, three files landed in the wrong place: `application-test.yaml` and `AuthIntegrationTest` were created under `src/main` instead of `src/test`, and `IdempotencyIntegrationTest` went directly under `src/test/java` instead of inside the package folder. The first produced a baffling `cannot find symbol: class Test`, because `spring-boot-starter-test` is test-scoped and simply is not on the main classpath — `src/main` and `src/test` are separate compilation units. IntelliJ also auto-imported `org.springframework.http.RequestEntity.post` alongside `MockMvcRequestBuilders.post`, producing "Ambiguous method call", which is a friendlier error than it looks: Java found two valid candidates and refused to guess. And `git add src/` was run before the files were moved, so git showed the same files three times — staged at the old path, deleted, and untracked at the new one; `git add -A src/` resolved it into clean renames.

## Day 12a — Flyway migrations (13 Aug 2026)

**Built**
- Added `flyway-core` + `flyway-database-postgresql` (the second is mandatory from Flyway 10 onward — Postgres support moved out of core).
- `V1__initial_schema.sql` — a `pg_dump --schema-only --no-owner` of the live `transakt` schema, preamble trimmed.
- `V2__add_query_indexes.sql` — `idx_payments_merchant_id`, `idx_ledger_entries_payment_id`.
- `application.yaml`: `ddl-auto: update` → `validate`; added `spring.flyway` with `baseline-on-migrate: true`, `baseline-version: 1`.
- `application-test.yaml`: `ddl-auto: create-drop` → `validate`; added `baseline-on-migrate: false` as a deliberate guard.
- Dropped and recreated `transakt_test` so the migrations ran against a genuinely empty schema.

**Why**
Day 8 hit the wall that makes this necessary: `ddl-auto: update` cannot add a NOT NULL column to a
table that already has rows. The fix was a hand-run `ALTER TABLE` in psql — a change that existed
only on my laptop, recorded nowhere, reproducible by nobody. Flyway makes every schema change a
numbered, ordered, checksummed file in the repo. The database's shape becomes something the code
review can see.

**Concepts**
- **Migration history table.** Flyway keeps `flyway_schema_history` in the database itself. That's the
  record of which versions have been applied, so the database knows its own state rather than
  relying on anyone remembering.
- **`validate` vs `update`.** Hibernate stops building anything and only checks that the schema
  matches the entities. If it doesn't, the app refuses to start. All construction is Flyway's job now.
- **Baselining.** `baseline-on-migrate: true` handles a database that already exists: Flyway writes a
  baseline row at `baseline-version` and skips everything at or below it. The dev DB skipped V1 for
  exactly this reason; the freshly-created test DB ran it for real. Same config, opposite path.
- **Indexes.** Both new indexes back non-unique columns used for filtering. Columns with a UNIQUE
  constraint (`merchants.api_key`, `merchants.email`) already have an index — Postgres implements
  unique constraints *as* unique indexes — so adding one by hand there would be pure waste.

**Interview line**
"I moved schema management off Hibernate's `ddl-auto` and onto Flyway, and baselined the existing
database rather than dropping it. The subtle part is that the same config behaves differently on a
pre-existing database than on an empty one, so I disabled baselining in the test profile — a
non-empty test database should fail the build loudly instead of silently skipping the first
migration and reporting a green suite that proved nothing."

**Mistakes & fixes**
1. `pg_dump` on PostgreSQL 18 wraps its output in `\restrict` / `\unrestrict`. The opening one went
   with the trimmed preamble; the closing one sat *below* "dump complete" and looked like a footer.
   Flyway isn't psql — it sends statements over JDBC, so a backslash line is a syntax error, and
   since migrations run in a transaction the whole schema would have rolled back. Removed with
   `sed -i '' '/^\\unrestrict/d'` (macOS `sed` requires the empty `''`).
2. V2 was never actually saved to disk. `ls` showed one file, `total 8`. Flyway wouldn't have
   errored — it would have run V1, reported success, and left the indexes uncreated. Verifying with
   `ls` before running is now part of the routine.
3. `./mvnw spring-boot:run` failed with "Port 8080 was already in use" — a stale instance from
   IntelliJ's run button, started *before* the config change. Same cause explained a second symptom:
   the dev database looked untouched because the process holding the port predated Flyway.
   `lsof -i :8080` → `kill <PID>`.


## Day 12b — pagination on GET /payments (13–14 Aug 2026)

**Built**
- `findByMerchantId` returns `Page<Payment>` and takes a `Pageable`; deleted the old `List` overload.
- `getAllForCaller` gained a `Pageable` param. The admin branch needed nothing — `findAll(Pageable)`
  comes free from `JpaRepository`.
- Controller: `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`.
- `spring.data.web.pageable.max-page-size: 100`.
- New `WebConfig`: `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.

**Why**
`GET /payments` returned every row a merchant owned. Invisible at seven payments; at 100,000 it fails
three ways at once — Postgres builds the entire result set, Hibernate materialises every row as a Java
object, Jackson serialises the lot into one response. And the client had no way to ask for less: there
was no vocabulary in the API for "just the newest twenty."

**Concepts**
- **`Pageable` / `Page`.** Spring MVC resolves a `Pageable` straight from query parameters, the same way
  it resolves `Authentication` from the SecurityContext. Spring Data rewrites the SQL to add
  `LIMIT`/`OFFSET`. Verified in the logs:
  `where merchant_id=? order by created_at desc fetch first ? rows only` — and that `WHERE` is exactly
  the query `idx_payments_merchant_id` was built for on 12a.
- **The second query.** `Page` promises `getTotalElements()`, which a slice of 20 rows can't know, so
  Spring Data fires a `COUNT`. It skips it when the first page returns fewer rows than the page size,
  because then the total is already in hand.
- **Offset pagination has a ceiling.** `OFFSET 100000` makes Postgres walk and discard 100,000 rows
  before returning anything. That's why Stripe uses cursors (`starting_after=<id>`) rather than offsets.
- **A cap is not optional.** Spring's default page size is 20 with *no upper bound*, so `?size=999999`
  is a supported request and puts you back where you started — except now it looks like a feature.
- **Don't publish a framework's internals.** Returning `Page` directly serialises `PageImpl`, which
  duplicates `size`/`number`/`sort` at the top level and again inside a `pageable` blob. Spring warns
  about this because the shape isn't guaranteed stable. `VIA_DTO` gives
  `{content: [...], page: {size, number, totalElements, totalPages}}` and a contract that won't move.

**Interview line**
"I paginated the list endpoint with Spring Data's `Pageable`, capped the page size server-side, and
switched the response to `PagedModel` rather than serialising `PageImpl` — publishing a framework
class as your API contract means a library refactor changes your public JSON."

**Mistake & fix**
`curl -s ... | python3 -m json.tool` returned `Expecting value: line 1 column 1` — an *empty* body, not
malformed JSON. `-s` silences curl's errors and the pipe swallows the status line. The app simply
wasn't running. When a response is empty, drop the pretty-printer and use `curl -i`.

---

## Day 12c — hashed API keys (14–15 Aug 2026)

**Built**
- `V3__hash_api_keys.sql` (expand): added `api_key_prefix` + `api_key_hash` nullable, backfilled from
  the plaintext column, unique index on the prefix.
- `common/ApiKeyHasher.java`: `PREFIX_LENGTH`, `prefixOf` with a length guard, `hash` via
  `MessageDigest` + `HexFormat`, `matches` via `MessageDigest.isEqual`.
- `Merchant` maps both columns; `@JsonIgnore` on the hash.
- `MerchantService.create` populates all three from one generated key.
- `ApiKeyFilter`: `prefixOf` → `findByApiKeyPrefix` → `matches`.
- `V4__drop_plaintext_api_key.sql` (contract): `SET NOT NULL` ×2, then `DROP COLUMN api_key`.
- `Merchant.apiKey` became `@Transient` so the key is still shown once at creation.

**Why**
`merchants.api_key` was stored in plaintext. Any dump, leak, or read-only login exposed every live
credential. Passwords were solved with BCrypt on Day 5 — the obvious move was to do the same here.

**It doesn't work, and the reason is the interesting part.** BCrypt is salted, so the same key hashes
differently every time. That's fine for passwords because the email identifies the row first, and
`matches()` compares against that one hash. An API key has no email — the key *is* the identity. To
find the merchant you'd BCrypt-compare against every row in the table, one deliberately-slow hash per
merchant, per request.

Hence the two-column design: a **prefix** that's indexed and not secret, used only to find the row,
and a **hash** that authenticates it. One indexed lookup plus one comparison, constant time regardless
of how many merchants exist. Same shape as Stripe's `sk_live_...` and GitHub's `ghp_...`.

**Concepts**
- **SHA-256, not BCrypt.** Slow hashing exists because humans pick guessable passwords. A 256-bit
  random key isn't brute-forceable at any hash speed, so BCrypt would add latency to every API request
  to defend against an attack that can't happen.
- **Expand/contract.** Add the new columns → start *writing* them → switch *readers* → drop the old
  column. Between the first and last step the database supports both shapes, so nothing is ever in a
  state where a rollback loses data, and the only irreversible step is last and isolated.
- **Writers before readers.** Switching the reader first would leave any merchant created in between
  with a plaintext key and no hash — unable to authenticate at all.
- **NOT NULL belongs to contract, not expand.** Same two statements, opposite phase. In expand the
  entity doesn't map the columns yet, so Hibernate's INSERT omits them and every signup would fail.
- **Constant-time comparison.** `String.equals` returns at the first differing byte, which in principle
  leaks how much of a guess was right. `MessageDigest.isEqual` doesn't.
- **The shown-once pattern.** `@Transient` means Hibernate ignores the field entirely but Jackson still
  serialises it. The key appears in the response that created it and nowhere else, ever.

**Interview line**
"I migrated plaintext API keys to hashed storage using expand/contract across four steps, so the system
kept working throughout and only the final step was irreversible. The design detail worth explaining is
why you can't just BCrypt an API key: with no separate identifier, authentication would mean hashing
against every row. Splitting the key into an indexed public prefix and a secret hash makes the lookup
constant-time — it's why Stripe and GitHub keys look the way they do."

**Mistakes & fixes**
1. **My own plan had the step order backwards** — readers before writers. Caught before writing code.
2. **The draft V3 included `SET NOT NULL`.** Would have failed every signup test. Those moved to V4.
3. **A missing `package` line produced ~100 errors in unrelated files.** `ApiKeyHasher.java` had no
   package declaration (a Cmd+A paste wiped it), so javac reported `duplicate class`. Because Lombok is
   an *annotation processor*, that early failure aborted processing and every Lombok-generated getter
   in the project vanished at once. The fix was one line. **Always read the first errors, never the
   tail:** `./mvnw compile 2>&1 | grep "ERROR.*\.java:" | head -20`.
4. **The three-keys trap.** `create()` generated the key inline inside `setApiKey(...)`. Adding the
   prefix and hash lines without extracting to a local variable first would have called
   `UUID.randomUUID()` three separate times — storing a prefix and hash for keys nobody ever received.
   Every column would have looked correctly populated. Only comparing `shasum -a 256` of the issued key
   against the stored hash would have revealed it.
5. **`save()` drops `@Transient` fields.** Signup started returning `apiKey: null` after V4. Because the
   service assigns the ID itself, Spring Data's `isNew()` is false, so `save()` calls `em.merge()` —
   which copies only *persistent* state onto a new managed instance and returns that. Fixed by
   re-setting the field on the returned object.

**Worth recording separately:** #5 was caught by `signupIsOpenAndNeverReturnsThePassword`, a test
written on Day 11 to check something entirely different — that signup doesn't leak the password hash.
It caught a subtle JPA behaviour four days later in code that had nothing to do with passwords. That's
the actual return on a test suite: not the bugs it finds the day you write it, but the ones it finds on
a Saturday in code you weren't thinking about.

## Day 13 — Docker and docker-compose (15–17 Aug 2026)

**Built**
- **`Dockerfile`, multi-stage.** Stage one is `maven:3.9-eclipse-temurin-21` and compiles the jar;
  stage two is `eclipse-temurin:21-jre` and runs it, pulling the artifact across with
  `COPY --from=build /app/target/*.jar`. `-DskipTests`, because the tests need Postgres and Redis
  and neither exists during an image build.
- **Layer ordering that matters.** `COPY pom.xml` and `mvn dependency:go-offline` come *before*
  `COPY src`, so the dependency layer caches. It took 154.9 seconds the first time and zero on
  every rebuild since.
- **`.dockerignore`** — `target/`, `.git/`, `.idea/`, `*.iml`, `.DS_Store`, `docs/`.
- **`docker-compose.yml`** — three services: `postgres:18`, `redis:8-alpine`, `app`. Healthchecks
  with `condition: service_healthy`. Named volume on Postgres only. Only `app` publishes 8080.
- **`application.yaml`** — datasource parameterised: `${DB_HOST:localhost}`, `${DB_NAME:transakt}`,
  `${DB_USER:aman}`, `${DB_PASSWORD:}`. Local behaviour unchanged; compose supplies its own values.

**Verified 17 Aug 01:54** — `Successfully applied 4 migrations to schema "public", now at version v4`,
`Started TransaktApplication in 4.43 seconds`, and `curl -i http://localhost:8080/api/v1/health`
returning 200 from the Mac. V1–V4 ran as real SQL against a completely empty containerised Postgres
18.6 — no baselining, no shortcuts.

**Why**
Running Transakt required three manual preconditions: Postgres.app started, `redis-server` alive in
its own terminal tab, and `JAVA_HOME` pinned to temurin-21. Every one of those is a thing a new
machine doesn't have and a thing I'd have to explain to anyone I handed the repo to. "Works on my
machine" is not a property of the code; it's a property of the machine.

There's a second reason, and it's the one that actually forced the work: Day 14 is impossible without
this. A platform doesn't run your source, it runs an image.

And it closed the loop on Day 12a. That empty containerised Postgres had never seen this project.
The schema arrived correctly anyway, built from migrations, on infrastructure nobody had configured.
That's the whole payoff from moving off `ddl-auto`, demonstrated rather than argued.

**Concepts**
- **Image vs container.** The image is the recipe, the container is the meal. One image, many
  containers, none of them carrying state that matters.
- **Multi-stage builds.** The build needs Maven, a full JDK, and ~200 MB of downloaded dependencies.
  The runtime needs a JRE and one jar. Shipping the first to production means shipping a compiler and
  a package manager to a machine that should only be running code.
- **Layer caching, and why instruction order is a design decision.** Docker caches each instruction
  and invalidates everything below the first change. `COPY src` before dependency resolution would
  re-download every dependency on every one-character code edit. Splitting the copy in two is the
  single highest-leverage line in a Dockerfile.
- **Build context.** Docker ships the entire directory to the daemon *before* running any instruction.
  Without `.dockerignore` that's the full git history and every compiled class, uploaded on each build
  to be ignored.
- **Service names are hostnames.** Inside the compose network, `postgres` resolves. `localhost` does
  not mean "the host machine" — inside the app container it means the app container.
- **`depends_on` is not enough.** It waits for the *container* to exist, not for Postgres to accept
  connections. The app boots in three seconds and would lose that race routinely. Healthchecks with
  `condition: service_healthy` are load-bearing, not decoration.
- **Volumes are a statement about what's worth keeping.** Postgres gets a named volume. Redis
  deliberately doesn't — everything in it has a TTL and is meant to expire.
- **Publishing ports is opt-in.** Only `app` exposes one. Postgres and Redis stay invisible to the
  host, which also avoids colliding with Postgres.app on 5432.

**Interview line**
"I containerised the whole stack — app, Postgres and Redis — so it starts with one command. The two
details worth explaining are ordering the Dockerfile so the dependency layer caches, which took the
rebuild from two and a half minutes to nothing, and using healthchecks rather than `depends_on`,
because `depends_on` only waits for the container to exist and my app boots faster than Postgres
accepts connections."

**Mistakes & fixes**
1. **Docker CLI is not the Docker daemon.** `docker --version` and `docker compose version` are local
   binaries that never contact the engine, so both pass happily while Docker Desktop isn't running.
   `docker info` is the command that proves the daemon is up — it prints a full Client section, then
   fails at `Server:`.
2. **Postgres 18 changed its data directory layout.** The official image now uses major-version
   subdirectories for `pg_ctlcluster` compatibility, so a volume mounted at
   `/var/lib/postgresql/data` is reported as an "unused mount/volume" and the container exits 1.
   Correct config for 18+ is a single mount at `/var/lib/postgresql`, then `docker compose down -v`
   to clear the half-initialised volume. (docker-library/postgres PR 1259, issue 37.)
3. **Compose interleaves every container's output.** Redis's startup banner buried Postgres's fatal
   error completely. `docker compose logs <service>` isolates one.
4. **IntelliJ creates files relative to the selected Project panel node.** `docker-compose.yml` first
   landed inside `target/` — gitignored, wiped by `mvn clean`, and excluded by `.dockerignore`. Click
   the root node first.
5. **Port 8080 conflict, third occurrence.** `lsof -ti :8080 | xargs kill`.

**Also noted:** Flyway logs a benign warning that PostgreSQL 18.4 is newer than it officially
supports. It validated every migration anyway. That wants a Flyway bump eventually, not a Postgres
downgrade.

---

## Day 14 — first deployment (17–23 Aug 2026)

**Live at https://transakt.onrender.com**

**Built**
- **Platform choice: Render.** Railway has had no free tier since 2023 ($5/month minimum). Render
  still has a genuine zero-cost path and asks for no card.
- **Two remaining config gaps** (commit `90a8512`): `server.port: ${PORT:8080}` at the top level, a
  sibling of `spring:`; and `spring.data.redis.password: ${REDIS_PASSWORD:}` alongside host and port.
- **Three Render resources:** web service `transakt` (Docker, Free, Singapore, Auto-Deploy on),
  `transakt-db` (PostgreSQL 18, Free, expires 17 Sept 2026), `transakt-redis` (**Valkey 8**, Free,
  `allkeys-lru`).
- **Environment variables:** `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
  `REDIS_HOST`, `REDIS_PORT`. No `REDIS_PASSWORD` — internal authentication is off, so internal
  traffic needs none.
- **Commit `b6045be`** — deleted a `@Bean CommandLineRunner` that had been quietly killing every
  deploy for three days. See below; it's the whole story of this day.

**Verified in production, 22–23 Aug**
`GET /health` 200 · signup 200 with a real UUID and hashed key · login 200 with a JWT ·
`GET /payments` 200, paginated and scoped · `POST /payments` 201 CAPTURED · the same
`Idempotency-Key` replayed returns the same payment · a 25-request loop returns nineteen 200s then
six 429s. Nineteen rather than twenty because the payment created moments earlier had already spent
one slot in that minute — which is itself the proof that the counter is per merchant per minute
across *all* authenticated endpoints, not per endpoint.

**Why**
Everything before this was a claim. Fourteen days of architecture that only runs on one laptop is
indistinguishable, from the outside, from fourteen days of nothing. A URL is evidence.

And it's where Day 12a stops being a design preference and starts being the thing that makes
deployment possible at all. A fresh managed Postgres, four migration files, and the schema builds
itself. Without Flyway this day would have begun with hand-running SQL against a production database.

**Concepts**
- **One artifact, three environments.** Every external address is `${VAR:sensible-default}`. The same
  image runs on the Mac, in compose, and in Singapore; only the environment differs. The defaults
  aren't laziness — they're what keeps local development working with zero setup.
- **Platform-injected PORT.** The host decides the port and tells the app. Binding a hardcoded 8080
  is a contract the platform never agreed to.
- **Internal versus external hostnames.** Render gives both. The internal `dpg-…` / `red-…` form
  never leaves their network and needs no TLS; the external form is public and does. Private
  networking is per-region, which is why all three services had to be in Singapore.
- **Free-tier trade-offs, accepted knowingly.** The web service spins down after 15 minutes idle —
  the first request afterwards measured *over two minutes*, not the 50 seconds advertised. Free
  Postgres expires 30 days after creation. Neither matters much when the schema rebuilds from
  migrations and the loss would be demo data.
- **Valkey.** Render's Key Value is Valkey, the fork created after Redis changed its licence. It's
  wire-compatible, so Lettuce and Spring Data Redis needed no changes at all.
- **Reading a deploy log.** "Exited with status 1" is a wrapper, never a cause. Search for `ERROR`,
  then `Caused by` — don't scroll. And read a Spring stack trace **bottom-up**: the last `Caused by:`
  is the truth, everything above it is framework plumbing.
- **`x-render-routing: no-deploy`** means the service has never had a successful deploy. The mystery
  HTML page served for three days was Render's own 502, not the app.
- **Defaults are not decisions.** Render's Postgres ships with inbound `0.0.0.0/0`; its Key Value
  ships blocking all external traffic. Same platform, two opposite security postures, neither of them
  chosen by me. Worth auditing rather than inheriting.

**Interview line**
"The deployment itself was routine. What's worth talking about is the three days it took to find the
failure. Every deploy exited with status 1 and the platform's error message was a wrapper with no
cause in it. It turned out to be a six-line `CommandLineRunner` I'd added as a debug check on Day 13,
which pinged Redis at startup. A `CommandLineRunner` runs *after* the context refreshes and Tomcat
binds — so the image had built, the database had connected, the migrations had applied and the server
had started, every single time. Then a debug line threw and Spring closed the whole context. The
failure was at the finish line, which is exactly why nothing upstream looked wrong."

**Mistakes & fixes**
1. **The three-day outage, and the assumption underneath it.** My notes said the app would boot fine
   without Redis, reasoning that Lettuce connects lazily and `/health` never touches it. That was
   wrong, and being wrong about it cost the whole week. Because I believed it, I treated the stuck
   Redis instance and the failing deploy as *two independent problems* — and spent days debugging
   ports, build contexts and database config on a failure that Redis was causing all along. The fix
   was deleting six lines. **A wrong assumption doesn't slow you down; it points you somewhere else
   entirely.**
2. **Docker Build Context Directory is a folder, Dockerfile Path is a file.** Pointing the context at
   `./Dockerfile` produced `invalid local: stat /opt/render/project/src/Dockerfile: not a directory`.
   Correct values are `.` and `./Dockerfile` respectively.
3. **Commit `de87094` was a one-line no-op — the intended edits never landed.** Render deploys from
   GitHub, so an unpushed or unsaved change simply doesn't exist to it. The check that closes this
   off is `git show origin/main:src/main/resources/application.yaml`, which reads the file *as
   pushed* rather than the copy on disk.
4. **The Key Value instance that would never provision.** Two instances hung on "Creating" — one for
   six hours — and a third couldn't be created at all. That third failure was the clue: the free tier
   allows one Key Value per workspace, and the stuck one was holding the slot. **Delete first, then
   create.** The replacement was Available in under five minutes.
5. **Both auth doors failing identically pointed downstream of both.** With the app finally running,
   every authenticated endpoint returned an empty-bodied 403 — with a fresh JWT *and* with a fresh
   API key. Two completely different credential paths failing the same way isn't a coincidence in
   each; it's one fault after both. It was `RateLimitFilter` reaching for a Redis that didn't exist
   yet. Testing both doors separately is what split the problem.
6. **A signal that carries two meanings proves neither.** Those 403s came with a `JSESSIONID`, which
   I first read as proof the request had arrived unauthenticated. It isn't: in Spring Security 6 the
   context is persisted to a session when a filter *sets* an Authentication, so the cookie appears
   either way. The log settled it in ten seconds; the inference had been leading the wrong direction
   for twenty minutes.
7. **A sudden 403 mid-test, again.** Same lesson as Day 9, learned again the hard way: the token had
   passed its one-hour expiry. Check the token age before anything else.
8. **An interactive prompt is not a shell.** Twice during SSH setup, the next command was pasted in
   as the *answer* to a question — once to `ssh-keygen`'s "where to save the key", once to ssh's
   "continue connecting?". Terminal looks identical either way. If the line ends in `?` or `:` rather
   than `%`, answer it first. Passphrase prompts also echo nothing while you type, which reads as a
   frozen window.
9. **GitHub HTTPS credentials expire.** `Password authentication is not supported for Git
   operations`. Switched the remote to SSH with an ed25519 key, which doesn't expire.

**Still open**
- The Redis failure surfaced as an empty-bodied **403 rather than a 500**. `RateLimitFilter` is
  registered before `UsernamePasswordAuthenticationFilter`, so it runs upstream of
  `ExceptionTranslationFilter` and an uncaught throw should have escaped as a 500. Something is
  catching it. Worth finding — and the design question underneath is worth answering deliberately:
  **when the rate limiter's store is unreachable, fail open or fail closed?** Failing closed turns a
  Redis outage into a total outage. Failing open drops the ceiling exactly when the system is already
  unhealthy. And the answer probably shouldn't be the same for idempotency, where failing open means
  charging a customer twice.
- **Still no CI.** Nineteen tests that run when someone remembers to run them. This is now the
  largest gap in the project.
## Day 13 — Docker and docker-compose (15–17 Aug 2026)

**Built**
- **`Dockerfile`, multi-stage.** Stage one is `maven:3.9-eclipse-temurin-21` and compiles the jar;
  stage two is `eclipse-temurin:21-jre` and runs it, pulling the artifact across with
  `COPY --from=build /app/target/*.jar`. `-DskipTests`, because the tests need Postgres and Redis
  and neither exists during an image build.
- **Layer ordering that matters.** `COPY pom.xml` and `mvn dependency:go-offline` come *before*
  `COPY src`, so the dependency layer caches. It took 154.9 seconds the first time and zero on
  every rebuild since.
- **`.dockerignore`** — `target/`, `.git/`, `.idea/`, `*.iml`, `.DS_Store`, `docs/`.
- **`docker-compose.yml`** — three services: `postgres:18`, `redis:8-alpine`, `app`. Healthchecks
  with `condition: service_healthy`. Named volume on Postgres only. Only `app` publishes 8080.
- **`application.yaml`** — datasource parameterised: `${DB_HOST:localhost}`, `${DB_NAME:transakt}`,
  `${DB_USER:aman}`, `${DB_PASSWORD:}`. Local behaviour unchanged; compose supplies its own values.

**Verified 17 Aug 01:54** — `Successfully applied 4 migrations to schema "public", now at version v4`,
`Started TransaktApplication in 4.43 seconds`, and `curl -i http://localhost:8080/api/v1/health`
returning 200 from the Mac. V1–V4 ran as real SQL against a completely empty containerised Postgres
18.6 — no baselining, no shortcuts.

**Why**
Running Transakt required three manual preconditions: Postgres.app started, `redis-server` alive in
its own terminal tab, and `JAVA_HOME` pinned to temurin-21. Every one of those is a thing a new
machine doesn't have and a thing I'd have to explain to anyone I handed the repo to. "Works on my
machine" is not a property of the code; it's a property of the machine.

There's a second reason, and it's the one that actually forced the work: Day 14 is impossible without
this. A platform doesn't run your source, it runs an image.

And it closed the loop on Day 12a. That empty containerised Postgres had never seen this project.
The schema arrived correctly anyway, built from migrations, on infrastructure nobody had configured.
That's the whole payoff from moving off `ddl-auto`, demonstrated rather than argued.

**Concepts**
- **Image vs container.** The image is the recipe, the container is the meal. One image, many
  containers, none of them carrying state that matters.
- **Multi-stage builds.** The build needs Maven, a full JDK, and ~200 MB of downloaded dependencies.
  The runtime needs a JRE and one jar. Shipping the first to production means shipping a compiler and
  a package manager to a machine that should only be running code.
- **Layer caching, and why instruction order is a design decision.** Docker caches each instruction
  and invalidates everything below the first change. `COPY src` before dependency resolution would
  re-download every dependency on every one-character code edit. Splitting the copy in two is the
  single highest-leverage line in a Dockerfile.
- **Build context.** Docker ships the entire directory to the daemon *before* running any instruction.
  Without `.dockerignore` that's the full git history and every compiled class, uploaded on each build
  to be ignored.
- **Service names are hostnames.** Inside the compose network, `postgres` resolves. `localhost` does
  not mean "the host machine" — inside the app container it means the app container.
- **`depends_on` is not enough.** It waits for the *container* to exist, not for Postgres to accept
  connections. The app boots in three seconds and would lose that race routinely. Healthchecks with
  `condition: service_healthy` are load-bearing, not decoration.
- **Volumes are a statement about what's worth keeping.** Postgres gets a named volume. Redis
  deliberately doesn't — everything in it has a TTL and is meant to expire.
- **Publishing ports is opt-in.** Only `app` exposes one. Postgres and Redis stay invisible to the
  host, which also avoids colliding with Postgres.app on 5432.

**Interview line**
"I containerised the whole stack — app, Postgres and Redis — so it starts with one command. The two
details worth explaining are ordering the Dockerfile so the dependency layer caches, which took the
rebuild from two and a half minutes to nothing, and using healthchecks rather than `depends_on`,
because `depends_on` only waits for the container to exist and my app boots faster than Postgres
accepts connections."

**Mistakes & fixes**
1. **Docker CLI is not the Docker daemon.** `docker --version` and `docker compose version` are local
   binaries that never contact the engine, so both pass happily while Docker Desktop isn't running.
   `docker info` is the command that proves the daemon is up — it prints a full Client section, then
   fails at `Server:`.
2. **Postgres 18 changed its data directory layout.** The official image now uses major-version
   subdirectories for `pg_ctlcluster` compatibility, so a volume mounted at
   `/var/lib/postgresql/data` is reported as an "unused mount/volume" and the container exits 1.
   Correct config for 18+ is a single mount at `/var/lib/postgresql`, then `docker compose down -v`
   to clear the half-initialised volume. (docker-library/postgres PR 1259, issue 37.)
3. **Compose interleaves every container's output.** Redis's startup banner buried Postgres's fatal
   error completely. `docker compose logs <service>` isolates one.
4. **IntelliJ creates files relative to the selected Project panel node.** `docker-compose.yml` first
   landed inside `target/` — gitignored, wiped by `mvn clean`, and excluded by `.dockerignore`. Click
   the root node first.
5. **Port 8080 conflict, third occurrence.** `lsof -ti :8080 | xargs kill`.

**Also noted:** Flyway logs a benign warning that PostgreSQL 18.4 is newer than it officially
supports. It validated every migration anyway. That wants a Flyway bump eventually, not a Postgres
downgrade.

---

## Day 14 — first deployment (17–23 Aug 2026)

**Live at https://transakt.onrender.com**

**Built**
- **Platform choice: Render.** Railway has had no free tier since 2023 ($5/month minimum). Render
  still has a genuine zero-cost path and asks for no card.
- **Two remaining config gaps** (commit `90a8512`): `server.port: ${PORT:8080}` at the top level, a
  sibling of `spring:`; and `spring.data.redis.password: ${REDIS_PASSWORD:}` alongside host and port.
- **Three Render resources:** web service `transakt` (Docker, Free, Singapore, Auto-Deploy on),
  `transakt-db` (PostgreSQL 18, Free, expires 17 Sept 2026), `transakt-redis` (**Valkey 8**, Free,
  `allkeys-lru`).
- **Environment variables:** `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`,
  `REDIS_HOST`, `REDIS_PORT`. No `REDIS_PASSWORD` — internal authentication is off, so internal
  traffic needs none.
- **Commit `b6045be`** — deleted a `@Bean CommandLineRunner` that had been quietly killing every
  deploy for three days. See below; it's the whole story of this day.

**Verified in production, 22–23 Aug**
`GET /health` 200 · signup 200 with a real UUID and hashed key · login 200 with a JWT ·
`GET /payments` 200, paginated and scoped · `POST /payments` 201 CAPTURED · the same
`Idempotency-Key` replayed returns the same payment · a 25-request loop returns nineteen 200s then
six 429s. Nineteen rather than twenty because the payment created moments earlier had already spent
one slot in that minute — which is itself the proof that the counter is per merchant per minute
across *all* authenticated endpoints, not per endpoint.

**Why**
Everything before this was a claim. Fourteen days of architecture that only runs on one laptop is
indistinguishable, from the outside, from fourteen days of nothing. A URL is evidence.

And it's where Day 12a stops being a design preference and starts being the thing that makes
deployment possible at all. A fresh managed Postgres, four migration files, and the schema builds
itself. Without Flyway this day would have begun with hand-running SQL against a production database.

**Concepts**
- **One artifact, three environments.** Every external address is `${VAR:sensible-default}`. The same
  image runs on the Mac, in compose, and in Singapore; only the environment differs. The defaults
  aren't laziness — they're what keeps local development working with zero setup.
- **Platform-injected PORT.** The host decides the port and tells the app. Binding a hardcoded 8080
  is a contract the platform never agreed to.
- **Internal versus external hostnames.** Render gives both. The internal `dpg-…` / `red-…` form
  never leaves their network and needs no TLS; the external form is public and does. Private
  networking is per-region, which is why all three services had to be in Singapore.
- **Free-tier trade-offs, accepted knowingly.** The web service spins down after 15 minutes idle —
  the first request afterwards measured *over two minutes*, not the 50 seconds advertised. Free
  Postgres expires 30 days after creation. Neither matters much when the schema rebuilds from
  migrations and the loss would be demo data.
- **Valkey.** Render's Key Value is Valkey, the fork created after Redis changed its licence. It's
  wire-compatible, so Lettuce and Spring Data Redis needed no changes at all.
- **Reading a deploy log.** "Exited with status 1" is a wrapper, never a cause. Search for `ERROR`,
  then `Caused by` — don't scroll. And read a Spring stack trace **bottom-up**: the last `Caused by:`
  is the truth, everything above it is framework plumbing.
- **`x-render-routing: no-deploy`** means the service has never had a successful deploy. The mystery
  HTML page served for three days was Render's own 502, not the app.
- **Defaults are not decisions.** Render's Postgres ships with inbound `0.0.0.0/0`; its Key Value
  ships blocking all external traffic. Same platform, two opposite security postures, neither of them
  chosen by me. Worth auditing rather than inheriting.

**Interview line**
"The deployment itself was routine. What's worth talking about is the three days it took to find the
failure. Every deploy exited with status 1 and the platform's error message was a wrapper with no
cause in it. It turned out to be a six-line `CommandLineRunner` I'd added as a debug check on Day 13,
which pinged Redis at startup. A `CommandLineRunner` runs *after* the context refreshes and Tomcat
binds — so the image had built, the database had connected, the migrations had applied and the server
had started, every single time. Then a debug line threw and Spring closed the whole context. The
failure was at the finish line, which is exactly why nothing upstream looked wrong."

**Mistakes & fixes**
1. **The three-day outage, and the assumption underneath it.** My notes said the app would boot fine
   without Redis, reasoning that Lettuce connects lazily and `/health` never touches it. That was
   wrong, and being wrong about it cost the whole week. Because I believed it, I treated the stuck
   Redis instance and the failing deploy as *two independent problems* — and spent days debugging
   ports, build contexts and database config on a failure that Redis was causing all along. The fix
   was deleting six lines. **A wrong assumption doesn't slow you down; it points you somewhere else
   entirely.**
2. **Docker Build Context Directory is a folder, Dockerfile Path is a file.** Pointing the context at
   `./Dockerfile` produced `invalid local: stat /opt/render/project/src/Dockerfile: not a directory`.
   Correct values are `.` and `./Dockerfile` respectively.
3. **Commit `de87094` was a one-line no-op — the intended edits never landed.** Render deploys from
   GitHub, so an unpushed or unsaved change simply doesn't exist to it. The check that closes this
   off is `git show origin/main:src/main/resources/application.yaml`, which reads the file *as
   pushed* rather than the copy on disk.
4. **The Key Value instance that would never provision.** Two instances hung on "Creating" — one for
   six hours — and a third couldn't be created at all. That third failure was the clue: the free tier
   allows one Key Value per workspace, and the stuck one was holding the slot. **Delete first, then
   create.** The replacement was Available in under five minutes.
5. **Both auth doors failing identically pointed downstream of both.** With the app finally running,
   every authenticated endpoint returned an empty-bodied 403 — with a fresh JWT *and* with a fresh
   API key. Two completely different credential paths failing the same way isn't a coincidence in
   each; it's one fault after both. It was `RateLimitFilter` reaching for a Redis that didn't exist
   yet. Testing both doors separately is what split the problem.
6. **A signal that carries two meanings proves neither.** Those 403s came with a `JSESSIONID`, which
   I first read as proof the request had arrived unauthenticated. It isn't: in Spring Security 6 the
   context is persisted to a session when a filter *sets* an Authentication, so the cookie appears
   either way. The log settled it in ten seconds; the inference had been leading the wrong direction
   for twenty minutes.
7. **A sudden 403 mid-test, again.** Same lesson as Day 9, learned again the hard way: the token had
   passed its one-hour expiry. Check the token age before anything else.
8. **An interactive prompt is not a shell.** Twice during SSH setup, the next command was pasted in
   as the *answer* to a question — once to `ssh-keygen`'s "where to save the key", once to ssh's
   "continue connecting?". Terminal looks identical either way. If the line ends in `?` or `:` rather
   than `%`, answer it first. Passphrase prompts also echo nothing while you type, which reads as a
   frozen window.
9. **GitHub HTTPS credentials expire.** `Password authentication is not supported for Git
   operations`. Switched the remote to SSH with an ed25519 key, which doesn't expire.

**Still open**
- The Redis failure surfaced as an empty-bodied **403 rather than a 500**. `RateLimitFilter` is
  registered before `UsernamePasswordAuthenticationFilter`, so it runs upstream of
  `ExceptionTranslationFilter` and an uncaught throw should have escaped as a 500. Something is
  catching it. Worth finding — and the design question underneath is worth answering deliberately:
  **when the rate limiter's store is unreachable, fail open or fail closed?** Failing closed turns a
  Redis outage into a total outage. Failing open drops the ceiling exactly when the system is already
  unhealthy. And the answer probably shouldn't be the same for idempotency, where failing open means
  charging a customer twice.
- **Still no CI.** Nineteen tests that run when someone remembers to run them. This is now the
  largest gap in the project.


---

## Day 16 — the 403 that was hiding a 500, and a resilience decision (24 Aug 2026)

**Commits:** `34419da` — "fix: permit /error so failures report their real cause"
`19a1c53` — "test: assert the rate limiter fails open when Redis is down"

**Built**
- **`.requestMatchers("/error").permitAll()`** as the first matcher in `SecurityConfig` — one line.
- **`RateLimitService.isAllowed` now fails open.** The Redis calls are wrapped in a `try`, the
  `catch` is `RedisConnectionFailureException` specifically, and it logs an `@Slf4j` warning naming
  the merchant before returning `true`.
- **`RateLimitServiceTest`** — the project's first genuine unit test, asserting that a request is
  allowed when Redis throws.

**Verified end to end.** With Redis stopped and the app running locally:
`HTTP/1.1 403` before the first fix, `HTTP/1.1 500` with a real JSON body after it, and
`HTTP/1.1 200` plus a `WARN` line after the second. Three states, same request, in the space of
half an hour. `./mvnw test` → **Tests run: 20, Failures: 0**.

---

### Part one — the 403 that was hiding a 500

**Why**
Since Day 14 a Redis outage had produced an empty-bodied 403 on every authenticated endpoint. That
is a lie in two directions at once: it implies a permissions problem, and it hides an outage. Anyone
debugging it — including me, for three days on Render — starts by looking at credentials.

**What it turned out to be, and it wasn't what I predicted.** I was sure something was catching the
`RedisConnectionFailureException` and converting it. Nothing was. `grep -rn "catch"` across all three
filters returned exactly one hit, in `JwtAuthFilter`, and `filterChain.doFilter` sits *outside* that
try block — so it can only ever catch its own JWT parsing, never anything downstream.

The real sequence, from the stack trace:

1. `RateLimitService.isAllowed` calls INCR, Redis is gone, the exception is thrown
2. It escapes every filter and reaches Tomcat — the trace shows no `catch` anywhere on the way up
3. Tomcat sets **500** and dispatches internally to `/error`
4. **That dispatch runs through the security filter chain again** — it is a fresh request
5. But `JwtAuthFilter` and `ApiKeyFilter` extend `OncePerRequestFilter`, whose
   `shouldNotFilterErrorDispatch()` returns `true` by default. **They skip error dispatches.**
6. So `/error` arrives with nobody authenticated, `anyRequest().authenticated()` denies it, and the
   resulting **403 overwrites the 500** the client never sees

**The error page, whose entire job is reporting failures, was itself failing a permissions check.**

That also closed the two things that had puzzled me on Render two days earlier: the empty body (a
Spring Security denial has none) and the `JSESSIONID` (the anonymous-denial path saves the request
for a post-login redirect). Both were signatures of the *error dispatch*, not of the original
request.

**Concepts**
- **An error dispatch is a new request through the whole chain.** Spring Boot registers the security
  filter chain for `REQUEST`, `ERROR` and `ASYNC` — but `OncePerRequestFilter` opts out of the error
  case by default. That asymmetry is the entire bug.
- **`/error` needs `permitAll`** in any app with `anyRequest().authenticated()`. Otherwise every
  unhandled exception in the application comes back as a permissions failure.
- **A status code can be overwritten after it's set.** The 500 was real for the whole three days; it
  just never reached the client.

---

### Part two — fail open or fail closed

**Why**
Fixing the visibility left the behaviour still wrong. A Redis outage produced a 500 on every
authenticated request — honest, but it meant **losing the cache took the entire API down**, including
reads that don't need Redis at all. The rate limiter exists to protect availability, and in that
state it was the single thing destroying it.

**The decision: fail open.** When the store is unreachable, the request is allowed and a warning is
logged.

The reasoning is that a control protecting availability must never itself become the cause of an
outage. Failing closed converts a cache outage into a total outage — and Redis is the least durable
component in the system, so that trade is badly priced. Cloudflare and most CDN rate limiters behave
the same way.

**And the answer is different for idempotency**, which is the part worth understanding. Letting a
request past a dead rate limiter costs a burst of traffic. Letting a retry past a dead idempotency
check **charges a customer twice** — the exact harm the feature exists to prevent. Same
infrastructure, same failure mode, opposite correct answers. That asymmetry is also the strongest
argument for eventually moving idempotency keys into Postgres with a unique index, written inside the
payment transaction, where they cannot fail independently of the payment they protect.

**Concepts**
- **Catch the specific exception.** `RedisConnectionFailureException` means one thing: the store is
  unreachable. `catch (Exception e)` would also swallow a bug in the key-building code and silently
  let everything through — a rate limiter that has quietly stopped limiting, with no signal.
- **Fail-open without a log is silent degradation.** The `WARN` line is what makes the difference
  between a deliberate trade-off and a control that stopped working without telling anyone.
- **The right question is not "how do I handle this error."** It's "what is the worst thing that
  happens if this control silently stops working?" For rate limiting, a traffic spike. For
  idempotency, a double charge. Ask it per feature, not per dependency.
- **This is where a unit test is correct** — and it's the opposite of the Day 11 argument. Integration
  tests won then because the behaviour lived in the wiring. Here the behaviour is one catch block, and
  exercising it requires Redis to fail *on demand*, which a mocked `StringRedisTemplate` does cleanly
  and a real one does not. Same principle both times: test at the level where the behaviour actually
  lives.

**Interview line**
"A Redis outage was returning 403 on every authenticated endpoint, which reads as a permissions
problem and isn't. Nothing was catching the exception — Tomcat was setting 500 and dispatching to
`/error`, and because `OncePerRequestFilter` skips error dispatches, that dispatch arrived
unauthenticated and got denied. The error page was failing its own permissions check and overwriting
the real status. One line fixed the visibility. Then the actual design question: should the rate
limiter fail open or closed? I went open, because a control that protects availability shouldn't
cause an outage — but deliberately kept the opposite answer for idempotency, where failing open means
charging someone twice."

**Mistakes & fixes**
1. **I predicted the wrong cause and said so with confidence.** The theory was a `try/catch` wrapping
   `chain.doFilter` in an auth filter — plausible, and wrong. What settled it was reproducing the
   failure locally with a debugger attached instead of reasoning from a remote log. **Local
   reproduction of a remote bug is worth the setup cost**: full stack trace, no truncation, instant
   retry.
2. **`git commit` skipped in a `&&` chain.** `cp` failed silently, but `git add && git commit && git
   push` ran anyway on an unchanged file and reported success. **`&&` only stops on the failure of the
   command immediately before it** — check the insertion count, not the exit code.
3. **Wrong directory again** (5th time): `fatal: not a git repository` from `~`. The alias exists;
   the habit doesn't yet.
4. **Redis left stopped after testing**, so the next `./mvnw test` failed with seven errors in
   `clearRedis` — the Day 11 `@BeforeEach flushDb()`. Not a regression: the integration tests
   correctly refuse to run in a broken environment. Worth noting the contrast, though — **the test
   suite fails loudly when Redis is missing while the application now degrades quietly. Both are
   right, for opposite reasons.**

**Still open**
- Idempotency keys still live in Redis and still inherit its durability. Moving them to Postgres with
  a unique index inside the payment transaction is the proper fix.
- The `catch (Exception e)` in `JwtAuthFilter` is wider than it needs to be. It should be
  `JwtException` — a `NullPointerException` in `extractRole` would currently deauthenticate silently
  rather than failing loudly.
---

## Day 16 — the 403 that was hiding a 500, and a resilience decision (24 Aug 2026)

**Commits:** `34419da` — "fix: permit /error so failures report their real cause"
`19a1c53` — "test: assert the rate limiter fails open when Redis is down"

**Built**
- **`.requestMatchers("/error").permitAll()`** as the first matcher in `SecurityConfig` — one line.
- **`RateLimitService.isAllowed` now fails open.** The Redis calls are wrapped in a `try`, the
  `catch` is `RedisConnectionFailureException` specifically, and it logs an `@Slf4j` warning naming
  the merchant before returning `true`.
- **`RateLimitServiceTest`** — the project's first genuine unit test, asserting that a request is
  allowed when Redis throws.

**Verified end to end.** With Redis stopped and the app running locally:
`HTTP/1.1 403` before the first fix, `HTTP/1.1 500` with a real JSON body after it, and
`HTTP/1.1 200` plus a `WARN` line after the second. Three states, same request, in the space of
half an hour. `./mvnw test` → **Tests run: 20, Failures: 0**.

---

### Part one — the 403 that was hiding a 500

**Why**
Since Day 14 a Redis outage had produced an empty-bodied 403 on every authenticated endpoint. That
is a lie in two directions at once: it implies a permissions problem, and it hides an outage. Anyone
debugging it — including me, for three days on Render — starts by looking at credentials.

**What it turned out to be, and it wasn't what I predicted.** I was sure something was catching the
`RedisConnectionFailureException` and converting it. Nothing was. `grep -rn "catch"` across all three
filters returned exactly one hit, in `JwtAuthFilter`, and `filterChain.doFilter` sits *outside* that
try block — so it can only ever catch its own JWT parsing, never anything downstream.

The real sequence, from the stack trace:

1. `RateLimitService.isAllowed` calls INCR, Redis is gone, the exception is thrown
2. It escapes every filter and reaches Tomcat — the trace shows no `catch` anywhere on the way up
3. Tomcat sets **500** and dispatches internally to `/error`
4. **That dispatch runs through the security filter chain again** — it is a fresh request
5. But `JwtAuthFilter` and `ApiKeyFilter` extend `OncePerRequestFilter`, whose
   `shouldNotFilterErrorDispatch()` returns `true` by default. **They skip error dispatches.**
6. So `/error` arrives with nobody authenticated, `anyRequest().authenticated()` denies it, and the
   resulting **403 overwrites the 500** the client never sees

**The error page, whose entire job is reporting failures, was itself failing a permissions check.**

That also closed the two things that had puzzled me on Render two days earlier: the empty body (a
Spring Security denial has none) and the `JSESSIONID` (the anonymous-denial path saves the request
for a post-login redirect). Both were signatures of the *error dispatch*, not of the original
request.

**Concepts**
- **An error dispatch is a new request through the whole chain.** Spring Boot registers the security
  filter chain for `REQUEST`, `ERROR` and `ASYNC` — but `OncePerRequestFilter` opts out of the error
  case by default. That asymmetry is the entire bug.
- **`/error` needs `permitAll`** in any app with `anyRequest().authenticated()`. Otherwise every
  unhandled exception in the application comes back as a permissions failure.
- **A status code can be overwritten after it's set.** The 500 was real for the whole three days; it
  just never reached the client.

---

### Part two — fail open or fail closed

**Why**
Fixing the visibility left the behaviour still wrong. A Redis outage produced a 500 on every
authenticated request — honest, but it meant **losing the cache took the entire API down**, including
reads that don't need Redis at all. The rate limiter exists to protect availability, and in that
state it was the single thing destroying it.

**The decision: fail open.** When the store is unreachable, the request is allowed and a warning is
logged.

The reasoning is that a control protecting availability must never itself become the cause of an
outage. Failing closed converts a cache outage into a total outage — and Redis is the least durable
component in the system, so that trade is badly priced. Cloudflare and most CDN rate limiters behave
the same way.

**And the answer is different for idempotency**, which is the part worth understanding. Letting a
request past a dead rate limiter costs a burst of traffic. Letting a retry past a dead idempotency
check **charges a customer twice** — the exact harm the feature exists to prevent. Same
infrastructure, same failure mode, opposite correct answers. That asymmetry is also the strongest
argument for eventually moving idempotency keys into Postgres with a unique index, written inside the
payment transaction, where they cannot fail independently of the payment they protect.

**Concepts**
- **Catch the specific exception.** `RedisConnectionFailureException` means one thing: the store is
  unreachable. `catch (Exception e)` would also swallow a bug in the key-building code and silently
  let everything through — a rate limiter that has quietly stopped limiting, with no signal.
- **Fail-open without a log is silent degradation.** The `WARN` line is what makes the difference
  between a deliberate trade-off and a control that stopped working without telling anyone.
- **The right question is not "how do I handle this error."** It's "what is the worst thing that
  happens if this control silently stops working?" For rate limiting, a traffic spike. For
  idempotency, a double charge. Ask it per feature, not per dependency.
- **This is where a unit test is correct** — and it's the opposite of the Day 11 argument. Integration
  tests won then because the behaviour lived in the wiring. Here the behaviour is one catch block, and
  exercising it requires Redis to fail *on demand*, which a mocked `StringRedisTemplate` does cleanly
  and a real one does not. Same principle both times: test at the level where the behaviour actually
  lives.

**Interview line**
"A Redis outage was returning 403 on every authenticated endpoint, which reads as a permissions
problem and isn't. Nothing was catching the exception — Tomcat was setting 500 and dispatching to
`/error`, and because `OncePerRequestFilter` skips error dispatches, that dispatch arrived
unauthenticated and got denied. The error page was failing its own permissions check and overwriting
the real status. One line fixed the visibility. Then the actual design question: should the rate
limiter fail open or closed? I went open, because a control that protects availability shouldn't
cause an outage — but deliberately kept the opposite answer for idempotency, where failing open means
charging someone twice."

**Mistakes & fixes**
1. **I predicted the wrong cause and said so with confidence.** The theory was a `try/catch` wrapping
   `chain.doFilter` in an auth filter — plausible, and wrong. What settled it was reproducing the
   failure locally with a debugger attached instead of reasoning from a remote log. **Local
   reproduction of a remote bug is worth the setup cost**: full stack trace, no truncation, instant
   retry.
2. **`git commit` skipped in a `&&` chain.** `cp` failed silently, but `git add && git commit && git
   push` ran anyway on an unchanged file and reported success. **`&&` only stops on the failure of the
   command immediately before it** — check the insertion count, not the exit code.
3. **Wrong directory again** (5th time): `fatal: not a git repository` from `~`. The alias exists;
   the habit doesn't yet.
4. **Redis left stopped after testing**, so the next `./mvnw test` failed with seven errors in
   `clearRedis` — the Day 11 `@BeforeEach flushDb()`. Not a regression: the integration tests
   correctly refuse to run in a broken environment. Worth noting the contrast, though — **the test
   suite fails loudly when Redis is missing while the application now degrades quietly. Both are
   right, for opposite reasons.**

**Still open**
- Idempotency keys still live in Redis and still inherit its durability. Moving them to Postgres with
  a unique index inside the payment transaction is the proper fix.
- The `catch (Exception e)` in `JwtAuthFilter` is wider than it needs to be. It should be
  `JwtException` — a `NullPointerException` in `extractRole` would currently deauthenticate silently
  rather than failing loudly.

---

## Day 17 — idempotency moves into the transaction (27 Aug 2026)

**Commits:** `1d0914a` V5 migration · `4291a9a` entity and repository · `62213d7` the swap ·
`3c725e3` dead code removed
Also `5f83eb1` — narrowed the `JwtAuthFilter` catch, carried over from Day 16's open list.

**Built**
- **`V5__add_idempotency_keys.sql`** — five columns, a primary key, a foreign key to `payments`,
  and the one that matters: `UNIQUE (merchant_id, idempotency_key)`.
- **`IdempotencyKey` and `IdempotencyKeyRepository`** — one derived query,
  `findByMerchantIdAndIdempotencyKey`.
- **`PaymentService.create` now takes the idempotency key** and writes that row inside the same
  `@Transactional` method as the payment and both ledger entries.
- **`IdempotencyService` went from four methods to one.**

**Verified with Redis stopped.** Same request twice, same `Idempotency-Key`:

```
{"id":"9173429d-...","createdAt":"2026-08-27T05:28:33.472017Z"}
{"id":"9173429d-...","createdAt":"2026-08-27T05:28:33.472017Z"}
```

The identical `createdAt` is the proof — a second payment would carry a fresh `Instant.now()`.
Hibernate logged an INSERT on the first and a SELECT on the second. `./mvnw test` → 20 green.

---

### Why

Idempotency keys lived in Redis; payments live in Postgres. Two stores that fail independently, and
the guarantee stretched across both. That produced two distinct failures, and I had only really
noticed the first:

**If Redis loses data, a retry creates a second payment.** A restart, an eviction, no persistence —
the key vanishes and the next retry looks like a fresh request. **A double charge.**

**If the app dies mid-request, the key is stuck.** `tryReserve` ran *before* `PaymentService.create`
was entered; `storeResult` ran *after* it returned. So there was a window where the payment had
committed but the key still said `IN_PROGRESS`. The merchant retries, gets a 409 for 24 hours, and
can never retrieve the payment they were charged for. Reading `PaymentController` alongside
`PaymentService` is what made this obvious — **the claim and the payment were never atomic, not even
in principle.**

### What replaced it

Both rows now commit together or not at all. The interesting consequence is how much code that
deletes:

- **No `IN_PROGRESS`.** It existed only to say "I've claimed this but can't prove the outcome yet."
  Inside one transaction there is no such moment.
- **No `release()`.** It was the compensating action for a two-phase commit hand-rolled across two
  databases. Rollback does it now.
- **No 409.** A concurrent duplicate blocks at the database until the first commits, then receives the
  original payment. `IdempotencyConflictException` and its handler were deleted — the state is
  unreachable.

Four methods became one. **When a design is right, code disappears rather than accumulating.**

### Concepts

- **A unique constraint is a coordination primitive.** The race isn't prevented — it's detected by
  the database and recovered from. That's strictly better than a lock the application maintains,
  because the database is already the thing that serialises writes.
- **Catch the violation *outside* the transactional method.** Once a constraint fires, the transaction
  is rollback-only; catching it inside means every subsequent query in that transaction fails too. The
  controller is the right place — Spring has already rolled back cleanly and the re-read runs in a
  fresh transaction.
- **`orElseThrow(() -> e)` narrows without string-matching.** `DataIntegrityViolationException` covers
  any constraint, not just ours — a null in a `NOT NULL` column throws the same type. Instead of
  parsing constraint names out of the message, ask the database: *is the key there now?* If yes, we
  lost a race, return the winner's payment. If no, it was a different violation entirely — rethrow.
  Self-verifying.
- **`paymentId` is a `String`, not a `@ManyToOne`.** This row is a receipt, not a node in an object
  graph. A relationship mapping invites lazy loading of a whole `Payment` that nothing needs. The
  foreign key still enforces the reference; the entity just doesn't pretend it's a navigation path.
- **Tests written against the contract survive a rewrite of the implementation.** All five existing
  idempotency tests passed unchanged while the entire storage layer was replaced underneath them.
  That is the payoff for testing through HTTP instead of mocking the service — and the reason to keep
  doing it.

**Interview line**
"Idempotency keys were in Redis and payments in Postgres, so the guarantee spanned two stores that
fail independently — if Redis lost data, a retry became a double charge, and if the app died between
the two writes, the key was stuck at IN_PROGRESS and the merchant could never retrieve the payment
they'd been charged for. I moved the keys into Postgres with a unique constraint on
`(merchant_id, idempotency_key)`, written inside the same transaction as the payment. The race is
now detected by the database rather than prevented by my code: a concurrent duplicate blocks, gets a
constraint violation, and I re-read and return the original payment. The IN_PROGRESS state, the
release path and the 409 all stopped existing — four service methods became one."

**Mistakes & fixes**
1. **I assumed the idempotency calls were in `PaymentService`** and asked for the wrong file first.
   They were in the controller — which turned out to be the whole point, since that placement was
   what made the two writes non-atomic. **Reading both files together found a design flaw that
   reading either alone would have missed.**
2. **Redis left down again after the previous session's testing**, so the first `./mvnw test` failed
   with seven `clearRedis` errors. Second time this week.
3. **Ran `redis-server` in the working tab** and lost the shell. `Cmd+T` for a new one; renaming the
   Redis tab would stop this recurring.

**Still open**
- **No TTL.** Redis expired keys after 24 hours for free; Postgres won't. Rows accumulate
  indefinitely. A scheduled delete of rows older than 24 hours is the fix — until then this is a slow
  leak, and it's in the known limitations for a reason.
- **The race path is untested.** `IdempotencyIntegrationTest` is `@Transactional`, so a constraint
  violation would poison the test's own transaction. The catch block in `PaymentController` is
  therefore unexercised. Testing it properly needs two real concurrent requests against a committed
  database — worth doing, not trivial.

---

## Day 17 — idempotency moves into the transaction (27 Aug 2026)

**Commits:** `1d0914a` V5 migration · `4291a9a` entity and repository · `62213d7` the swap ·
`3c725e3` dead code removed
Also `5f83eb1` — narrowed the `JwtAuthFilter` catch, carried over from Day 16's open list.

**Built**
- **`V5__add_idempotency_keys.sql`** — five columns, a primary key, a foreign key to `payments`,
  and the one that matters: `UNIQUE (merchant_id, idempotency_key)`.
- **`IdempotencyKey` and `IdempotencyKeyRepository`** — one derived query,
  `findByMerchantIdAndIdempotencyKey`.
- **`PaymentService.create` now takes the idempotency key** and writes that row inside the same
  `@Transactional` method as the payment and both ledger entries.
- **`IdempotencyService` went from four methods to one.**

**Verified with Redis stopped.** Same request twice, same `Idempotency-Key`:

```
{"id":"9173429d-...","createdAt":"2026-08-27T05:28:33.472017Z"}
{"id":"9173429d-...","createdAt":"2026-08-27T05:28:33.472017Z"}
```

The identical `createdAt` is the proof — a second payment would carry a fresh `Instant.now()`.
Hibernate logged an INSERT on the first and a SELECT on the second. `./mvnw test` → 20 green.

---

### Why

Idempotency keys lived in Redis; payments live in Postgres. Two stores that fail independently, and
the guarantee stretched across both. That produced two distinct failures, and I had only really
noticed the first:

**If Redis loses data, a retry creates a second payment.** A restart, an eviction, no persistence —
the key vanishes and the next retry looks like a fresh request. **A double charge.**

**If the app dies mid-request, the key is stuck.** `tryReserve` ran *before* `PaymentService.create`
was entered; `storeResult` ran *after* it returned. So there was a window where the payment had
committed but the key still said `IN_PROGRESS`. The merchant retries, gets a 409 for 24 hours, and
can never retrieve the payment they were charged for. Reading `PaymentController` alongside
`PaymentService` is what made this obvious — **the claim and the payment were never atomic, not even
in principle.**

### What replaced it

Both rows now commit together or not at all. The interesting consequence is how much code that
deletes:

- **No `IN_PROGRESS`.** It existed only to say "I've claimed this but can't prove the outcome yet."
  Inside one transaction there is no such moment.
- **No `release()`.** It was the compensating action for a two-phase commit hand-rolled across two
  databases. Rollback does it now.
- **No 409.** A concurrent duplicate blocks at the database until the first commits, then receives the
  original payment. `IdempotencyConflictException` and its handler were deleted — the state is
  unreachable.

Four methods became one. **When a design is right, code disappears rather than accumulating.**

### Concepts

- **A unique constraint is a coordination primitive.** The race isn't prevented — it's detected by
  the database and recovered from. That's strictly better than a lock the application maintains,
  because the database is already the thing that serialises writes.
- **Catch the violation *outside* the transactional method.** Once a constraint fires, the transaction
  is rollback-only; catching it inside means every subsequent query in that transaction fails too. The
  controller is the right place — Spring has already rolled back cleanly and the re-read runs in a
  fresh transaction.
- **`orElseThrow(() -> e)` narrows without string-matching.** `DataIntegrityViolationException` covers
  any constraint, not just ours — a null in a `NOT NULL` column throws the same type. Instead of
  parsing constraint names out of the message, ask the database: *is the key there now?* If yes, we
  lost a race, return the winner's payment. If no, it was a different violation entirely — rethrow.
  Self-verifying.
- **`paymentId` is a `String`, not a `@ManyToOne`.** This row is a receipt, not a node in an object
  graph. A relationship mapping invites lazy loading of a whole `Payment` that nothing needs. The
  foreign key still enforces the reference; the entity just doesn't pretend it's a navigation path.
- **Tests written against the contract survive a rewrite of the implementation.** All five existing
  idempotency tests passed unchanged while the entire storage layer was replaced underneath them.
  That is the payoff for testing through HTTP instead of mocking the service — and the reason to keep
  doing it.

**Interview line**
"Idempotency keys were in Redis and payments in Postgres, so the guarantee spanned two stores that
fail independently — if Redis lost data, a retry became a double charge, and if the app died between
the two writes, the key was stuck at IN_PROGRESS and the merchant could never retrieve the payment
they'd been charged for. I moved the keys into Postgres with a unique constraint on
`(merchant_id, idempotency_key)`, written inside the same transaction as the payment. The race is
now detected by the database rather than prevented by my code: a concurrent duplicate blocks, gets a
constraint violation, and I re-read and return the original payment. The IN_PROGRESS state, the
release path and the 409 all stopped existing — four service methods became one."

**Mistakes & fixes**
1. **I assumed the idempotency calls were in `PaymentService`** and asked for the wrong file first.
   They were in the controller — which turned out to be the whole point, since that placement was
   what made the two writes non-atomic. **Reading both files together found a design flaw that
   reading either alone would have missed.**
2. **Redis left down again after the previous session's testing**, so the first `./mvnw test` failed
   with seven `clearRedis` errors. Second time this week.
3. **Ran `redis-server` in the working tab** and lost the shell. `Cmd+T` for a new one; renaming the
   Redis tab would stop this recurring.

**Still open**
- **No TTL.** Redis expired keys after 24 hours for free; Postgres won't. Rows accumulate
  indefinitely. A scheduled delete of rows older than 24 hours is the fix — until then this is a slow
  leak, and it's in the known limitations for a reason.
- **The race path is untested.** `IdempotencyIntegrationTest` is `@Transactional`, so a constraint
  violation would poison the test's own transaction. The catch block in `PaymentController` is
  therefore unexercised. Testing it properly needs two real concurrent requests against a committed
  database — worth doing, not trivial.

## Day 18 — the bank seam (28 Aug 2026)

**Built**
- `BankClient` interface, plus `FakeBankClient` implementing it with a configurable
  latency (200–800ms) and a configurable decline rate.
- `PaymentService.create` split into two transactional methods: `createPending`
  (writes the payment row as PENDING, plus the idempotency key) and `settle`
  (sets the final status and writes ledger entries if approved).
- A new `PaymentProcessor` that orchestrates the three steps.
- Ledger entries moved inside the approved branch. A declined payment now writes
  no ledger rows at all.
- Two new tests pinning the state machine. 22 tests green.

The shape:

```
createPending()    ← transaction 1: payment row + idempotency key
bank.authorize()   ← NO transaction. 200–800ms.
settle()           ← transaction 2: final status + ledger entries
```

**Why**

Two reasons, and the second is the real one.

1. A payment gateway that never talks to a bank isn't a payment gateway.
   `BankClient` is the seam where a real acquirer would plug in. Nothing above it
   knows that today's implementation flips a weighted coin.

2. The bank call takes up to 800ms. If it sat inside a single `@Transactional`
   method, a pooled database connection would be held open and idle for that
   entire round trip. The pool has a fixed size. Under load, every in-flight
   payment squats on a connection while waiting on someone else's server, the
   pool empties, and endpoints with nothing to do with payments start failing on
   connection acquisition. So the transaction is split, with the slow call in
   the gap.

**Concepts**
- *Transaction boundary vs request boundary.* They are separate things and do not
  have to line up. One HTTP request here spans two transactions.
- *A transaction should contain database work and nothing else.* Anything remote,
  slow, or capable of hanging belongs outside it.
- *Ports and adapters.* `BankClient` is the port. `FakeBankClient` is one adapter;
  a real acquirer client would be another. Swapping it is a config change.
- *Self-invocation and the proxy, again.* Both new methods carry `@Transactional`,
  which Spring implements by wrapping the bean in a proxy. Had `PaymentService`
  called its own `settle`, the call would hit the object directly, skip the proxy,
  and run with no transaction at all — silently. `PaymentProcessor` existing as a
  separate bean is what makes those annotations do anything. Same reason the
  Day 10 idempotency orchestration was pushed up into the controller.
- *Declines are not movements.* Double-entry means every ledger row records money
  that actually moved. A declined payment moved nothing, so it writes nothing.
  Before this, the ledger was recording intent rather than fact.
- *Failure now has a shape.* Two transactions means there is a real state between
  them. A crash after `createPending` and before `settle` strands a payment at
  PENDING. Nothing sweeps those up yet.

**Interview line**

"The bank call takes up to 800ms, so I split payment creation into two
transactions with the external call in the gap between them. Holding a pooled
database connection open while you wait on a third party is how you drain the
pool and take down endpoints that have nothing to do with payments."

**Mistake & fix**

`FakeBankClient` declines a configurable percentage of payments at random. That is
exactly right for poking at it by hand: you see both branches without editing
anything. It is poison in CI. A test that passes or fails on a coin flip teaches
you nothing and trains you to re-run the pipeline instead of reading it. Fix: the
tests set the decline rate explicitly, 0 or 100, so each test drives one branch
deterministically. Randomness is a manual-exploration feature, never a test
feature.

**Still open**
- A payment stranded at PENDING if the process dies between the two transactions.
  No reconciliation job exists.
- Idempotency keys have no TTL and accumulate forever.
- The idempotency race path is untested.
