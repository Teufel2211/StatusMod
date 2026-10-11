# Pre-release token check: verifies every token/ID the release process needs
# WITHOUT publishing anything. Read-only calls only, secrets are never printed.
#
# Checks:
#   Modrinth:  MODRINTH_TOKEN + MODRINTH_PROJECT_ID (project readable, versions listable)
#   CurseForge: CURSEFORGE_TOKEN + CURSEFORGE_PROJECT_ID (optional; game versions + project)
#   GitHub:    gh auth status + repo access (for CI releases)
#   Supabase:  Dashboard env (NEXT_PUBLIC_SUPABASE_URL + SERVICE_ROLE_KEY via dashboard/.env.local)
#
# Usage:
#   .\scripts\ci\check-release-tokens.ps1
# Exit code 0 = all required checks pass (CurseForge may warn-only).

param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Continue"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent }

$fail = 0
$warn = 0

function Write-Ok([string]$msg) { Write-Host "  [OK] $msg" }
function Write-Fail([string]$msg) { Write-Host "  [FAIL] $msg"; $script:fail++ }
function Write-Warn([string]$msg) { Write-Host "  [WARN] $msg"; $script:warn++ }
function Mask([string]$v) {
    if ([string]::IsNullOrEmpty($v)) { return "(leer)" }
    if ($v.Length -le 8) { return "****" }
    return $v.Substring(0, 4) + "..." + $v.Substring($v.Length - 4)
}

# Load dashboard/.env.local for Supabase check (values stay in memory, never printed).
$dashboardEnv = @{}
$envLocal = Join-Path $RepoRoot "dashboard\.env.local"
if (Test-Path -LiteralPath $envLocal) {
    Get-Content -LiteralPath $envLocal | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $dashboardEnv[$Matches[1]] = $Matches[2].Trim('"', "'", " ")
        }
    }
}

Write-Host "=== Modrinth (Pflicht) ==="
$mrToken = [string]$env:MODRINTH_TOKEN
$mrProject = [string]$env:MODRINTH_PROJECT_ID
if ([string]::IsNullOrWhiteSpace($mrToken)) { Write-Fail "MODRINTH_TOKEN nicht gesetzt" }
else { Write-Host "  MODRINTH_TOKEN gesetzt ($(Mask $mrToken))" }
if ([string]::IsNullOrWhiteSpace($mrProject)) { Write-Fail "MODRINTH_PROJECT_ID nicht gesetzt" }
else { Write-Host "  MODRINTH_PROJECT_ID=$mrProject" }
if (-not [string]::IsNullOrWhiteSpace($mrToken) -and -not [string]::IsNullOrWhiteSpace($mrProject)) {
    try {
        $proj = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$mrProject" -Method Get `
            -Headers @{ "Authorization" = $mrToken } -TimeoutSec 30 -ErrorAction Stop
        Write-Ok "Projekt lesbar: $($proj.title) (id=$($proj.id))"
    } catch { Write-Fail "Projekt-Abfrage: $($_.Exception.Message)" }
    try {
        $vers = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$mrProject/version" -Method Get `
            -Headers @{ "Authorization" = $mrToken } -TimeoutSec 30 -ErrorAction Stop
        $n = if ($vers -is [array]) { $vers.Count } else { 0 }
        Write-Ok "Versionsliste lesbar ($n Eintraege)"
    } catch { Write-Fail "Versions-Abfrage: $($_.Exception.Message)" }
}

