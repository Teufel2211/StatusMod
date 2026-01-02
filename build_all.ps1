#!/usr/bin/env pwsh
# StatusMod Build All Versions Script
# Baut alle verfügbaren Minecraft-Versionen

Write-Host "🔨 StatusMod Multi-Version Builder" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan
Write-Host ""

# Finde alle gradle.properties.<version> Dateien
$versions = @()
Get-ChildItem gradle.properties.* | ForEach-Object {
    $v = $_.Name -replace "gradle.properties\.", ""
    $versions += $v
}

if ($versions.Count -eq 0) {
    Write-Host "❌ Keine Versionsdateien gefunden!" -ForegroundColor Red
    exit 1
}

Write-Host "Gefundene Versionen: $($versions.Count)"
$versions | ForEach-Object { Write-Host "  ✓ $_" }
Write-Host ""

$successCount = 0
$failedVersions = @()

foreach ($version in $versions) {
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    Write-Host "🚀 Baue Version: $version" -ForegroundColor Cyan
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
    
    # Starte build.ps1
    & .\build.ps1 $version
    
    if ($LASTEXITCODE -eq 0) {
        $successCount++
        Write-Host "✅ $version erfolgreich!" -ForegroundColor Green
    } else {
        $failedVersions += $version
        Write-Host "❌ $version fehlgeschlagen!" -ForegroundColor Red
    }
    
    Write-Host ""
}

# Zusammenfassung
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "📊 Zusammenfassung" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "Erfolgreich: $successCount / $($versions.Count)" -ForegroundColor Green

if ($failedVersions.Count -gt 0) {
    Write-Host "Fehlgeschlagen: $($failedVersions.Count)" -ForegroundColor Red
    $failedVersions | ForEach-Object { Write-Host "  ❌ $_" }
}

Write-Host ""
Write-Host "JAR-Dateien in build/libs/:" -ForegroundColor Cyan
Get-ChildItem "build/libs/statusmod-*.jar" | Where-Object { $_ -notmatch "sources" } | ForEach-Object {
    Write-Host "  ✓ $($_.Name)" -ForegroundColor Green
}
