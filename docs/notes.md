# Transakt — Learning Notes

Concept explanations behind every decision in this project.

---

## Day 1 — Tooling and Git

### Why Java 21 and not Java 26?
Java releases a new version every six months, but only every few are **LTS (Long Term Support)** — 8, 11, 17, 21, 25. Non-LTS releases stop receiving updates after six months, so production systems never run them. Spring, libraries, and cloud platforms all target LTS first.
**Without this choice:** brand-new JDKs frequently break compiler-hooking tools like Lombok, and my practice environment wouldn't match any real codebase.

### Commit vs push
A **commit** saves a snapshot into the local `.git` folder on my machine. A **push** uploads commits that GitHub doesn't have yet. They are separate on purpose: you can commit offline and reorganise history before sharing it.

### .gitignore only blocks untracked files
`.gitignore` means "never start tracking these". It does **not** untrack files Git already knows about. I committed `.idea/` before writing the rule, so the rule did nothing. `git rm -r --cached .idea` removes files from Git's index while leaving them on disk.

### OAuth (seen in the wild)
Authorising IntelliJ to push to GitHub used OAuth: instead of giving JetBrains my GitHub password, GitHub asked *me* to approve a specific set of permissions and issued JetBrains a scoped token. Same pattern I'll implement with Google login in the security phase.

---

## Day 2 — Spring Boot foundations

### Maven and pom.xml
Maven is the build tool. `pom.xml` (Project Object Model) declares project identity (groupId, artifactId, version), dependencies, and build plugins. Maven downloads dependencies from Maven Central into a local `~/.m2` cache and places them on the classpath when compiling and running.

### The parent POM manages versions
My dependencies have no `<version>` tags. `spring-boot-starter-parent` supplies them — it's a curated list pinning hundreds of libraries to versions tested together. Changing one parent version moves the whole stack coherently.
**Without it:** dependency hell — manually matching Spring, Jackson, Tomcat, and Hibernate versions that actually work together.

### Starters
`spring-boot-starter-web` is not one library but a bundle: Spring MVC + embedded Tomcat + Jackson + validation. One dependency line, a complete web stack.

### Auto-configuration — the core Spring Boot idea
`@SpringBootApplication` combines three annotations:
- `@ComponentScan` — scans this class's package and all sub-packages for annotated classes and registers them. **This is why all my code must live under `com.transakt.transakt`.**
- `@EnableAutoConfiguration` — inspects the classpath at startup and applies defaults: "Tomcat is present, so start a web server on 8080; Jackson is present, so configure JSON conversion."
- `@Configuration` — allows this class to define beans itself.

Auto-configuration is conditional: it backs off whenever I define my own bean for the same job. Convention with an override, not magic.

### Embedded Tomcat
Traditional Java deployment meant installing a Tomcat server and deploying a WAR file into it. Spring Boot inverts this: Tomcat is a library *inside* my app, packaged into one executable JAR. `java -jar transakt.jar` runs everything. This is exactly what makes Docker packaging simple later.

### The request lifecycle
1. The client opens a TCP connection to port 8080 and sends raw HTTP text.
2. Tomcat parses those bytes into an `HttpServletRequest` object.
3. The **DispatcherServlet** — Spring MVC's single front door for every request — consults a handler-mapping table built at startup by scanning `@GetMapping` / `@PostMapping` annotations.
4. It invokes my controller method, converting URL segments and the JSON body into Java parameters.
5. My return value is passed to Jackson (because of `@RestController`) and serialised to JSON.
6. Tomcat writes the HTTP response with status 200 and `Content-Type: application/json`.

I wrote none of steps 1, 2, 3, 5, or 6 — that's the framework earning its place.

---

## Day 3 — Layers, beans, and the first domain object

### Layered architecture
- **Controller** — HTTP concerns only: read the request, call a service, return a result.
- **Service** — business logic: generate IDs, enforce rules, coordinate work.
- **Store / Repository** — data access.