Write-Host "=== CurseForge (Pflicht) ==="
$cfToken = [string]$env:CURSEFORGE_TOKEN
$cfProject = [string]$env:CURSEFORGE_PROJECT_ID
if ([string]::IsNullOrWhiteSpace($cfToken)) { Write-Fail "CURSEFORGE_TOKEN nicht gesetzt" }
else { Write-Host "  CURSEFORGE_TOKEN gesetzt ($(Mask $cfToken))" }
if ([string]::IsNullOrWhiteSpace($cfProject)) { Write-Fail "CURSEFORGE_PROJECT_ID nicht gesetzt" }
else { Write-Host "  CURSEFORGE_PROJECT_ID=$cfProject" }
if (-not [string]::IsNullOrWhiteSpace($cfToken) -and -not [string]::IsNullOrWhiteSpace($cfProject)) {
    Write-Host "  CURSEFORGE_TOKEN gesetzt ($(Mask $cfToken)), Projekt=$cfProject"
    try {
        $vers = Invoke-RestMethod -Uri "https://minecraft.curseforge.com/api/game/versions" -Method Get `
            -Headers @{ "X-API-Key" = $cfToken } -TimeoutSec 30 -ErrorAction Stop
        Write-Ok "Game-Versions lesbar ($($vers.Count) Eintraege)"
    } catch { Write-Fail "Game-Versions: $($_.Exception.Message)" }
    try {
        $p = Invoke-RestMethod -Uri "https://minecraft.curseforge.com/api/projects/$cfProject" -Method Get `
            -Headers @{ "X-API-Key" = $cfToken } -TimeoutSec 30 -ErrorAction Stop
        Write-Ok "Projekt lesbar: $($p.name) (id=$($p.id))"
    } catch { Write-Fail "Projekt-Abfrage: $($_.Exception.Message)" }
}

Write-Host "=== GitHub (CI-Releases) ==="
try {
    $gh = gh auth status 2>&1 | Out-String
    if ($LASTEXITCODE -eq 0) { Write-Ok "gh eingeloggt"; ($gh -split "`n" | Select-Object -First 3 | ForEach-Object { Write-Host "    $($_.Trim())" }) }
    else { Write-Fail "gh nicht eingeloggt (gh auth login)" }
} catch { Write-Fail "gh CLI fehlt oder Fehler: $($_.Exception.Message)" }
try {
    $repo = gh repo view --json nameWithOwner -q .nameWithOwner 2>$null
    if ($repo) { Write-Ok "Repo-Zugriff: $repo" } else { Write-Warn "Repo nicht ermittelbar (CI nutzt GITHUB_TOKEN, lokal egal)" }
} catch { Write-Warn "Repo-Check uebersprungen" }

Write-Host "=== Supabase (Dashboard-Deploy) ==="
$sbUrl = $dashboardEnv["NEXT_PUBLIC_SUPABASE_URL"]
$sbKey = $dashboardEnv["SERVICE_ROLE_KEY"]
if ([string]::IsNullOrWhiteSpace($sbUrl)) { Write-Warn "NEXT_PUBLIC_SUPABASE_URL fehlt in dashboard/.env.local" }
else { Write-Host "  NEXT_PUBLIC_SUPABASE_URL=$sbUrl" }
if ([string]::IsNullOrWhiteSpace($sbKey)) { Write-Warn "SERVICE_ROLE_KEY fehlt in dashboard/.env.local" }
else { Write-Host "  SERVICE_ROLE_KEY gesetzt ($(Mask $sbKey))" }
if (-not [string]::IsNullOrWhiteSpace($sbUrl) -and -not [string]::IsNullOrWhiteSpace($sbKey)) {
    try {
        $r = Invoke-RestMethod -Uri "$sbUrl/rest/v1/" -Method Get `
            -Headers @{ "apikey" = $sbKey; "Authorization" = "Bearer $sbKey" } -TimeoutSec 30 -ErrorAction Stop
        Write-Ok "Supabase REST erreichbar"
    } catch { Write-Fail "Supabase REST: $($_.Exception.Message)" }
}

Write-Host ""
if ($fail -gt 0) { Write-Host "ERGEBNIS: $fail FEHLER, $warn Warnungen - Release NICHT starten" -ForegroundColor Red; exit 1 }
elseif ($warn -gt 0) { Write-Host "ERGEBNIS: alles Pflichtige OK, $warn Warnungen" -ForegroundColor Yellow; exit 0 }
else { Write-Host "ERGEBNIS: alles OK - bereit fuer Release" -ForegroundColor Green; exit 0 }
