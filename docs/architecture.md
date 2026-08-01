# Transakt Architecture

## Current: v0.6 — validation and error handling (Day 6)

````mermaid
flowchart TD
  client[API client - Postman]
  client -->|HTTP + JSON| tomcat[Embedded Tomcat :8080]
  tomcat --> dispatcher[DispatcherServlet]
  dispatcher --> health[HealthController]
  dispatcher --> mc[MerchantController]
  dispatcher --> pc[PaymentController - Valid DTO]
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

**How a request flows:** Tomcat parses the HTTP request and the DispatcherServlet routes it to the right controller. Incoming payment JSON is bound to a validated DTO (`CreatePaymentRequest`) — `@Valid` rejects bad input before it reaches any logic. Controllers handle only HTTP concerns and delegate to services. `PaymentService` is `@Transactional`: creating a payment writes the payment row plus two balancing ledger entries as one atomic unit. Every service reaches the database only through a repository; Spring Data JPA turns repository calls into SQL against PostgreSQL.

**Validation and error handling (v0.6):** clients send a DTO exposing only the fields they may set (merchantId, amountPaise, currency), never internal fields like id, status, or createdAt. Bean Validation annotations on the DTO enforce rules such as "amount must be positive." Any exception from any controller is caught by a single `GlobalExceptionHandler` (`@RestControllerAdvice`), which returns small, consistent JSON with the correct status code — 400 for validation failures, 404 for missing resources — so stack traces never leak to clients.

**The double-entry ledger (v0.5):** a payment never updates a stored balance. It appends two `LedgerEntry` rows — a CREDIT to the merchant account and an equal DEBIT from the gateway account. The two are equal and opposite, so the books always net to zero; any corruption makes them stop balancing, which is what makes errors detectable. Entries are append-only. Money is stored as integer paise, never a decimal, so arithmetic is exact.

**Known limitation:** there is no authentication yet — any caller can create merchants or payments. API keys, JWT login, and role-based access control are next.

## Version log
| Version | Day | What changed |
|---------|-----|--------------|
| v0.1 | 1 | Repository and tooling. No code. |
| v0.2 | 2 | Spring Boot application running; first REST endpoint. |
| v0.3 | 3 | Merchant domain: controller, service, in-memory store. |
| v0.4 | 4 | PostgreSQL + Spring Data JPA. Data now durable. |
| v0.5 | 5 | Payments + double-entry ledger, atomic transactional writes. |
| v0.6 | 6 | DTO validation + global exception handling. |