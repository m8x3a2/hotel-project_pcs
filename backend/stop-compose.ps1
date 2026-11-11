# Stops the containers. Database data is KEPT (named volume hotel_pgdata).
# -ResetData also deletes the volume: on next start Flyway recreates the schema and demo data.
param([switch]$ResetData)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ComposePath = Join-Path $PSScriptRoot "docker-compose.yml"
$EnvPath = Join-Path $ProjectRoot ".env"

if ($ResetData) {
    & docker compose --env-file $EnvPath -f $ComposePath down -v
} else {
    & docker compose --env-file $EnvPath -f $ComposePath down
}
exit $LASTEXITCODE