**Why:** each layer changes independently. Swapping the HashMap for PostgreSQL will touch only the bottom layer. Business rules can be tested without HTTP. A second entry point (CLI, scheduled job) would reuse the same service.

### Inversion of Control and Dependency Injection
I never write `new MerchantService()`. At startup Spring:
1. scans and finds `@Service MerchantService`,
2. creates **one** instance and stores it in the application context (the IoC container),
3. finds `MerchantController`, whose constructor requires a `MerchantService`,
4. passes the existing instance in.

"Inversion of control" = the framework controls object creation and lifetime, not me. I declare what I need; Spring supplies it.

**Constructor injection** (what I used) beats field injection with `@Autowired`: the field can be `final`, dependencies are visible in the signature, and the object can never exist half-built. It also makes unit testing trivial — pass in a fake service.

### What a "bean" actually is
A bean is simply an object Spring created and manages. `@Service`, `@RestController`, `@Repository`, and `@Component` all register one — they are specialisations of `@Component` whose different names document intent and let tooling target specific layers.

### Lombok
An annotation processor that generates code at compile time. `@Data` produces getters, setters, `toString`, `equals`, and `hashCode` — roughly 120 lines I didn't write. `@NoArgsConstructor` is required by Jackson, which creates an empty object and then calls setters. IntelliJ needs "Enable annotation processing" switched on, otherwise it compiles without the generated methods and reports errors that aren't real.

### Web annotations used
- `@RequestMapping("/api/v1/merchants")` on the class — a shared URL prefix for every method inside.
- `@RequestBody` — Jackson deserialises the JSON body into a `Merchant`, matching JSON keys to field names.
- `@PathVariable` — extracts a URL segment (`{id}`) into a method parameter.

### Server-controlled fields
`id` and `createdAt` are set inside the service, never taken from the client. In a payment system this is a security boundary, not a style preference: a client that can choose its own identifier can overwrite another merchant's record.

### Why the in-memory HashMap must be replaced
Verified by experiment: created a merchant, restarted the app, fetched it — gone.
- Data lives in the JVM heap, so a restart or crash destroys everything.
- Two app instances behind a load balancer would each hold different data.
- No querying — "find by email" would mean scanning every entry.
- `HashMap` is not thread-safe; concurrent requests can corrupt it.
- Memory is finite; a real merchant table doesn't fit in RAM.

A database solves durability, shared state, querying, and concurrency. That is the entire reason JPA and PostgreSQL arrive next.
## Day 4 — Real database with PostgreSQL and JPA

### The problem we solved
The HashMap lost all data on restart. A payment system cannot forget its merchants. We moved storage to PostgreSQL, a database that writes to disk and survives restarts, crashes, and multiple app instances.

### JPA, Hibernate, Spring Data JPA — three names, what each is
- **JPA** is a specification — a standard set of rules for mapping Java objects to database rows. It's an interface, not code.
- **Hibernate** is the implementation of JPA that actually does the work — generating SQL, managing the session.
- **Spring Data JPA** sits on top and removes the boilerplate: you declare repository interfaces and it writes the implementations.

### Entity mapping
`@Entity` marks a class as a database table. `@Id` marks the primary key. `@Column` maps a field to a column and can enforce constraints (`nullable = false`, `unique = true`). Java uses camelCase, SQL uses snake_case, so `@Column(name = "business_name")` bridges the two. `updatable = false` on createdAt makes "never change this after insert" a structural rule, not a hope.

### ddl-auto: update
Hibernate reads the entity classes at startup and creates or alters the tables to match. This is why no CREATE TABLE was ever written by hand. In production this is set to `validate` (verify the schema matches, change nothing) or `none`, with schema changes managed by migration tools like Flyway. `update` is a learning convenience.

