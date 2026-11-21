# Verification report — Food Plant REST API Backend

**Audit date:** 21 November 2026  
**Status:** Source-level checks passed; Spring/runtime acceptance results **not yet verified**.

This report distinguishes actual checks executed in the artifact-generation environment from tests that require Docker, Maven Central, PostgreSQL, Redis, and k6. **A source archive with configured tests is not the same as a passing test report.**

## Results actually obtained

| Check | Observed result | Execution evidence |
|---|---|---|
| Java language syntax, including tests | **PASS** | Java 21 `JavacTask.parse()` accepted all **36 Java source/test files** (does not resolve external dependencies) |
| Domain weight conversion | **PASS** | `javac` compiled the production `WeightNormalizer` class; **11** behavior checks passed in `WeightNormalizerSelfTest` |
| OpenAPI → implemented Controller paths | **PASS** | Exactly **32 unique** OpenAPI operations map to the 32 annotated routes in Java controllers |
| OpenAPI → Postman | **PASS** | Exactly **32** matching operation IDs and 32 generated Postman requests |
| OpenAPI schema refs / auth / error definitions | **PASS** | Static schema walk and security assertions |
| Spring, Compose, CI, test YAML | **PASS** | Successfully parsed using PyYAML |
| Fixture consistency | **PASS** | Three production lines, machine-to-line mapping, 5 batch events, duplicate legacy numbers, nullable night moisture, valid material consumption and stock balances |
| Python verification scripts | **PASS** | `python -m py_compile` of live smoke and reporting scripts |

## Checks not executed here

| Acceptance check | Status | How to obtain trustworthy evidence |
|---|---|---|
| Maven compile / real Spring context startup | **NOT VERIFIED** | `docker compose --profile test run --rm maven-test` |
| JUnit / authenticated MockMvc tests | **NOT VERIFIED** | Maven `clean verify` XML output in `target/surefire-reports` |
| JaCoCo ≥75% LINE coverage | **NOT MEASURED** | Actual `target/site/jacoco` report; `verify` fails below 75% |
| PostgreSQL Flyway migrations V1+V2 | **NOT VERIFIED** | Docker API startup and live smoke test |
| Redis cache + invalidation | **NOT VERIFIED** | Live smoke tests on create/update/delete production line |
| Concurrent stock spend correctness | **NOT VERIFIED** | Live smoke test with 10 simultaneous stock-consumption requests |
| Authorization, JSON validation, audit ledger | **NOT VERIFIED** | Running-API smoke + JUnit tests |
| 200 RPS sustained and p95 <300ms | **NOT MEASURED** | k6 Compose profile; raw output + JSON in `verification/` |
| All endpoints free of unhandled server errors | **NOT PROVEN** | Negative-path tests cover important cases but cannot prove every possible input |

Maven, Docker and k6 were unavailable in the authoring environment. Maven dependencies could not be downloaded due unavailable Maven Central DNS access. **No coverage percentages, latency claims or full-test successes have been fabricated.**

## Additional second-pass audit checks (21 November 2026)

- **PASS (static):** GitHub Actions now uses `set -o pipefail` on both Maven and k6 command pipelines, so an underlying test failure cannot be masked by `tee`.
- **PASS (source review + syntax):** Material-lot supplier changes and audited output-weight mutations are rejected; their regression tests have been added.
- **PASS (source review + syntax):** Known concurrent write/locking exceptions map to HTTP 409 rather than unhandled HTTP 500.
- **PASS (static):** Re-generated OpenAPI and Postman artifacts still match all 32 controller operations.
- **NOT VERIFIED:** The newly added JUnit regression tests have not been executed against a running Spring context.

## Automated acceptance run on a suitable machine

Use a machine with Docker Desktop (Compose), Python 3.10+ and adequate memory. Maven and k6 run **inside Docker containers**, so local installation is not necessary.

1. Copy `.env.example` to `.env` and replace passwords and `JWT_SECRET` with unpredictable values.
2. In Windows PowerShell, run `powershell -ExecutionPolicy Bypass -File .\scripts\verify_all.ps1`; on Linux/macOS run `bash scripts/verify_all.sh`.
3. The automation runs Maven test+JaCoCo, Docker PostgreSQL/Redis startup, real API checks, 10 concurrent stock requests, then k6 at 200 RPS for 60 seconds.
4. Preserve the generated `verification/RESULTS.md`, logs, Surefire XML, JaCoCo report and `verification/k6.json`. They are evidence for an actual acceptance result and are intentionally not prepopulated in this archive.

This run is also configured in `.github/workflows/ci.yml` for a pushed GitHub repository. Performance results depend on the actual runner and hardware; if the targets fail, measure and tune before claiming success.
