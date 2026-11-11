# Creates the root .env file with random secrets (DB password, JWT secret, temporary admin password).
# Usage (from the project root):  powershell -ExecutionPolicy Bypass -File .\backend\init-env.ps1 [-Force]
# The file is ASCII-only on purpose: Windows PowerShell 5.1 reads scripts without BOM in the ANSI code page.
param(
    [string]$AdminEmail = "admin@hotel.local",
    [string]$AdminName = "Hotel Administrator",
    [int]$AppPort = 8080,
    [int]$PostgresPort = 55432,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$EnvPath = Join-Path $ProjectRoot ".env"

if ((Test-Path $EnvPath) -and -not $Force) {
    throw ".env already exists: $EnvPath. Use -Force to replace it (the database volume keeps the OLD passwords!)."
}

$rng = [Security.Cryptography.RandomNumberGenerator]::Create()

function New-RandomBytes([int]$Length) {
    $bytes = New-Object byte[] $Length
    $rng.GetBytes($bytes)
    return $bytes
}

function Get-RandomChar([string]$Alphabet) {
    $b = New-RandomBytes 4
    $n = [BitConverter]::ToUInt32($b, 0)
    return $Alphabet[[int]($n % [uint32]$Alphabet.Length)]
}

# Temporary admin password: 16 chars, upper + lower + digit + special (passes the server password policy).
# No '$', '#', quotes or spaces - they have special meaning in .env files.
function New-TemporaryPassword {
    $upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    $lower = "abcdefghijkmnopqrstuvwxyz"
    $digits = "23456789"
    $special = "!@%-_+"
    $all = $upper + $lower + $digits + $special
    $chars = @((Get-RandomChar $upper), (Get-RandomChar $lower), (Get-RandomChar $digits), (Get-RandomChar $special))
    while ($chars.Count -lt 16) { $chars += (Get-RandomChar $all) }
    # Fisher-Yates shuffle
    for ($i = $chars.Count - 1; $i -gt 0; $i--) {
        $j = [int]([BitConverter]::ToUInt32((New-RandomBytes 4), 0) % [uint32]($i + 1))
        $tmp = $chars[$i]; $chars[$i] = $chars[$j]; $chars[$j] = $tmp
    }
    return -join $chars
}

# Base64 without '/', '+' and '=' is not required by Docker, but alphanumerics keep the DB password simple.
$DbPassword = ([Convert]::ToBase64String((New-RandomBytes 30))) -replace '[^A-Za-z0-9]', 'x'
$JwtSecret = [Convert]::ToBase64String((New-RandomBytes 64))
$AdminPassword = New-TemporaryPassword
$rng.Dispose()

$content = @"
POSTGRES_DB=hotel_booking
POSTGRES_USER=hotel_app
POSTGRES_PASSWORD=$DbPassword

JWT_SECRET_BASE64=$JwtSecret
JWT_ISSUER=hotel-booking
ACCESS_TOKEN_MINUTES=15
REFRESH_TOKEN_DAYS=14

BOOTSTRAP_ADMIN_EMAIL=$AdminEmail
BOOTSTRAP_ADMIN_NAME=$AdminName
BOOTSTRAP_ADMIN_PASSWORD=$AdminPassword

CORS_ALLOWED_ORIGINS=http://localhost:$AppPort
COOKIE_SECURE=false
PERMANENT_DELETE_ENABLED=true
APP_PORT=$AppPort
POSTGRES_PORT=$PostgresPort
"@

[IO.File]::WriteAllText($EnvPath, $content, (New-Object Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "Created: $EnvPath" -ForegroundColor Green
Write-Host "Admin login:              $AdminEmail" -ForegroundColor Cyan
Write-Host "Temporary admin password: $AdminPassword" -ForegroundColor Yellow
Write-Host "The password must be changed after the first sign-in. Do not commit .env." -ForegroundColor DarkYellow
Write-Host ""
