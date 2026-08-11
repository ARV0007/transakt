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

---

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

---

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

---

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

---

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

---

## Day 8 — Passwords, JSON Web Tokens, and roles

### The problem
Day 7 closed the payment API to machines, but there is still no way for a *person* to log in. A merchant who wants to open a dashboard and look at their own payments has no credential to offer — a Merchant row has a name, an email, and an API key, and nothing a human could type into a login form.

Handing that human the API key instead would be the wrong move twice over. It never expires, so a leak is permanent. And it carries no notion of role, so there is no way to distinguish a merchant from an admin.

### Why passwords are never stored
If `hunter2` sits in a column and the database leaks, every merchant's password is public. Worse, people reuse passwords, so the leak reaches their email accounts and their banks too.

Instead the database stores a **fingerprint**. On login the incoming plaintext is fingerprinted and the two fingerprints are compared. The original string exists nowhere — not in a column, not in a log, not in a backup.

### Why BCrypt and not SHA-256
**BCrypt is deliberately slow**, and that is the entire feature. A fast hash lets an attacker holding a stolen database try billions of candidates per second. BCrypt takes roughly 100ms per attempt. On a login endpoint that is invisible; against millions of guesses it is ruinous.

**BCrypt salts automatically.** Random noise is mixed into the password before hashing, so two merchants who both chose `hunter2` end up with completely different hashes. Without salting, an attacker precomputes a table of common passwords once and cracks every account in the database simultaneously.

The salt is stored *inside* the hash string, which is why there is no separate salt column and why the salt is never managed by hand.

### Anatomy of the stored value
```
$2a$10$Wrs1LY3CQTEeu2uMI7QMU.ufs2d2/MChD46dl4m0doW7scVCLqP2i
 └┬┘ └┬┘ └───────────────────────┬──────────────────────────┘
  │   │                          └── salt + hash
  │   └── cost factor: 2^10 rounds
  └── algorithm version
```
Always exactly 60 characters. A common production bug is setting the column to VARCHAR(20) while thinking about password *length*, which silently truncates every hash.

### @JsonProperty(WRITE_ONLY)
`POST /api/v1/merchants` is a public signup endpoint. Without this annotation Jackson would serialise the password field into every response, and every hash in the system would be one public URL away.

WRITE_ONLY means Jackson accepts the field coming *in* from a request body and never writes it *out*. Same instinct as the Day 6 DTOs: make the unsafe thing structurally impossible rather than relying on remembering.

### @Configuration versus @Service — two ways Spring learns about beans
`@Service`, `@Component`, `@RestController` all say **"this class *is* a bean"** — Spring scans, finds the annotation, and builds one instance. The class itself is the product.

`@Configuration` says **"this class is a *source of* beans"** — Spring ignores the object and instead calls the `@Bean` methods inside it, keeping whatever they return. The class is a factory.

Why does PasswordEncoder need the factory treatment? Because BCryptPasswordEncoder lives inside Spring Security's jar. I do not own that source file, so I cannot annotate it. A `@Bean` method is the only route into the container.

This pattern will recur for every third-party object from now on — RestTemplate, ObjectMapper, Redis clients, Kafka producers. Anything I did not write arrives through a `@Bean` method.

### Why the encoder lives in its own class
SecurityConfig takes ApiKeyFilter through its constructor. If ApiKeyFilter reached for MerchantService, and MerchantService needed PasswordEncoder, and PasswordEncoder lived inside SecurityConfig, that is a ring with no starting point — Spring refuses to boot with "the dependencies of some of the beans form a cycle."

Putting the bean in its own PasswordConfig breaks the ring, because nothing in that chain depends on PasswordConfig. It is also simply tidier: SecurityConfig's job is describing the filter chain, and password hashing is a separate concern that happens to be security-adjacent.

### Return the interface, not the implementation
The bean method returns `PasswordEncoder`, not `BCryptPasswordEncoder`. Every class that injects it only knows "something that can encode and match passwords." Migrating to Argon2 later changes one line and nothing else notices — the same move that paid off on Day 4, when the controller talked to a repository interface and the HashMap-to-PostgreSQL swap left it untouched.

### What a JWT actually is
Three Base64 chunks joined by dots:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ0ZXN0QHNob3AuY29tIn0.7Kx2mQ...
      header                 payload                  signature
