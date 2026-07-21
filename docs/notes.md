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