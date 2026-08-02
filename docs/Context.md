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

---

## My environment

- MacBook Air (Apple Silicon), macOS
- Java 21 (Temurin) — project SDK must be **temurin-21**, never 26
- IntelliJ IDEA
- PostgreSQL 18 via Postgres.app, port 5432, database `transakt`, user `aman`, no password
- Postman for API testing
- Project path: `~/Documents/Coding/transakt`

---

## Tech stack (versions matter!)

- **Spring Boot 3.4.1** — was 4.1.0, downgraded because no matching security starter existed
- Maven, Java 21, YAML config (`application.yml`)
- Dependencies: spring-boot-starter-**web** (not webmvc), validation, data-jpa, security,
  devtools, postgresql, lombok, spring-boot-starter-**test** (not webmvc-test)
- `ddl-auto: update`, `show-sql: true`, `open-in-view: false`

---

## Current state: Day 7 complete

| Day | What was built |
|-----|----------------|
| 1 | Tooling, repo, README, .gitignore, docs/ |
| 2 | Spring Boot running, embedded Tomcat, `GET /api/v1/health` |
| 3 | Merchant domain — controller, service, in-memory HashMap, full CRUD |
| 4 | PostgreSQL + Spring Data JPA — data survives restarts |
| 5 | Payments + **double-entry ledger**, atomic `@Transactional` writes |
| 6 | DTO validation + `GlobalExceptionHandler` (clean 400s and 404s) |
| 7 | API key authentication via a custom Spring Security filter |

Architecture doc is at **v0.7**. All work committed and pushed.

---

## Package structure

```
com.transakt.transakt
├── common/     GlobalExceptionHandler, ResourceNotFoundException,
│               ApiKeyFilter, SecurityConfig
├── merchant/   Merchant (entity), MerchantRepository,
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
  DEBIT from gateway, equal amounts). Balances are never stored or edited; they're the sum
  of entries. If the books stop netting to zero, something is broken — errors are detectable.
  Entries are append-only, so the trail is tamper-evident.
- **Money as integer paise** (`Long`), never decimals. ₹500 = 50000. Floating point loses
  precision; in money that's a lawsuit.
- **`@Transactional` on payment creation** — the payment row and both ledger entries commit
  together or not at all. There can never be a payment without its accounting.
- **Server-controlled fields** — id, status, createdAt are set by the service, never the client.
  DTOs make this structural: the client literally cannot send those fields.
- **Layered architecture** — controller (HTTP only) → service (business rules) → repository
  (data). Proven when the HashMap became PostgreSQL and the controller needed zero changes.
- **API keys prefixed `tk_`** — like Stripe's `sk_`, Razorpay's `rzp_`. Recognisable, scannable
  if leaked.

---

## Endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/health` | open |
| POST/GET/PUT/DELETE | `/api/v1/merchants` | open (bootstrap: need a merchant before a key) |
| POST | `/api/v1/payments` | requires `X-API-Key` header |
| GET | `/api/v1/payments/{id}` | requires key |
| GET | `/api/v1/payments/{id}/ledger` | requires key |

---

## Gotchas already hit (don't repeat these)

- Lombok needs **annotation processing enabled** in IntelliJ or its generated methods appear missing
- YAML forbids **duplicate keys** at one level — a second `spring:` silently drops the first
- `.gitignore` only blocks **untracked** files; use `git rm -r --cached` for already-tracked ones
- When repositories seem to lack `save()`/`findById()`, it's a **stale IntelliJ index** —
  Maven Reload + Rebuild, or Invalidate Caches
- Artifact **names** change between major Spring versions, not just numbers
- 401 = "who are you"; 403 = "known request, not permitted"

---

## What's next

- **Day 8 — JWT login** for a merchant dashboard (humans), plus role-based access control
- Then: Redis (idempotency — never double-charge; rate limiting)
- Then: Kafka (payment events, webhook delivery to merchants with retries + DLQ)
- Then: bank simulator, Docker, Kubernetes, Spring AI (RAG docs assistant, MCP server)

Also outstanding: API keys are stored in plain text — production would hash them and show
the key once at creation.

---

## Docs in this repo

- `docs/notes.md` — concept explanations, the "why" behind everything
- `docs/WORKLOG.md` — daily entries: Built / Why / Concepts / Interview line / Mistake & fix
- `docs/architecture.md` — versioned architecture with a Mermaid diagram, currently v0.7
- `docs/CONTEXT.md` — this file