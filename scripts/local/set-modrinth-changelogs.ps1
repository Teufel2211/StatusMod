# Setzt den kuratierten CHANGELOG.md-Changelog auf allen Modrinth-Versionen
# der aktuellen version.txt (PATCH /v2/version/{id}).
# Nutzung: $env:MODRINTH_TOKEN='mrp_...'; $env:MODRINTH_PROJECT_ID='nlde8cvK'; .\scripts\local\set-modrinth-changelogs.ps1
param(
    [string]$ModVersion = ""
)

$ErrorActionPreference = "Stop"

$token = ([string]$env:MODRINTH_TOKEN).Trim().Trim('"').Trim("'")
$projectId = ([string]$env:MODRINTH_PROJECT_ID).Trim()
if ([string]::IsNullOrWhiteSpace($token)) { throw "MODRINTH_TOKEN nicht gesetzt" }
if ([string]::IsNullOrWhiteSpace($projectId)) { throw "MODRINTH_PROJECT_ID nicht gesetzt" }

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if ([string]::IsNullOrWhiteSpace($ModVersion)) { $ModVersion = (Get-Content (Join-Path $root "version.txt") -Raw).Trim() }

$clText = Get-Content -LiteralPath (Join-Path $root "CHANGELOG.md") -Raw
$m = [regex]::Match($clText, "(?ms)^## " + [regex]::Escape($ModVersion) + "\b.*?\r?\n([\s\S]*?)(?=^## |\z)")
if (-not $m.Success) { throw "Kein CHANGELOG.md-Abschnitt fuer $ModVersion" }
$changelog = $m.Groups[1].Value.Trim()
Write-Host ("Changelog: " + $changelog.Length + " Zeichen")

$headers = @{ "Authorization" = $token; "User-Agent" = "StatusPlayer/release-script" }
$versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$projectId/version" -Method Get -Headers $headers -TimeoutSec 60
$targets = @($versions | Where-Object { [string]$_.version_number -like "$ModVersion+*" })
Write-Host ("Treffer: " + $targets.Count)
$ok = 0; $fail = 0
foreach ($v in $targets) {
    try {
        $body = @{ changelog = $changelog } | ConvertTo-Json -Compress
        Invoke-RestMethod -Uri ("https://api.modrinth.com/v2/version/" + $v.id) -Method Patch `
            -Headers ($headers + @{ "Content-Type" = "application/json" }) -Body $body -TimeoutSec 60 | Out-Null
        Write-Host ("  OK " + $v.version_number)
        $ok++
    } catch {
        Write-Warning ("  FAILED " + $v.version_number + ": " + $_.Exception.Message)
        $fail++
    }
}
Write-Host ("Fertig: $ok ok, $fail failed")
if ($fail -gt 0) { exit 1 }
