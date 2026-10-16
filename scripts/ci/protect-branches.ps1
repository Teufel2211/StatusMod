# Richtet Branch Protection für main + development ein.
# Nur Owner (Repo-Admin) kann das setzen. In der EIGENEN Shell laufen lassen
# (braucht gh mit Admin-Rechten), NICHT im Agent-Sandbox-Token.
#
#   .\scripts\ci\protect-branches.ps1          # Dry-Run: zeigt nur das Payload
#   .\scripts\ci\protect-branches.ps1 -Apply   # Setzt den Schutz wirklich
#
# Schutz: KEINE PR-Pflicht (Owner arbeitet direkt), aber kein Force-Push,
# kein Löschen, Admins eingeschlossen. Externe kommen nur per Fork-PR rein
# (sie haben eh keine Schreibrechte) — Owner-Review vor Merge.

param(
    [switch]$Apply,
    [string]$Owner = "Teufel2211",
    [string]$Repo = "Status-Player"
)

$ErrorActionPreference = "Stop"

$body = @{
    required_status_checks         = $null
    enforce_admins                 = $true
    required_pull_request_reviews  = $null
    restrictions                   = $null
    required_linear_history        = $false
    allow_force_pushes             = $false
    allow_deletions                = $false
    block_creations                = $false
    required_conversation_resolution = $false
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
    Write-Host "Schutz gesetzt für $branch"
}

if (-not $Apply) {
    Write-Host ""
    Write-Host "Dry-Run only. Mit -Apply wirklich setzen."
}
