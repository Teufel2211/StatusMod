param(
  [Parameter(Mandatory)][string]$DatabaseUrl,
  [Parameter()][string]$BackupDir = "./backups",
  [Parameter()][string]$GpgRecipient = "",
  [Parameter()][int]$RetentionDays = 7
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = Join-Path $BackupDir "statusmod-db-$timestamp.sql"
$logFile = Join-Path $BackupDir "backup.log"

if (-not (Test-Path $BackupDir)) { New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null }

function Write-Log { param([string]$Msg) $msg = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Msg"; Write-Host $msg; Add-Content -Path $logFile -Value $msg }

Write-Log "Starting backup..."

# pg_dump
$env:PGPASSWORD = $DatabaseUrl -replace '.*://[^:]+:([^@]+)@.*', '$1'
$pgArgs = @(
  "--dbname=$DatabaseUrl"
  "--format=custom"
  "--file=$backupFile"
  "--verbose"
  "--no-owner"
  "--no-acl"
)

Write-Log "Running pg_dump..."
& pg_dump @pgArgs 2>> $logFile
if ($LASTEXITCODE -ne 0) { Write-Log "ERROR: pg_dump failed (exit $LASTEXITCODE)"; exit 1 }

# Compress
Write-Log "Compressing..."
& gzip -f $backupFile
$gzFile = "$backupFile.gz"
if (-not (Test-Path $gzFile)) { Write-Log "ERROR: compression failed"; exit 1 }

# GPG encrypt
if ($GpgRecipient) {
  Write-Log "Encrypting with GPG recipient: $GpgRecipient"
  & gpg --batch --yes --trust-model always --recipient $GpgRecipient --encrypt $gzFile
  Remove-Item $gzFile -Force
  Write-Log "Encrypted: $gzFile.gpg"
} else {
  Write-Log "No GPG recipient - stored uncompressed: $gzFile"
}

# Cleanup old backups
Write-Log "Cleaning backups older than $RetentionDays days..."
$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem $BackupDir -Filter "*.sql*" | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
  Remove-Item $_.FullName -Force
  Write-Log "Deleted old backup: $($_.Name)"
}

Write-Log "Backup complete: $gzFile"
exit 0
