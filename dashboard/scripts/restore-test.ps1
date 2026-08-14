param(
  [Parameter(Mandatory)][string]$DatabaseUrl,
  [Parameter(Mandatory)][string]$BackupFile,
  [Parameter()][string]$TargetDb = "statusmod_restore_test"
)

$ErrorActionPreference = "Stop"
Write-Host "=== Restore Test ==="
Write-Host "Backup: $BackupFile"
Write-Host "Target: $TargetDb"
Write-Host ""

# Decompress if .gz
$restoreFile = $BackupFile
if ($BackupFile -like "*.gz") {
  Write-Host "Decompressing..."
  & gzip -d -k -f $BackupFile
  $restoreFile = $BackupFile -replace '\.gz$', ''
}

# Create temp database
Write-Host "Creating temp database: $TargetDb"
$connStr = $DatabaseUrl -replace '/[^/]+$', "/$TargetDb"
& psql $DatabaseUrl -c "DROP DATABASE IF EXISTS `"$TargetDb`"" 2>$null
& psql $DatabaseUrl -c "CREATE DATABASE `"$TargetDb`""

# Restore
Write-Host "Restoring..."
& pg_restore --dbname=$connStr --no-owner --no-acl --verbose $restoreFile 2>&1

if ($LASTEXITCODE -eq 0) {
  Write-Host "Restore SUCCESS"
  # Count tables
  $tableCount = & psql $connStr -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" 2>$null
  Write-Host "Tables restored: $tableCount"
} else {
  Write-Host "Restore FAILED (exit $LASTEXITCODE)"
}

# Cleanup
Write-Host "Dropping temp database..."
& psql $DatabaseUrl -c "DROP DATABASE IF EXISTS `"$TargetDb`""

if ($restoreFile -ne $BackupFile) { Remove-Item $restoreFile -Force }
Write-Host "=== Done ==="