### The repository — the magic trick
`MerchantRepository extends JpaRepository<Merchant, String>` is an empty interface. From the two type parameters (the entity and its id type), Spring Data generates save, findById, findAll, deleteById, existsById, count and more, each backed by real SQL. Spring creates a proxy implementing the interface at startup and registers it as a bean. No @Repository annotation is needed — extending JpaRepository is enough.

### Optional
findById returns Optional<Merchant>, not Merchant. This forces the caller to acknowledge the row might not exist. `.orElse(null)` unwraps it, returning null when absent. It is Java's structural answer to null-pointer bugs.

### open-in-view: false
By default Spring keeps the database session open until the HTTP response is fully written, which can trigger surprise queries during JSON serialization and hides N+1 problems. Disabling it keeps database access inside the service layer where it belongs. This is a known Spring interview topic.

### show-sql: true
Prints every SQL statement Hibernate generates to the console. The best learning setting available — you watch exactly what your Java produces, including the insert and select statements behind save() and findById().

## Day 5 — Payments and the double-entry ledger

### The problem
Storing "merchant balance = 500" is unsafe: a bug, a crash mid-update, or two concurrent requests can corrupt it, and there is nothing to check the number against. In a payment system, a silently wrong balance is catastrophic and unauditable.

### Double-entry bookkeeping
The 500-year-old accounting rule that every serious financial system still uses: every movement of money is recorded twice — once as money leaving (a debit), once as money arriving (a credit) — and the two entries are equal. A balance is never stored or edited; it is the sum of all entries for an account. Because the two sides must always net to zero, any corruption makes the books stop balancing, so errors are detectable before money is lost. Entries are append-only: never changed, never deleted, giving a permanent tamper-evident trail.

In Transakt, creating a ₹500 payment writes a CREDIT of 50000 to the merchant's account and a DEBIT of 50000 from the gateway account. Equal and opposite.

### Money as integers, not decimals
Amounts are stored as paise in a Long (₹500 = 50000), never as a decimal rupee value. Floating-point cannot represent values like 0.1 exactly, so decimal arithmetic accumulates rounding errors — unacceptable for money. Integers of the smallest unit are exact; format to rupees only for display.

### Enums for fixed sets of values
PaymentStatus (PENDING, CAPTURED, FAILED) and EntryDirection (DEBIT, CREDIT) are Java enums, not Strings. The compiler then rejects an invalid value like "SUCESS"; a String would store any garbage. @Enumerated(EnumType.STRING) stores the readable word in the database rather than a fragile ordinal number.

### Transactions and @Transactional
Creating a payment is three database writes (the payment plus two ledger entries). If the app crashed after the payment but before the ledger entries, money would have "moved" with no accounting record. A transaction makes the three writes one indivisible unit: either all succeed and commit together, or any failure rolls all of them back. This is the Atomicity in ACID. Spring provides it with a single @Transactional annotation on the method — it opens a transaction on entry and commits on normal return, rolling back on a runtime exception.

### Derived query methods
LedgerEntryRepository declares `List<LedgerEntry> findByPaymentId(String paymentId)` and nothing else. Spring Data reads the method name, understands "find rows where paymentId equals the argument," and generates the SQL. The method name is the query.

### Nested resource URLs
GET /api/v1/payments/{id}/ledger exposes the ledger belonging to a payment — a related sub-resource under its parent. This is standard REST design for expressing "the X that belongs to this Y."

## Day 6 — Validation and global error handling

### The problem
Without validation, the API accepted nonsense (a payment of -500). Without a central error handler, a bad request returned a 5.6 KB stack trace exposing internal class names, with the useful message buried at the bottom.

### DTOs (Data Transfer Objects)
Controllers no longer accept the Payment entity directly. A CreatePaymentRequest holds only the fields a client may send (merchantId, amountPaise, currency) — not id, status, or createdAt. The controller maps the DTO into a Payment entity. Benefits: the client structurally cannot set internal fields; the public API contract is decoupled from the database schema, so one can change without breaking the other; and validation rules live on the DTO.