```
The **header** names the algorithm. The **payload** holds the claims — who you are, when the token was issued, when it dies. The **signature** is an HMAC over the first two parts, computed with a secret only the server holds.

### The payload is encoded, not encrypted
This is the part almost everyone gets wrong. Anyone holding the token can decode the payload and read every claim — paste one into jwt.io and the data appears in plain text.

The signature hides nothing. It proves nothing was **altered**: change one character of the payload and the recomputed signature stops matching, so the server rejects it.

The rule that follows is absolute — never put a secret in a token. No passwords, no card numbers. `merchantId` is fine because an identifier is not a credential.

### Standard claims versus custom claims
`sub` (subject), `iat` (issued at) and `exp` (expiration) are RFC-standard, which is why jjwt gives them dedicated builder methods and checks `exp` automatically when parsing.

`merchantId` and `role` are custom — arbitrary key/value pairs added by hand. They are the whole payoff over API keys: when a request arrives, the filter reads the merchant's identity and permission level straight out of the token, with no database lookup. That is what "stateless" means in practice, and why authentication cost stays flat as the merchant table grows.

### Why the signing key is built once
`Keys.hmacShaKeyFor(...)` sits in the constructor, not inside generateToken. Deriving a SecretKey from a string is real computation, and doing it per login would be waste. Build once at startup, reuse forever — the same instinct as the PasswordEncoder bean.

The field is `final` for a reason beyond style: if the signing key could change at runtime, every token already issued would silently stop verifying.

### Why the secret must be at least 32 characters
HS256 needs a 256-bit key, and 256 ÷ 8 = 32 bytes. jjwt checks this at startup and throws WeakKeyException rather than quietly shipping weak crypto.

The value is read as `${JWT_SECRET:default}` — Spring's syntax for "use the environment variable if it exists, otherwise fall back to this literal." The repository is public, so the committed value is a dev throwaway and says so in its own text; production sets the real secret as an environment variable that never touches Git.

### Verification cannot be skipped
```java
Jwts.parser()
    .verifyWith(key)      // not optional
    .build()
    .parseSignedClaims(token)
    .getPayload();
