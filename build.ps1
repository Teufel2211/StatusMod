#!/usr/bin/env pwsh
# StatusMod Build Script für verschiedene Minecraft-Versionen
# Usage: .\build.ps1 [version]
# Beispiele: .\build.ps1 1.21.10 | .\build.ps1 1.20.4

param(
    [string]$Version = "1.21.10"
)

Write-Host "🔨 StatusMod Builder" -ForegroundColor Cyan
Write-Host "===================" -ForegroundColor Cyan
Write-Host "Version: $Version"

# Überprüfe, ob gradle.properties.<version> existiert
$configFile = "gradle.properties.$Version"
if (-not (Test-Path $configFile)) {
    Write-Host "❌ Fehler: $configFile nicht gefunden!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Verfügbare Versionen:" -ForegroundColor Yellow
    Get-ChildItem gradle.properties.* | ForEach-Object {
        $v = $_.Name -replace "gradle.properties\.", ""
        Write-Host "  - $v"
    }
    exit 1
}

# Kopiere gradle.properties.<version> zu gradle.properties
Write-Host "📋 Lade Konfiguration aus $configFile..." -ForegroundColor Cyan
Copy-Item $configFile gradle.properties -Force
Write-Host "✓ gradle.properties aktualisiert"

# Clean
Write-Host "🧹 Räume auf..." -ForegroundColor Cyan
& .\gradlew.bat clean 2>&1 | Out-Null

# Build
Write-Host "🚀 Kompiliere... (das kann eine Weile dauern)" -ForegroundColor Cyan
$buildOutput = & .\gradlew.bat build --no-daemon 2>&1
$buildOutput | Write-Host

# Überprüfe ob Build erfolgreich war
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Build fehlgeschlagen!" -ForegroundColor Red
    exit 1
}

# JAR umbenennen
Write-Host "📦 Kopiere JAR-Datei..." -ForegroundColor Cyan
$jar = Get-ChildItem "build/libs/statusmod-*.jar" | Where-Object { $_ -notmatch "sources" } | Select-Object -First 1
if ($jar) {
    $jarName = "statusmod-$Version.jar"
    Copy-Item $jar.FullName "build/libs/$jarName" -Force
    Write-Host "✓ Datei gespeichert: build/libs/$jarName" -ForegroundColor Green
} else {
    Write-Host "❌ Keine JAR-Datei gefunden!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✅ Build erfolgreich!" -ForegroundColor Green
Write-Host "📁 Output: build/libs/statusmod-$Version.jar" -ForegroundColor Cyan
