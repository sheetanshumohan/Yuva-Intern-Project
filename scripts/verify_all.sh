#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p verification
if [[ ! -f .env ]]; then echo 'Create .env from .env.example and change the secrets first'; exit 2; fi
# Maven runs inside a container: no host Maven installation necessary.
echo '==> Validating Compose configuration'
docker compose config --quiet
echo '==> Maven JUnit + MockMvc + JaCoCo 75% coverage gate'
docker compose --profile test run --rm maven-test 2>&1 | tee verification/maven.log
echo '==> Building real PostgreSQL + Redis + API stack'
docker compose up --build -d
python3 scripts/wait_for_api.py
echo '==> Real HTTP + PostgreSQL locking + Redis cache smoke'
python3 scripts/api_smoke.py 2>&1 | tee verification/smoke.log
echo '==> k6 200 RPS / p95 < 300ms acceptance'
docker compose --profile loadtest run --rm k6 2>&1 | tee verification/k6.log
python3 scripts/verification_report.py
printf '\nAll enabled automated acceptance checks passed. Read verification/RESULTS.md.\n'
