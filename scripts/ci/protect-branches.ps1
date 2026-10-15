# Richtet Branch Protection fuer main + development ein.
# Nur Owner (Repo-Admin) kann das setzen. In der EIGENEN Shell laufen lassen
# (braucht gh mit Admin-Rechten), NICHT im Agent-Sandbox-Token.
#
#   .\scripts\ci\protect-branches.ps1          # Dry-Run: zeigt nur das Payload
#   .\scripts\ci\protect-branches.ps1 -Apply   # Setzt den Schutz wirklich
#
# Schutz: PR vor Merge Pflicht, 1 Approval, Code-Owner-Review Pflicht,
# Stale-Reviews werden verworfen, Admins eingeschlossen, kein Force-Push,
# kein Loeschen. Status-Checks bewusst leer: nach dem ersten gruenen
# "Build & Verify"-Lauf in SECURITY.md als Required Check nachtragen.

param(
    [switch]$Apply,
    [string]$Owner = "Teufel2211",
    [string]$Repo = "Status-Player"
)

$ErrorActionPreference = "Stop"

$body = @{
    required_pull_request_reviews = @{
        required_approving_review_count = 1
        dismiss_stale_reviews           = $true
        require_code_owner_reviews      = $true
    }
    enforce_admins               = $true
    required_status_checks       = $null
    restrictions                 = $null
    allow_force_pushes           = $false
    allow_deletions              = $false
    block_creations              = $false
    required_conversation_resolution = $true
} | ConvertTo-Json -Depth 5

foreach ($branch in @("main", "development")) {
    Write-Host "=== $branch ==="
    if (-not $Apply) {
        Write-Host $body
        continue
    }
    $tmp = [System.IO.Path]::GetTempFileName()
    Set-Content -LiteralPath $tmp -Value $body
    gh api -X PUT "repos/$Owner/$Repo/branches/$branch/protection" --input $tmp | Out-Host
    Remove-Item -LiteralPath $tmp -Force
    Write-Host "Schutz gesetzt fuer $branch"
}

if (-not $Apply) {
    Write-Host ""
    Write-Host "Dry-Run only. Mit -Apply wirklich setzen."
}
