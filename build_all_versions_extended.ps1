# Build StatusMod for ALL Minecraft versions from 1.21.10 down to 1.19
# Note: Some API versions may not be available - script will skip those

$versions = @(
    # 1.21.x series
    @{ minecraft = "1.21.10"; yarn = "1.21.10+build.1"; fabric_api = "0.136.0+1.21.10" },
    @{ minecraft = "1.21.9"; yarn = "1.21.9+build.1"; fabric_api = "0.136.0+1.21.9" },
    @{ minecraft = "1.21.8"; yarn = "1.21.8+build.1"; fabric_api = "0.135.0+1.21.8" },
    @{ minecraft = "1.21.7"; yarn = "1.21.7+build.1"; fabric_api = "0.134.0+1.21.7" },
    @{ minecraft = "1.21.6"; yarn = "1.21.6+build.1"; fabric_api = "0.134.0+1.21.6" },
    @{ minecraft = "1.21.5"; yarn = "1.21.5+build.1"; fabric_api = "0.134.0+1.21.5" },
    @{ minecraft = "1.21.4"; yarn = "1.21.4+build.1"; fabric_api = "0.132.0+1.21.4" },
    @{ minecraft = "1.21.3"; yarn = "1.21.3+build.1"; fabric_api = "0.132.0+1.21.3" },
    @{ minecraft = "1.21.2"; yarn = "1.21.2+build.1"; fabric_api = "0.132.0+1.21.2" },
    @{ minecraft = "1.21.1"; yarn = "1.21.1+build.1"; fabric_api = "0.132.0+1.21.1" },
    
    # 1.20.x series
    @{ minecraft = "1.20.6"; yarn = "1.20.6+build.1"; fabric_api = "0.100.0+1.20.6" },
    @{ minecraft = "1.20.5"; yarn = "1.20.5+build.1"; fabric_api = "0.100.0+1.20.5" },
    @{ minecraft = "1.20.4"; yarn = "1.20.4+build.1"; fabric_api = "0.100.0+1.20.4" },
    @{ minecraft = "1.20.3"; yarn = "1.20.3+build.1"; fabric_api = "0.100.0+1.20.3" },
    @{ minecraft = "1.20.2"; yarn = "1.20.2+build.1"; fabric_api = "0.92.0+1.20.2" },
    @{ minecraft = "1.20.1"; yarn = "1.20.1+build.1"; fabric_api = "0.92.2+1.20.1" },
    
    # 1.19.x series
    @{ minecraft = "1.19.4"; yarn = "1.19.4+build.1"; fabric_api = "0.90.7+1.19.4" },
    @{ minecraft = "1.19.3"; yarn = "1.19.3+build.1"; fabric_api = "0.82.2+1.19.3" },
    @{ minecraft = "1.19.2"; yarn = "1.19.2+build.1"; fabric_api = "0.80.0+1.19.2" },
    @{ minecraft = "1.19"; yarn = "1.19+build.1"; fabric_api = "0.76.0+1.19" }
)

$buildDir = "build/libs"
$successCount = 0
$failCount = 0
$successVersions = @()

Write-Host "`n===========================================" -ForegroundColor Cyan
Write-Host "     STATUSMOD MULTI-VERSION BUILD" -ForegroundColor Green
Write-Host "   Building for $($versions.Count) Minecraft versions" -ForegroundColor Cyan
Write-Host "==========================================`n" -ForegroundColor Cyan

if (-not (Test-Path $buildDir)) { 
    New-Item -ItemType Directory -Path $buildDir -Force | Out-Null 
}

foreach ($ver in $versions) {
    $mc = $ver.minecraft
    
    Write-Host "[$($successCount + $failCount + 1)/$($versions.Count)] Building Minecraft $mc..." -NoNewline
    
    # Update gradle.properties
    $gradleProps = @"
minecraft_version=$mc
yarn_mappings=$($ver.yarn)
loader_version=0.16.9
fabric_api_version=$($ver.fabric_api)
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@
    Set-Content -Path "gradle.properties" -Value $gradleProps -ErrorAction Stop
    
    # Build silently
    $buildOutput = & .\gradlew.bat clean build --no-daemon 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        # Find and copy the JAR
        $jar = Get-ChildItem "build/libs/statusmod-*.jar" -Exclude "*-sources.jar" | Select-Object -First 1
        if ($jar) {
            $newName = "$buildDir/statusmod-$mc.jar"
            Copy-Item $jar.FullName $newName -Force
            Write-Host "`r[$($successCount + 1)/$($versions.Count)] Minecraft $mc " -ForegroundColor Green -NoNewline
            Write-Host "✓" -ForegroundColor Green
            $successCount++
            $successVersions += $mc
        }
    } else {
        Write-Host "`r[$($failCount + 1)/$($versions.Count)] Minecraft $mc " -ForegroundColor Red -NoNewline
        Write-Host "✗" -ForegroundColor Red
        $failCount++
    }
}

# Restore latest version
Write-Host "`nRestoring gradle.properties to 1.21.10..." -ForegroundColor Yellow
@"
minecraft_version=1.21.10
yarn_mappings=1.21.10+build.1
loader_version=0.16.9
fabric_api_version=0.136.0+1.21.10
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@ | Set-Content gradle.properties

Write-Host "`n===========================================" -ForegroundColor Cyan
Write-Host "          BUILD SUMMARY" -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host "Total: $($versions.Count) versions" -ForegroundColor White
Write-Host "Success: $successCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================`n" -ForegroundColor Cyan

if ($successCount -gt 0) {
    Write-Host "Built JARs:" -ForegroundColor Cyan
    Get-ChildItem "$buildDir/statusmod-*.jar" -Exclude "*-sources*" | Sort-Object { 
        [version]($_.Name -replace 'statusmod-|\.jar', '')
    } -Descending | ForEach-Object { 
        Write-Host "  ✓ $($_.Name)" -ForegroundColor Green
    }
}

Write-Host ""