```
There is no path in this API that hands back claims without checking the signature first. A tampered or expired token throws before anything downstream sees the data. The library removes the unsafe path rather than documenting it as a bad idea.

### Maven runtime scope as an API guardrail
jjwt ships as three artifacts. `jjwt-api` holds the interfaces and is compile scope, because my code imports from it. `jjwt-impl` and `jjwt-jackson` are **runtime** scope: on the classpath when the application runs, but invisible to the compiler. I therefore physically cannot import an internal class by accident. The build file is enforcing "code against the interface."

These three carry explicit `<version>` tags, unlike every Spring dependency, because the parent POM only manages versions inside its own ecosystem and jjwt is not part of it.

### jjwt 0.12 was a breaking release
`setSubject`, `setExpiration`, `setSigningKey` and `parseClaimsJws` became `subject`, `expiration`, `verifyWith` and `parseSignedClaims`. Version 0.13.0 is API-identical to 0.12.x. Practical consequence: almost every JWT tutorial online predates the change and will not compile.

### Don't overload a method that makes different guarantees
While adding the role claim I briefly had two `generateToken` methods — a two-argument one and a three-argument one. Java allows it, and it compiles, but the two produce *different kinds of token*: one carries a role, one does not. At a call site they look interchangeable.

Calling the old one by accident would have minted a token that authenticates perfectly and has no role, so every admin endpoint would return 403 with no visible cause. Deleting the old method turned that silent runtime bug into a compile error naming the exact line. Overloading is for "same job, different inputs" — not "same name, different guarantees."

### The login endpoint
Login does three things: find the merchant by email in one indexed query, check the password, mint a token.

The password check runs in a direction worth being precise about. `passwordEncoder.matches(raw, storedHash)` does not decrypt anything — BCrypt is one-way and cannot be reversed. It pulls the salt out of the stored hash, hashes the incoming plaintext with that same salt and cost factor, and compares the two results. Argument order matters: raw first, stored hash second. Swap them and it silently returns false forever.

The endpoint must be `permitAll`. `anyRequest().authenticated()` would otherwise block it, and you would need a token to obtain a token — the same circular bootstrap that already applies to merchant signup.

### Why both failure paths return the same message
Wrong password and unknown email both throw `InvalidCredentialsException("Invalid email or password")`. Never "no such user" versus "wrong password".

Distinct messages let an attacker feed in a list of addresses and learn which ones are registered, purely from which error comes back. That is **email enumeration**, and it is a routine finding in security audits — the information leak is not the password, it is the fact that an account exists at all.

There is a subtler version of the same leak that the generic message does not close. When the email does not exist, the method returns immediately. When it does, a ~100ms BCrypt comparison runs first. An attacker timing the responses can still tell the difference. Real systems run a dummy BCrypt comparison against a throwaway hash in the not-found branch so both paths take roughly the same time. Not implemented here, but worth knowing — it is the answer that shows you thought past the obvious one.

### 401 versus 403, revisited
A failed login returns **401**. Identity was never established, so the honest answer is "I don't know who you are." A caller who authenticated correctly but lacks the role gets **403** — "I know exactly who you are, and no."

### The Bearer scheme
The `Authorization` header supports several schemes: `Basic`, `Digest`, `Bearer`. The prefix tells the server how to interpret what follows, which is why the filter checks `startsWith("Bearer ")` and then takes `substring(7)` — seven characters for `Bearer` plus the space.

`Bearer` means literally that: whoever bears this token is treated as its owner, no further proof required. That is why a leaked token is as good as a password until it expires, and why the expiry is short.

### Why the auth filter never rejects
`JwtAuthFilter` has no 401s, no 403s, no early returns. Its only job is: *if a valid token is present, record who it belongs to.* No token or a bad one, and it does nothing — the request continues, still unauthenticated.

Deciding whether an unauthenticated request is allowed belongs to `SecurityConfig`. That separation is why two filters can share one chain without either knowing the other's rules.

The try/catch is not defensive padding either. An expired token throws `ExpiredJwtException`; a tampered one throws `SignatureException`. Let either escape and Spring returns a 500 with a stack trace — which is both ugly and a small information leak, since the error text tells an attacker whether their forgery failed on the signature or on the expiry.

### Filter order, stated rather than inherited
Both filters go into the chain with `addFilterBefore`. The second call anchors to `ApiKeyFilter.class` rather than to `UsernamePasswordAuthenticationFilter.class` again, which fixes the relative order in writing instead of leaving it to insertion mechanics.

Both filters also guard on `SecurityContextHolder.getContext().getAuthentication() == null`, so whichever runs second cannot overwrite the first one's work. No request carries both credentials today, which is precisely why that bug would have surfaced in six months rather than now.

### Roles, and the prefix that is not optional
Spring Security has a convention that is easy to miss and expensive to get wrong:

> `hasRole("ADMIN")` looks for an authority literally named `ROLE_ADMIN`.

It prepends the prefix when checking, so you must write it when creating: `new SimpleGrantedAuthority("ROLE_" + role)`. Store plain `"ADMIN"` and every check fails silently — no exception, no warning, just a 403 with nothing to explain it.

The null guard matters too. Any token minted before the role claim existed returns `null` from `extractRole`, and `"ROLE_" + null` produces the authority `"ROLE_null"` — an authentication that looks entirely valid and matches nothing. An empty authorities list is the honest answer: authenticated, no role.

The role itself is a `MerchantRole` enum with a field initialiser defaulting to `MERCHANT`, so a client cannot choose its own permission level any more than it can choose its own id. Converting it to a String for the claim uses `.name()` rather than `.toString()` — they are identical for a plain enum, but `toString()` can be overridden by anyone editing the enum later while `name()` is guaranteed by the language. When a value drives permission checks, take the one that cannot change by accident.

### Splitting a path by HTTP method
Since Day 7, `/api/v1/merchants/**` was `permitAll` — meaning anyone on the internet could list, edit or delete every merchant. The reason was real (a merchant must exist before it can hold a key) but the fix was too broad: "signup must be open" got applied to the whole path.

The correction splits by method:

```java
.requestMatchers(HttpMethod.POST, "/api/v1/merchants").permitAll()
.requestMatchers("/api/v1/merchants/**").hasRole("ADMIN")
```

Two details. **Order is load-bearing** — Spring evaluates top to bottom and first match wins, so the POST rule must come first or the ADMIN rule swallows signup and the bootstrap problem returns. And the POST line has **no `/**`**: `/api/v1/merchants` matches that exact path only, while `/api/v1/merchants/**` matches it and everything beneath. Signup is one endpoint; the admin rule is a subtree.

### `ddl-auto: update` and the NOT NULL problem
Adding `role` as a `NOT NULL` column failed at startup: Postgres will not add one to a table that already has rows, because there is nothing to put in them. The Java-side default (`= MerchantRole.MERCHANT`) does not help — it only runs when a new object is constructed, never for rows already on disk.

The fix was three manual steps: add the column as nullable, `UPDATE` every existing row, restart so Hibernate can apply the constraint. In production this is one Flyway migration — versioned, committed, applied identically everywhere. `ddl-auto: update` is a learning convenience, and this is the moment its limits show.

### Two doors, and the trade-off between them
| | API key (`tk_`) | JWT |
|---|---|---|
| Who holds it | the merchant's server | a human at a dashboard |
| Lifetime | forever | one hour |
| Carries a role | no | yes |
| Verification | database lookup per request | local signature check |
| Revocable | yes, delete the row | no, must wait for expiry |

That last row is the honest cost and the standard interview follow-up. Stateless verification means there is no central place to declare a token dead. Short lifetimes are the mitigation; larger systems add refresh tokens or a revocation list, which reintroduces exactly the per-request state lookup that JWTs exist to avoid. It is a trade-off, not a free win.

### Two filters, two staleness guarantees
The same permission model behaves differently depending on which door a caller uses.

`ApiKeyFilter` reads the role from a **fresh database row** on every request, so demoting a merchant takes effect immediately. `JwtAuthFilter` reads it from the **token**, so a demotion changes nothing until that token expires — up to an hour later.

That is the revocation trade-off above, no longer an abstraction but a property of code in this repository.

### Why hashing the API keys is not symmetric
Day 7 left API keys in plain text, and BCrypt does not simply solve it.

Verifying an API key means first *finding* the merchant it belongs to. A hash cannot be indexed, so the only option would be pulling every merchant and BCrypt-comparing against each at ~100ms apiece. A thousand merchants and the gateway falls over.

Passwords escape this because login also sends an email: the email finds the row in one indexed query, and only then is a single hash comparison needed.

The real fix is a **lookup prefix** — store the key as `tk_live_<lookup>_<secret>`, index the lookup half in plain text to find the row in one query, and hash only the secret half. This is what Stripe does.

---

## Day 9 — Ownership: "is this your data?"

### The problem
Day 8 finished with two questions answered and one still open.

**Authentication** answers *who are you* — a signed token, a valid API key.
**Role authorization** answers *what kind of user are you* — MERCHANT or ADMIN.
**Ownership authorization** answers *is this record yours* — and nothing in the system asked it.

A merchant holding a completely legitimate token, with a correct MERCHANT role and a valid signature, could send `merchantId` in a payment body and file a transaction under someone else's account. Every check passed, because every check was about role.

The reads were worse. `GET /api/v1/payments/{id}` returned any payment to any authenticated caller. Sign up for a free account, get a token, start walking IDs: amounts, timing, volume, the whole book. The create hole was a data-integrity bug. The read hole was a breach.

### Unify the principal first
Nothing else could work until the two filters agreed on what identity means. `ApiKeyFilter` set the merchant's UUID as the principal; `JwtAuthFilter` set the email. So `getAuthentication().getName()` returned different shapes depending on which door the caller used, and any ownership comparison would have silently failed for every JWT caller — comparing an email address against a UUID.

Merchant ID wins, because emails change and IDs don't. `JwtService` gained `extractMerchantId`, reading a claim that had been sitting in every token since Day 8 and had never been read.

### Delete the field, don't validate it
The obvious fix for the create hole is an if-check: compare the body's `merchantId` against the caller and throw if they differ.

The better fix is to remove `merchantId` from `CreatePaymentRequest` entirely. The authenticated caller **is** the merchant — the server already knows. Asking the client to tell you, then verifying the answer, is work that doesn't need doing.

Same instinct as Day 3's server-controlled `id` and Day 6's DTO that structurally cannot carry `status`. Applied here to identity rather than to a field.

The difference shows up in the test. Send a forged `merchantId` now and the response is **200**, not 400 — Jackson looks for a field to bind that key to, finds none, and drops it. The request isn't rejected; it's unaffected. A validation check can be forgotten by the next endpoint or lost in a refactor. A field that doesn't exist can't be exploited by anyone, including future-me.

### `Authentication` as a method parameter
```java
public Payment create(@Valid @RequestBody CreatePaymentRequest request,
                      Authentication authentication) {
```
Spring MVC recognises the type and resolves it from the SecurityContext — the same context the filters wrote to milliseconds earlier. No annotation, no injection, no `SecurityContextHolder` call. Declare the parameter and it's there.

`getName()` returns the **principal**: the first argument the filters passed to `UsernamePasswordAuthenticationToken`. Which is exactly why unifying it had to come first.

### Why the controller reads it and the service doesn't
The identity arrives with the request, so the controller — whose job is HTTP concerns — pulls it out and hands the service a plain `String` and a `boolean`. The service then knows nothing about Spring Security, stays unit-testable without standing up a security context, and the Day 3 layering rule holds.

### 404, not 403
When a merchant asks for a payment that isn't theirs, there are two defensible answers.

**403 Forbidden** — "that exists, it's not yours." Accurate and easy to debug.

**404 Not Found** — "nothing here." A small lie, and the safer one.

A 403 *confirms the ID is real*, which is precisely what someone enumerating IDs wants to learn. A 404 is indistinguishable from a genuinely missing record, so walking IDs teaches them nothing.

Stripe returns 404. So does GitHub for private repositories — a repo you can't see looks like it doesn't exist rather than announcing itself.

The implementation detail that makes it work: **both throws use the identical message.** `"Payment not found: " + id` whether the row is missing or merely not yours. Change one of them to "not yours" and the whole protection is undone.

### The rule lives in one place
`getLedgerForPayment` calls `getById` and discards the result:

```java
public List<LedgerEntry> getLedgerForPayment(String paymentId, String callerMerchantId, boolean isAdmin) {
    getById(paymentId, callerMerchantId, isAdmin);
    return ledgerEntryRepository.findByPaymentId(paymentId);
}
```

It reads oddly and is deliberate: *check I'm allowed, then fetch.* The alternative is copying the ownership condition into a second method, and rules that live in two places drift apart.

This endpoint was also the quiet danger. Before Day 9 it went straight to `ledgerEntryRepository` and never touched the payments table — a `LedgerEntry` knows its `paymentId`, not its merchant, so it had no concept of ownership at all. Easy to secure the obvious endpoint and leave its neighbour open, because `/ledger` *reads* like it's about ledger entries rather than payments.

### Fetch-then-check versus scope-the-query
For a **single** resource, load it and then reject: `getById` fetches the payment, compares owners, throws if it isn't yours.

For a **collection**, checking after the fact doesn't work. You would load every payment in the database and filter in Java — slow, memory-hungry, and the data has already left the database before you decided the caller shouldn't have it.

Instead the query itself is scoped:

```java
public List<Payment> getAllForCaller(String callerMerchantId, boolean isAdmin) {
    if (isAdmin) {
        return paymentRepository.findAll();
    }
    return paymentRepository.findByMerchantId(callerMerchantId);
}
```

Two different queries, chosen by role, with no filtering step anywhere. The rows a merchant isn't allowed to see never load. That's the difference between an authorization *check* and an authorization *boundary*.

`findByMerchantId` is another derived query — the method name is the query, same trick as `findByPaymentId` on Day 5 and `findByEmail` on Day 8.

### Two `@GetMapping` annotations on one class
`@GetMapping` with no path maps to the class-level `/api/v1/payments`; `@GetMapping("/{id}")` maps to anything beneath it. Spring resolves by **pattern specificity**, so declaration order doesn't matter here.

Worth contrasting with `SecurityConfig`, where `authorizeHttpRequests` is **first-match-wins** and order is load-bearing. Two configuration systems in the same framework, two different resolution rules.

### Known limitation: no pagination
`findAll()` has no limit. Ten payments is fine; ten million would try to load every row into memory and serialise it.

Real APIs paginate — `GET /payments?page=0&size=20`. Spring Data supports it directly: change the return type to `Page<Payment>`, accept a `Pageable` parameter, and the framework generates the `LIMIT`/`OFFSET`. An interviewer who sees an unpaginated list endpoint will ask about it, and "I know, here's what it would take" is a much better answer than not having noticed.

### A debugging habit worth keeping
Twice during testing, a request that had worked minutes earlier returned 403, and both times the instinct was to suspect the new code. Both times the token had passed its one-hour expiry — once by 89 minutes.

On this API a sudden 403 means **check the token age first.** The code did not change between the two requests; the clock did.

---

## Day 10 — Idempotency and rate limiting with Redis

### The problem

A merchant's server sends `POST /api/v1/payments`. The payment is created, the ledger entries are written, the transaction commits — and the connection drops before the response gets back.

The merchant's server now knows nothing. Did it work? There is no way to tell from where it stands. So it does the correct thing and retries.

**The customer is charged twice.**

This is not a rare edge case. Timeouts happen constantly at scale, retrying is proper client behaviour, and a double charge is the most damaging bug a payment gateway can ship.

Note which HTTP method is involved. `GET`, `PUT` and `DELETE` are naturally safe to repeat — doing them twice has the same effect as once. That property is called **idempotent**. `POST` is the one that isn't, which is precisely why it needs help.

### What Redis is, and what it is not

An **in-memory key-value store**. You put a value under a name and get it back by that name. No tables, no columns, no joins, no SQL.

It is not a replacement for PostgreSQL:

| | PostgreSQL | Redis |
|---|---|---|
| Lives in | disk | RAM |
| Speed | milliseconds | microseconds |
| Structure | tables, relations, queries | keys and values |
| Survives a crash | yes | not by default |
| Good for | the truth | fast, short-lived facts |

Extending the restaurant analogy: **Postgres is the fridge** — everything real is kept there and losing it is a disaster. **Redis is the notepad by the till** where the head waiter scribbles *"table 5 already ordered the fish."* Instantly checkable, and if the notepad burns you can rebuild from the kitchen tickets — but you'd rather not.

Idempotency keys fit that shape exactly. They matter intensely for about 24 hours and then never again.

### TTL: storage that cleans itself

```
SETEX temp 10 "this dies soon"
TTL temp        → 7
GET temp        → (nil)   after ten seconds
```

Redis deleted it. No cron job, no cleanup script, no scheduled task. That property is the main reason idempotency keys live here rather than in Postgres, where expiring old rows would need a job somebody has to write, run and monitor.

### `SET ... NX` — the whole mechanism in one command

```
SET order123 "payment-abc" NX EX 86400   → OK
SET order123 "payment-abc" NX EX 86400   → (nil)
```

- **`NX`** — set only if the key does **N**ot e**X**ist
- **`EX 86400`** — expire in 24 hours

`OK` means *"you're the first, go ahead."* `nil` means *"someone already did this."* That is the duplicate detector, complete.

### Why not `EXISTS` then `SET`

The obvious implementation:

```java
if (redis.exists(key)) { return existing; }
redis.set(key, value);
createPayment();
```

This has a **race condition**. Two retries arrive in the same millisecond. Both run `exists`, both get "no", both proceed. **Two payments.** A duplicate-prevention system that fails under exactly the conditions it exists for.

`SET ... NX` performs the check and the write as **one atomic operation**. Redis executes commands one at a time, so there is no gap for a second request to slip into. Exactly one caller can receive `OK`.

"Check then act" is one of the most common shapes of real production bug, and learning to recognise it is worth more than the Redis syntax.

### The two-phase dance

When the first request arrives there is no payment ID yet, because the payment hasn't been created. So the reservation holds a placeholder:

1. **Reserve** — `SET key "IN_PROGRESS" NX EX 86400`
2. **Create the payment**
3. **Overwrite** the key with the real payment ID

A later retry does a `GET` and finds one of three things:

| Value | Meaning | Response |
|---|---|---|
| nothing | first time | create the payment |
| a payment ID | already done | return **that** payment |
| `IN_PROGRESS` | the original is still running | **409 Conflict** |

That third case is the in-flight window — a retry arriving milliseconds after the original, before it has finished. You cannot return a payment that does not exist yet, and you must not create a second one. **409** is the honest answer: *this conflicts with the current state; try again shortly.* Not 400, because the request is perfectly well-formed. Not 500, because nothing broke.

In practice the implementation reserves *first* and only investigates on failure, so the happy path is a single Redis round trip.

### The release, which is easy to forget

If payment creation throws, the reservation must be deleted. Otherwise the merchant is locked out of that idempotency key for 24 hours and can never retry a request that legitimately failed — a transient error turned permanent.

```java
try {
    Payment saved = paymentService.create(...);
    idempotencyService.storeResult(merchantId, key, saved.getId());
    return saved;
} catch (RuntimeException e) {
    idempotencyService.release(merchantId, key);
    throw e;
}
```

### Key design

Redis has one flat namespace. The universal convention is colons for hierarchy:

```
idem:ed82ce2a-e41a-4952-8dfd-d73abf2c9cd7:order-001
└─┬─┘└──────────────┬─────────────────┘└────┬────┘
prefix          merchant ID          client's key
```

**Scoping by merchant is not decoration.** Idempotency keys are chosen by clients. Two merchants could independently pick `order-1`, and without the merchant in the key, merchant B's payment would be silently blocked by merchant A's — a bug that would be almost impossible to reproduce.

The `idem:` prefix means `KEYS idem:*` shows every idempotency key while debugging without wading through everything else.

### Why the orchestration lives in the controller

After nine days the instinct is *"business logic belongs in the service."* Here it doesn't, for two reasons.

The honest one: idempotency is an HTTP-protocol concern. It's driven by a header and decides HTTP status codes. In production it would be a filter or interceptor — definitively the HTTP layer. Stripe treats it that way.

The one that would have caused a real bug: **Spring implements `@Transactional` with a proxy.** External callers hit the proxy, which opens a transaction, calls the real method and commits. But a call from one method to another *inside the same bean* goes straight to the real object and **bypasses the proxy entirely**.

So a `PaymentService.createIdempotent()` calling its own `create()` would run with no transaction at all. The payment would commit, and if a ledger write then failed there would be no rollback — a payment with no accounting, which is exactly what Day 5 existed to prevent. No error, no warning.

**Self-invocation defeats Spring's proxy-based annotations.** Worth carrying: it applies to `@Transactional`, `@Cacheable`, `@Async` and anything else built the same way.

### The durability trade-off

Redis is not durable by default. If it restarts and loses a key, a retry in that window sails through and creates a duplicate charge.

Some payment systems therefore store idempotency keys in the **database**, with a unique index, written inside the same transaction as the payment. That survives a crash and eliminates the window entirely, at the cost of extra latency on every request and a cleanup job for expired rows.

Redis is the industry-standard choice and the right one here. But it is a choice, and being able to say why you would choose otherwise is worth more than the implementation.

### A window that remains

Between the payment committing and `storeResult` running there is a gap of roughly a millisecond. Crash exactly there and the key stays `IN_PROGRESS` until its TTL expires, so retries get 409 rather than the payment. That is the honest failure mode of this design, and it is the same gap the database-backed approach closes.

### What the key does and does not check

Reuse a key with a *different body* and the original payment comes back — the new amount is ignored entirely. The key asserts *"this is the same operation"* and the server takes it at its word without inspecting the request.

Stripe goes further: it stores a fingerprint of the request and returns **422** if a key is reused with different content, on the grounds that you probably have a bug. Stricter and safer.

Either is defensible. It should be a decision rather than an accident.

---

## Rate limiting

### The problem

A public endpoint with no ceiling is an endpoint anyone can hammer. A buggy merchant integration stuck in a retry loop, a scraper, or an actual attacker all produce the same outcome: the database saturates and **every other merchant's requests start failing**. One caller degrades the service for everyone.

### Fixed-window counting

Count requests per merchant per minute; refuse past a threshold. The key encodes the window:

```
rate:ed82ce2a-e41a-4952-8dfd-d73abf2c9cd7:29771924
└─┬─┘└──────────── merchant ───────────┘└── minute ──┘
```

That last number is `System.currentTimeMillis() / 60000` — integer division giving a value that increments once a minute.

When the clock ticks over, **the key name changes**, so a fresh counter springs into existence and the old one expires by itself. There is no reset logic anywhere in the code. The key name does the work.

### `INCR`, and why the TTL is conditional

```java
Long count = redis.opsForValue().increment(key);
if (count != null && count == 1L) {
    redis.expire(key, WINDOW);
}
return count != null && count <= maxRequestsPerMinute;
```

`INCR` on a missing key treats it as 0, adds 1, and returns 1 — so there is no "create the counter" step. The first request creates it by incrementing it. And it is atomic, for the same reason `SET NX` is: read-then-write-back would let two simultaneous requests both see 19 and both write 20.

**The TTL is set only when the count is 1.** That is the first request in this window. Setting it on every request would keep pushing the expiry forward and the key would never die.

### This filter rejects, and that's new

`ApiKeyFilter` and `JwtAuthFilter` never refuse anything — they record identity and let `SecurityConfig` decide. `RateLimitFilter` enforces directly, because rate limiting is not a per-path rule; it applies to everyone equally.

```java
if (auth != null && !rateLimitService.isAllowed(auth.getName())) {
    response.setStatus(429);
    response.setContentType("application/json");
    response.getWriter().write("{\"error\":\"Rate limit exceeded. Try again shortly.\"}");
    return;                       // no filterChain.doFilter — request stops dead
}
```

**It writes the JSON by hand, and it has to.** `GlobalExceptionHandler` is a `@RestControllerAdvice` — it catches exceptions coming out of controllers, routed through the DispatcherServlet. A filter runs *before* the DispatcherServlet, so an exception thrown there never reaches that handler and produces a raw servlet error page instead.

**429 Too Many Requests** is the correct code. Not 403 — the caller is perfectly permitted. They are simply going too fast.

### `addFilterAfter`, and why order is load-bearing

```java
.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, ApiKeyFilter.class)
.addFilterAfter(rateLimitFilter, ApiKeyFilter.class);
```

Rate limiting is per-merchant, so it needs `getAuthentication()` to already be populated. Place it before the auth filters and the SecurityContext is empty on every request, `auth` is null, the check is skipped, and **nothing is ever limited** — silently, with no error.

Final order: **JwtAuthFilter → ApiKeyFilter → RateLimitFilter → the rest of Spring's chain.**

### Known limitations

**Unauthenticated traffic isn't limited at all.** The filter guards on `auth != null`, so someone brute-forcing `/auth/login` with bad passwords hits no ceiling. Production gateways add a second limiter keyed by IP address for exactly this.

**Fixed windows allow a boundary burst.** Twenty requests at 10:00:59 and twenty more at 10:01:00 is forty in two seconds, despite a limit of twenty per minute. Sliding-window algorithms fix this using Redis sorted sets, at meaningfully more complexity. Fixed window is what most systems ship; knowing *why* it is imperfect is the part worth having.

**No `Retry-After` header.** A well-behaved API tells the client how long to wait. Currently it just says no.

### The pattern underneath both

Idempotency asks *"have I seen this key?"* Rate limiting asks *"how many times this minute?"* Different questions, identical ingredients: an **atomic Redis operation**, a **carefully designed key name**, and a **TTL doing the cleanup**.

That combination recurs everywhere — caching, distributed locks, session storage, deduplication, leaderboards. Learning it once here is worth considerably more than the two features it produced.

---

## Day 11 — Testing

### The problem

Every scenario in this project had been verified by hand in Postman. Login works. Ownership checks work. Idempotency works. All confirmed, all real.

None of it stays confirmed.

Refactor `SecurityConfig` next week, accidentally make `/api/v1/payments` public, and nothing says a word. You find out when a stranger reads someone's transaction history. The verification happened once, in a moment, and then decayed.

**A test is a claim made once that a machine re-checks forever.** That is the entire product. Finding bugs today is a side effect.

It is also the fastest signal an interviewer reads. Ten days of careful architecture with zero tests reads as someone who has not yet worked on a team.

### Why integration tests, not unit tests

The usual advice is a pyramid: many fast unit tests at the bottom, fewer integration tests above, a few end-to-end tests at the top. Sensible advice, and mostly wrong for this project.

Think about where the interesting behaviour actually lives here. A filter chain. Per-path rules evaluated first-match-wins. Ownership checks that depend on who authenticated. Idempotency that depends on Redis. Rate limiting that depends on a filter running *after* the auth filters.

A unit test of `PaymentService` with mocked repositories would pass happily while `SecurityConfig` was wide open — it never touches a filter. It would test the arithmetic and miss the entire security model.

So the tests go through the front door: real HTTP requests, the real filter chain, a real database. Precisely the Postman scenarios, made permanent.

### MockMvc

`MockMvc` sends requests through the full Spring stack **without opening a network port**. Filters run, the DispatcherServlet routes, controllers execute, services hit Postgres and Redis. Only the TCP layer is skipped.

Nineteen tests run in about twenty seconds. Each one by hand in Postman was a minute of clicking and pasting tokens.

```java
mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
```

That reads as the scenario it is.

### The four annotations

`@SpringBootTest` starts the **entire application** — every bean, every filter, both connections. If any bean cannot be constructed or any config is malformed, the test fails before a single line of test code runs.

`@AutoConfigureMockMvc` builds the `MockMvc` bean so it can be injected.

`@ActiveProfiles("test")` layers `application-test.yaml` on top of the main config. **This one line is the difference between a test suite and a data-loss incident** — without it, `ddl-auto: create-drop` would delete every merchant and payment in the real database.

`@Transactional` on a test class wraps each test in a transaction that is **rolled back afterwards**. A merchant created in one test does not exist in the next. Every test starts from the same state.

### The test profile

A Spring profile is a named set of config layered on top of `application.yaml`, not a replacement for it. Only the handful of things that differ get overridden.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/transakt_test
  jpa:
    hibernate:
      ddl-auto: create-drop
  data:
    redis:
      database: 1

ratelimit:
  requests-per-minute: 10000
```

**A separate database**, so a test cannot touch development data.

**`create-drop`** builds the schema from the entities at startup and drops it at the end. Every run begins from an empty, correctly-shaped database — no leftover rows, no "works on my machine because I happen to have that merchant". It doubles as a free schema check: broken entity mappings fail the run immediately.

**Redis database 1.** Redis ships with sixteen numbered databases sharing one server but with completely separate keyspaces. The application uses 0, tests use 1. Same process, no collision, no second install.

**A rate limit of 10,000**, because a test suite fires dozens of requests per second and would trip the real limit of 20 instantly — failing tests for entirely the wrong reason.

### Test isolation is per-store, not automatic

This is the part worth carrying to every future project.

`@Transactional` rolls back **Postgres**. It does nothing to **Redis**.

So an idempotency key written by one test is still sitting there when the next runs. A rate-limit counter survives. The database resets; the cache does not.

```java
@BeforeEach
void clearRedis() {
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
}
```

Anything outside the transaction manager — a cache, a message queue, a file on disk, an external API — needs cleaning up explicitly. The rollback is not a general-purpose reset button; it is a database feature that happens to be very convenient.

### Two actors in one test

Ownership is a claim about two people, so testing it requires being both.

```java
String alice = tokenFor(ALICE);
String paymentId = createPayment(alice, 50000);

String bob = tokenFor(BOB);

mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + bob))
        .andExpect(status().isNotFound());
```

By hand that is two signups, two logins, copying two tokens, creating a payment, copying its ID, and swapping headers. Here it is six lines, and it runs in milliseconds every time the suite runs.

The mechanism that makes it possible:

```java
.andReturn().getResponse().getContentAsString()
```

`jsonPath` **checks** a value. `andReturn` **retrieves** one. You need the retrieved token and payment ID to build the *next* request — assertions alone cannot chain.

### `@TestPropertySource` and the context cache

Rate limiting needs a low limit to test, but the rest of the suite needs a high one. One annotation solves it:

```java
@TestPropertySource(properties = "ratelimit.requests-per-minute=5")
```

The cost is worth knowing. Spring **caches application contexts** and reuses them across test classes with identical configuration — which is why the second, third and fourth test classes start almost instantly. A property override makes the configuration different, so that class gets its own context and pays the startup cost again.

Fine for one class. Scatter overrides across twenty and the suite crawls.

### Naming tests as sentences

`paymentsAreClosedWithoutACredential`, not `testPayments1`.

When a test fails, the name is the first thing read, often in a CI log with no other context. It should say what broke, not what was called.

The four test classes read as a specification of the system:

- signup is open and never returns the password
- login with a wrong password is rejected
- login with an unknown email gives the identical message
- a merchant cannot read another merchant's payment
- the list endpoint is scoped to the caller
- `merchantId` in the body is ignored
- the same key twice returns the same payment
- two merchants can use the same key independently
- one merchant hitting the limit does not affect another

That list is more useful documentation than most README files.

### Testing a known limitation on purpose

```java
@Test
void aReusedKeyIgnoresADifferentBody() {
    String first = createPayment(token, "order-001", 50000);
    String second = createPayment(token, "order-001", 999999);
    assertThat(second).isEqualTo(first);
}
```

This asserts a **deliberate simplification**, not ideal behaviour. Stripe would return 422 here; this system returns the original payment and ignores the new amount.

Writing a test for a limitation is worth doing. If body fingerprinting is added later, this test fails — which forces the change to be noticed rather than sliding in unannounced. A test that encodes a decision is as valuable as one that encodes a requirement.

### The tests that matter most

The ones guarding failures that would be **silent** in production:

| Change | Consequence | Test that catches it |
|---|---|---|
| Delete `@JsonProperty(WRITE_ONLY)` | every password hash exposed on a public endpoint | signup never returns the password |
| Change one login error message | email enumeration | both failure paths, identical message |
| Add `merchantId` back to the DTO | payments filed under other accounts | `merchantId` in the body is ignored |
| Drop merchant from the idempotency key | two merchants collide on `order-1` | two merchants, same key, independently |
| Drop merchant from the rate key | the whole user base throttled as one bucket | one merchant's limit does not affect another |

Every one of those is a one-word change that looks harmless in a diff, and nearly impossible to catch by hand.

### `src/main` and `src/test` are separate worlds

A test class created under `src/main/java` fails with `cannot find symbol: class Test` — baffling, because the import is correct.

The reason: `spring-boot-starter-test` is declared with `<scope>test</scope>`, so it is on the classpath for `src/test` and **not** for `src/main`. They are separate compilation units with separate dependency sets. JUnit genuinely does not exist when compiling main.

Same for `application-test.yaml`. Placed under `src/main/resources` it still works — main resources are on the test classpath too — but it would also be packaged into the production JAR, shipping a config file pointing at `transakt_test` with a rate limit of 10,000. Not a bug today; a nasty one at the first deployment.