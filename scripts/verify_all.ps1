$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Force -Path 'verification' | Out-Null
if (-not (Test-Path '.env')) { throw 'Create .env from .env.example and change the secrets first' }
function Run-Checked([string]$label, [string]$command, [string]$logFile) {
  Write-Host "==> $label"
  & cmd /c "$command > $logFile 2>&1"
  if ($LASTEXITCODE -ne 0) {
    Get-Content $logFile -Tail 50
    throw "$label failed (exit=$LASTEXITCODE). Full output: $logFile"
  }
  Get-Content $logFile -Tail 8
}
Run-Checked 'Compose configuration' 'docker compose config --quiet' 'verification\compose.log'
Run-Checked 'JUnit/MockMvc + JaCoCo' 'docker compose --profile test run --rm maven-test' 'verification\maven.log'
Run-Checked 'Build and start PostgreSQL/Redis/API' 'docker compose up --build -d' 'verification\compose-up.log'
py -3 scripts/wait_for_api.py
if ($LASTEXITCODE -ne 0) { throw 'API did not become healthy' }
Run-Checked 'Live HTTP / stock locking / Redis cache' 'py -3 scripts/api_smoke.py' 'verification\smoke.log'
Run-Checked 'k6 200 requests/s load test' 'docker compose --profile loadtest run --rm k6' 'verification\k6.log'
py -3 scripts/verification_report.py
if ($LASTEXITCODE -ne 0) { throw 'Report generation failed' }
Write-Host 'Automated checks completed. See verification\RESULTS.md.'
