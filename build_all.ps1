#!/usr/bin/env pwsh
# StatusMod Build All Versions Script
# Baut alle verfügbaren Minecraft-Versionen

Write-Host "🔨 StatusMod Multi-Version Builder" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan
Write-Host ""

# Finde alle gradle.properties.<version> Dateien und sortiere sie
$versions = @()
Get-ChildItem gradle.properties.1.* | ForEach-Object {
    $v = $_.Name -replace "gradle.properties\.", ""
    $versions += $v
}

# Sortiere Versionen in korrekter Reihenfolge (neueste zuerst)
$versions = $versions | Sort-Object { 
    $parts = $_ -split '\.'
    [int]$parts[0] * 1000000 + [int]$parts[1] * 1000 + ([int]$parts[2] -as [object])
} -Descending

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
Get-ChildItem "build/libs/statusmod-*.jar" | Where-Object { $_ -notmatch "sources" } | Sort-Object Name -Descending | ForEach-Object {
    Write-Host "  ✓ $($_.Name)" -ForegroundColor Green
}
