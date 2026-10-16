# Kopiert StatusMod-JARs in die Modrinth-App-Profile (wie sync) UND registriert
# sie in app.db als Content, sodass sie im Content-Tab mit Icon erscheinen.
# Reines Kopieren reicht nicht: der Content-Tab liest instance_content_entries,
# nicht das Dateisystem (manuelle Kopien landen nur im Files-Tab).
#
# VORAUSSETZUNG: Modrinth App komplett beendet (Taskleiste -> Quit).
# Das Skript legt vorher ein Backup (app.db.register-bak-*) an und ist
# idempotent (erneutes Laufen ändert nichts).
param(
    [string]$RepoRoot = "",
    [string]$ModVersion = ""
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent }
if ([string]::IsNullOrWhiteSpace($ModVersion)) { $ModVersion = (Get-Content (Join-Path $RepoRoot "version.txt") -Raw).Trim() }

$app = Get-Process -Name "Modrinth App" -ErrorAction SilentlyContinue
if ($app) { throw "Modrinth App laeuft noch (PID $($app.Id)) - bitte komplett beenden (Taskleiste -> Quit), dann erneut starten." }

& (Join-Path $PSScriptRoot "sync-modrinth-profiles.ps1") -RepoRoot $RepoRoot -ModVersion $ModVersion
python (Join-Path $PSScriptRoot "register-modrinth-content.py") $RepoRoot $ModVersion
