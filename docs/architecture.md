# Transakt Architecture

## Current: v0.3 — layered monolith, in-memory storage (Day 3)

````mermaid
flowchart TD
  client[API client - Postman]
  client -->|HTTP + JSON| tomcat[Embedded Tomcat :8080]
  tomcat --> dispatcher[DispatcherServlet]
  dispatcher --> health[HealthController]
  dispatcher --> mc[MerchantController]
  mc --> ms[MerchantService]
  ms --> store[(In-memory HashMap - temporary)]
````

**How a request flows:** Tomcat accepts the connection and parses HTTP. The DispatcherServlet matches the method and path against its handler table and calls the right controller. The controller handles only HTTP concerns and delegates to the service, which owns business rules such as generating identifiers and timestamps. The service reads and writes the store.

**Known limitation:** the store is a `HashMap` in JVM memory. All data is lost on restart, cannot be shared across instances, and cannot be queried. PostgreSQL and Spring Data JPA replace it next, changing only the storage layer.

## Version log
| Version | Day | What changed |
|---------|-----|--------------|
| v0.1 | 1 | Repository and tooling. No code. |
| v0.2 | 2 | Spring Boot application running; first REST endpoint. |
| v0.3 | 3 | Merchant domain: controller, service, in-memory store. |