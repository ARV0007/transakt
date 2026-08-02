# Transakt Architecture

## Current: v0.7 — API key authentication (Day 7)

````mermaid
flowchart TD
  client[API client - merchant server]
  client -->|HTTP + JSON + X-API-Key| tomcat[Embedded Tomcat :8080]
  tomcat --> filter[ApiKeyFilter - resolves key to merchant]
  filter --> dispatcher[DispatcherServlet]
  dispatcher --> health[HealthController - open]
  dispatcher --> mc[MerchantController - open]
  dispatcher --> pc[PaymentController - key required]
  dispatcher -.->|exceptions| geh[GlobalExceptionHandler - clean 400 / 404]
  mc --> ms[MerchantService]
  pc --> ps[PaymentService - Transactional]
  ms --> mr[MerchantRepository]
  ps --> pr[PaymentRepository]
  ps --> lr[LedgerEntryRepository]
  mr --> db[(PostgreSQL)]
  pr --> db
  lr --> db
````

**How a request flows:** Tomcat parses the HTTP request. Before any controller runs, `ApiKeyFilter` inspects the `X-API-Key` header and, if it matches a merchant, marks the request authenticated and records which merchant it belongs to. The DispatcherServlet then routes to the right controller. Incoming payment JSON is bound to a validated DTO — `@Valid` rejects bad input before it reaches any logic. Controllers handle only HTTP concerns and delegate to services. `PaymentService` is `@Transactional`: creating a payment writes the payment row plus two balancing ledger entries as one atomic unit. Services reach the database only through repositories; Spring Data JPA turns those calls into SQL against PostgreSQL.

**Authentication (v0.7):** every merchant receives an API key (prefixed `tk_`) when their account is created. `/api/v1/health` and `/api/v1/merchants` are open — the latter deliberately, since a merchant must exist before it can hold a key. Everything else, including `/api/v1/payments`, requires a valid key; requests without one are refused before reaching a controller. CSRF protection is disabled because this is a stateless API, not a browser form application.

**Validation and error handling (v0.6):** clients send a DTO exposing only the fields they may set (merchantId, amountPaise, currency), never internal fields like id, status, or createdAt. Bean Validation annotations enforce rules such as "amount must be positive." A single `GlobalExceptionHandler` (`@RestControllerAdvice`) catches every exception and returns small, consistent JSON with the correct status code — 400 for validation failures, 404 for missing resources — so stack traces never leak to clients.

**The double-entry ledger (v0.5):** a payment never updates a stored balance. It appends two `LedgerEntry` rows — a CREDIT to the merchant account and an equal DEBIT from the gateway account. The two are equal and opposite, so the books always net to zero; any corruption makes them stop balancing, which is what makes errors detectable. Entries are append-only. Money is stored as integer paise, never a decimal, so arithmetic is exact.

**Known limitation:** there is no human login yet — a merchant dashboard would need email/password authentication issuing JWTs, plus role-based access control so support staff and admins have different permissions. API keys are also stored in plain text; production systems store a hash and show the key only once at creation.

## Version log
| Version | Day | What changed |
|---------|-----|--------------|
| v0.1 | 1 | Repository and tooling. No code. |
| v0.2 | 2 | Spring Boot application running; first REST endpoint. |
| v0.3 | 3 | Merchant domain: controller, service, in-memory store. |
| v0.4 | 4 | PostgreSQL + Spring Data JPA. Data now durable. |
| v0.5 | 5 | Payments + double-entry ledger, atomic transactional writes. |
| v0.6 | 6 | DTO validation + global exception handling. |
| v0.7 | 7 | API key authentication via a Spring Security filter. |