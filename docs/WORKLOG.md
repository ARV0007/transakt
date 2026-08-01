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