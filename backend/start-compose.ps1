# Starts PostgreSQL + backend in Docker and waits for both healthchecks.
# Usage (from the project root):  powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
# ASCII-only on purpose (Windows PowerShell 5.1 reads scripts without BOM in the ANSI code page).
param(
    [int]$WaitTimeoutSeconds = 600
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ComposePath = Join-Path $PSScriptRoot "docker-compose.yml"
$EnvPath = Join-Path $ProjectRoot ".env"

& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker is not running. Start Docker Desktop and try again." }

if (-not (Test-Path $EnvPath)) {
    Write-Host ".env not found - creating it with random secrets..." -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot "init-env.ps1")
}

Write-Host "Building the image and starting containers (first run downloads Maven dependencies, a few minutes)..." -ForegroundColor Cyan
& docker compose --env-file $EnvPath -f $ComposePath up -d --build --wait --wait-timeout $WaitTimeoutSeconds
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Startup failed. Last backend log lines:" -ForegroundColor Red
    & docker compose --env-file $EnvPath -f $ComposePath logs --tail 120 backend
    exit $LASTEXITCODE
}

$values = @{}
foreach ($line in Get-Content -LiteralPath $EnvPath) {
    $t = $line.Trim()
    if (-not $t -or $t.StartsWith("#")) { continue }
    $eq = $t.IndexOf("=")
    if ($eq -gt 0) { $values[$t.Substring(0, $eq)] = $t.Substring($eq + 1) }
}
$appPort = if ($values["APP_PORT"]) { $values["APP_PORT"] } else { "8080" }
$pgPort = if ($values["POSTGRES_PORT"]) { $values["POSTGRES_PORT"] } else { "15432" }

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " HOTEL BOOKING BACKEND IS READY" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Web UI:           http://localhost:$appPort/"
Write-Host " Swagger UI:       http://localhost:$appPort/swagger-ui.html  (ADMIN)"
Write-Host " REST API:         http://localhost:$appPort/api"
Write-Host " OpenAPI JSON:     http://localhost:$appPort/v3/api-docs"
Write-Host " Health:           http://localhost:$appPort/actuator/health"
Write-Host " Android emulator: http://10.0.2.2:$appPort/api"
Write-Host " PostgreSQL JDBC:  jdbc:postgresql://localhost:$pgPort/$($values['POSTGRES_DB'])"
Write-Host " PostgreSQL user:  $($values['POSTGRES_USER'])"
Write-Host " Admin login:      $($values['BOOTSTRAP_ADMIN_EMAIL'])  (temporary password - see .env)"
Write-Host "============================================================" -ForegroundColor Green
& docker compose --env-file $EnvPath -f $ComposePath ps
