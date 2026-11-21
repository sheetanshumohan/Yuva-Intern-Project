# Quality audit changes — 21 November 2026

This is the revised source release, not an assertion of full runtime certification.

- Enforced sub-gram rejection **before** weight rounding and added maximum stock precision guard.
- Added an independently testable, production `WeightNormalizer` class.
- Split raw-lot creation (`MaterialRequest`) from metadata updates (`MaterialUpdateRequest`); unknown request fields now return 400.
- Required explicit `active` and `increase` booleans to prevent accidental deactivation or unintended negative stock movements.
- Added real stock-ledger opening movements and partial material-consumption records to the three-line synthetic seed. Fixture CSVs now include machines and material consumption.
- Preserved historical lot records through a Flyway V2 archiving migration. Archiving requires a zero stock balance; archived stock cannot be changed or consumed.
- Forbade changing identifying batch fields after QA or material usage was recorded.
- Rejected expired raw material for consumption and PASS quality checks without moisture measurements.
- Applied request-ID and rate-limit filters before authentication so all API requests, including rejected ones, are logged/counted consistently.
- Made JUnit tests independent of shared Redis cache state and expanded negative cases for quality/stock/archiving/security.
- Added a live HTTP acceptance runner with real PostgreSQL locking tests (10 concurrent deductions), JWT authorization checks, and Redis cache invalidation checks.
- Added Docker-contained Maven tests, GitHub Actions acceptance workflow, and a k6 profile targeting 200 RPS with a 300 ms p95 threshold.
- Added actual JDK Java 21 source parsing, 11 compiled domain behavior checks, and strong OpenAPI/controller/fixture static assertions.
- Updated OpenAPI, Postman, README, security documentation, ER diagram and evidence-based test report.

**Verification limits:** No Maven/Spring context, PostgreSQL, Docker, or k6 execution was possible in the archive-generation environment; see `TEST_REPORT.md` for precise statuses. The first complete acceptance run must be recorded before stating that the project meets every success threshold.

## Final verification hardening (2026-11-21)
- Hardened GitHub Actions acceptance pipeline: Bash pipefail propagates Maven and k6 failures instead of treating successful log streaming as successful tests.
- Enforced immutable material-lot supplier to preserve provenance, including before any consumption.
- Prevented edits to recorded batch output weight after QC/consumption history exists.
- Added a structured 409 response for optimistic/pessimistic concurrency conflicts.
- Rejected non-positive rate-limit settings on startup and moisture with more than two fractional digits.
- Added MockMvc regression cases for supplier immutability, audited output immutability, moisture precision and invalid rate limits.

## Final verification audit (2026-11-21)
- Added consistent HTTP 409 mapping for optimistic/pessimistic locking and transaction write conflicts instead of exposing generic HTTP 500.
- Added regression tests for tampered JWTs and negative inventory pagination limits.
- Rechecked offline Java syntax, weight normalization, OpenAPI/Postman contract, and seed invariants. Full Maven/Compose/k6 results still require a runnable host or successful GitHub Actions execution.