### Bean Validation
Rules are declared as annotations on the DTO fields: @NotBlank (present and non-empty), @NotNull (present), @Positive (greater than zero). @Valid on the controller parameter activates them — without @Valid the annotations do nothing. Rules are declared once on the data, not scattered as if-checks through the code.

### @RestControllerAdvice — centralized error handling
One class annotated @RestControllerAdvice handles exceptions from every controller. @ExceptionHandler(SomeException.class) maps an exception type to a method that returns a clean response. MethodArgumentNotValidException (thrown by @Valid on failure) is caught and turned into a small {field: message} map with a 400. This replaces scattered try-catch blocks with a single source of truth for error responses.

### ResponseEntity
Lets a handler control both the response body and the HTTP status code precisely — 400 for validation, 404 for not-found.

### Fail loudly, not silently
getById changed from .orElse(null) to .orElseThrow(new ResourceNotFoundException(...)). Returning null forces every caller to remember a null check and causes a mysterious NullPointerException later if they forget. Throwing fails immediately at the real cause with a clear message, which the global handler turns into a proper 404. A missing record is now an explicit 404, not a misleading empty 200.

### Custom exceptions
ResourceNotFoundException extends RuntimeException so it can be thrown anywhere without cluttering method signatures. Its message travels to the global handler.

## Day 7 — API key authentication with Spring Security

### The problem
Every endpoint was open. Anyone who could reach the server could create payments or read any merchant's ledger. For a payment system that is unacceptable — the entire product is trust.

### Two kinds of caller, two kinds of credential
A payment gateway is called by machines and by people, and they authenticate differently. A merchant's backend server proves itself with an API key sent on every request — like a staff badge. A human logging into a dashboard uses email and password and receives a session token (JWT). Today covers the first, which is what protects the payment API itself.

### Spring Security is secure by default
Adding the dependency immediately locked every endpoint and generated a random password at startup. Nothing was configured yet — the framework's stance is "deny everything until told otherwise." The job then is to open specific doors, not to add locks.

### Filters — the guard before the waiter
A filter runs on every request before it reaches any controller. Spring Security is built as a chain of these. ApiKeyFilter extends OncePerRequestFilter, reads the X-API-Key header, looks the key up via findByApiKey, and if it matches a merchant, marks the request authenticated in the SecurityContext, recording which merchant it is. If no key or an unknown key, it does nothing and the request stays unauthenticated. addFilterBefore inserts it into the chain.

### SecurityFilterChain configuration
requestMatchers declares per-path rules: /api/v1/health and /api/v1/merchants are permitAll, anyRequest().authenticated() covers the rest — critically /api/v1/payments. CSRF is disabled because CSRF protection targets browser form posts, not stateless APIs.

### The bootstrap problem
Merchant creation is left open deliberately: a new merchant needs to exist before it can have a key, so requiring a key to get your first key is circular. Real systems solve this with a separate admin or signup path.

### 401 vs 403
401 Unauthorized means "I don't know who you are." 403 Forbidden means "I know a request arrived but you are not permitted." Sending an unrecognised key produced 403 — the filter ran, found no matching merchant, and Security refused the request.

### Key format conventions
Keys are prefixed: tk_ for Transakt, as Stripe uses sk_ and Razorpay rzp_. A prefix makes a key instantly recognisable and easy to scan for if one leaks into logs or a repository.

### Dependency versions can break at runtime, not just compile time
Spring Boot 4.1.0 had no matching spring-boot-starter-security. Pinning the starter to 3.4.1 let the project compile, but at runtime the security library and the core disagreed on where HttpSecurity lived, so the bean could not be found. The correct fix was aligning the whole project to one generation (Boot 3.4.1) and letting the parent POM manage every version — which also required renaming spring-boot-starter-webmvc to spring-boot-starter-web and webmvc-test to test, since artifact names changed between major versions.
