# Threat model & security controls

The service is a demo, **not an audited production system**. API clients are assumed untrusted; DB/Redis are inside a private Compose network and only the API's localhost port is published by default.

| OWASP API risk | Implemented control | Remaining risk |
|---|---|---|
| API1 Broken Object Level Authorization | UUID-based records and required JWT; all resources belong to one fictional plant | No multi-tenant row isolation; if added later, include mandatory tenant scoping |
| API2 Broken Authentication | BCrypt password storage, HS256 JWT, expiration, short TTL, login throttling | No refresh/revocation/key rotation, no MFA, no login audit trail |
| API3 Broken Object Property Level Authorization | DTOs explicitly whitelist request/response fields; writes gated by role | Separate material create/update DTOs and strict unknown-property rejection prevent silent inventory mutation |
| API4 Unrestricted Resource Consumption | General/login IP rate limits, bounded page size (100), bounded cache | Per-instance limiter; no request body size limit or distributed edge defense |
| API5 Broken Function Level Authorization | `@PreAuthorize` on every mutating method, authentication by default | Admin must monitor role grants; role claim remains valid until token expiration |
| API6 Unrestricted Access to Sensitive Business Flows | Stock/weight invariants, zero-stock archive prerequisite, batch and lot pessimistic locks | More detailed operator approval workflows might be needed |
| API7 Server-Side Request Forgery | No user-supplied URL fetch operations | Review again when adding notifications or integrations |
| API8 Security Misconfiguration | Required JWT secret, env vars, sanitized 500 responses, localhost port | TLS, dependency scanning, external secrets manager remain deployment responsibilities |
| API9 Improper Inventory Management | 32 documented routes, Swagger/OpenAPI, versioned API path | Ensure deprecated routes are removed deliberately |
| API10 Unsafe Consumption of APIs | No external provider API dependencies in request path | Future integrations must validate responses and timeouts |

**Secret setup:** Set a long random `JWT_SECRET` independent from database/user passwords. `docker-compose.yml` fails fast when env vars are absent. Never ship `.env`, real credentials, or JUnit production data.

**Rate limiting behavior:** An in-memory Caffeine cache stores a fixed-window counter per remote IP and endpoint class (login vs general). It sends `429` and `Retry-After: 60` when reached. This is designed for a single application replica. Replace with a Redis atomic limiter or edge gateway when scaling to multiple application replicas. Do not trust raw `X-Forwarded-For` unless an authenticated reverse proxy is configured.

**Concurrency:** `findByIdForUpdate` on the batch and raw-material lot is used before computing consumption and stock. The same transaction writes both stock and movements so an exception rolls back all changes. SQL CHECK constraints protect negative stock and invalid weights even if another code path bypasses the service. Optimistic `@Version` columns provide additional conflict protection.

**Errors and logging:** Clients receive sanitized errors; server-side errors include stack traces in logs only. A request UUID header is set on each request. Add trace ID to your log aggregation and scrub any sensitive payloads. Spring Boot built-in Logstash structured logging includes MDC fields such as requestId.

**Additional hardening before any production use:** TLS proxy, vulnerability scanning, secret rotation and vault, secure per-environment CORS, durable audit logs and write-once policies, authenticated admin dashboard, integration tests on actual PostgreSQL, Redis availability/failover policy, and observability alerts.

## Security hardening added during audit

- The request-ID and IP-rate-limit filters run before Spring Security, so invalid/unauthorized requests also receive a correlation ID and count against the appropriate limit.
- Unknown request JSON properties are rejected. Inventory creation and metadata updates have distinct DTOs, so an opening balance is never silently ignored on update.
- Archived lots cannot be adjusted or consumed. A lot supplier cannot be reassigned, and a lot must first have zero stock; all adjustments are audited and the historical stock ledger remains readable.
- Expired raw material lots are rejected for consumption. Passing QA checks require an actual moisture reading; missing night-shift readings are represented as `null` and `PENDING`.
- Reused human-readable batch numbers remain allowed, but batch identity and both recorded weights become immutable once QA or consumption history exists.

These are demo-level controls, not a penetration test or production security certification.
