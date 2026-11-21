# Food Plant REST API — Verified CI Test Report

**Verification date:** 21 November 2026  
**Repository:** https://github.com/sheetanshumohan/Yuva-Intern-Project  
**Successful acceptance workflow:** https://github.com/sheetanshumohan/Yuva-Intern-Project/actions/runs/44872259269  
**Status:** **PASS** for the automated CI acceptance workflow on the tested GitHub-hosted runner.

## Actual recorded results

| Acceptance check | Measured result | Outcome |
|---|---|---|
| Maven compile and verification | BUILD SUCCESS | PASS |
| JUnit and MockMvc tests | 82 run, 0 failures, 0 errors, 0 skipped | PASS |
| JaCoCo line coverage | 92.44%, required ≥75% | PASS |
| Static Java, OpenAPI, Postman, fixtures checks | CI validation step succeeded | PASS |
| Docker build and startup | PostgreSQL, Redis and API built and started | PASS |
| HTTP smoke and concurrency checks | 74 successful checks, including 10 concurrent transactions | PASS |
| k6 requests | 12,001 HTTP requests | PASS |
| k6 throughput | ~199.13 requests/sec average during 200 arrivals/sec configured scenario | PASS (tool thresholds) |
| k6 p95 HTTP response latency | 1.51 ms, required <300 ms | PASS |
| k6 HTTP failures | 0 / 12,001 | PASS |
| k6 assertions | 24,000 / 24,000 | PASS |
| GitHub Actions result | completed / success | PASS |

The k6 run was scheduled with **200 arrivals per second** for one minute. Measured average HTTP throughput was approximately **199.13 requests/second**, and no iterations were dropped. Measurements are from an isolated GitHub Actions runner and **do not guarantee equivalent production throughput**.

## Evidence

- [Successful CI workflow](https://github.com/sheetanshumohan/Yuva-Intern-Project/actions/runs/44872259269)
- [CI evidence artifacts](https://github.com/sheetanshumohan/Yuva-Intern-Project/actions/runs/44872259269#artifacts) include Maven logs, Surefire test reports, JaCoCo output, API smoke results, k6 logs and JSON output, and generated `verification/RESULTS.md`.
- The CI workflow definition is [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Not covered by the acceptance claim

Passing tests does not establish the absence of all bugs or certify production security. Remaining production-hardening work includes TLS termination, secrets management and key rotation, distributed rate limiting for multiple replicas, JWT revocation / refresh, vulnerability scanning, monitoring/alerting, PostgreSQL-backed integration test expansion, and additional adversarial security checks.

This report summarizes **real evidence obtained in GitHub CI**, rather than inferred or fabricated results. If the workflow is changed, rerun it and update these recorded measurements.
