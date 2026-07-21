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