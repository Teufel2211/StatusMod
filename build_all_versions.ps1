# Build StatusMod for multiple Minecraft versions
# This script updates gradle.properties and builds for each version

$versions = @(
    # 1.21.x versions
    @{ minecraft = "1.21.10"; yarn = "1.21.10+build.1"; fabric_api = "0.136.0+1.21.10" },
    @{ minecraft = "1.21.9"; yarn = "1.21.9+build.1"; fabric_api = "0.136.0+1.21.9" },
    @{ minecraft = "1.21.8"; yarn = "1.21.8+build.1"; fabric_api = "0.135.0+1.21.8" },
    @{ minecraft = "1.21.7"; yarn = "1.21.7+build.1"; fabric_api = "0.134.0+1.21.7" },
    @{ minecraft = "1.21.6"; yarn = "1.21.6+build.1"; fabric_api = "0.134.0+1.21.6" },
    @{ minecraft = "1.21.5"; yarn = "1.21.5+build.1"; fabric_api = "0.134.0+1.21.5" },
    @{ minecraft = "1.21.4"; yarn = "1.21.4+build.1"; fabric_api = "0.133.0+1.21.4" },
    @{ minecraft = "1.21.3"; yarn = "1.21.3+build.1"; fabric_api = "0.133.0+1.21.3" },
    @{ minecraft = "1.21.2"; yarn = "1.21.2+build.1"; fabric_api = "0.132.0+1.21.2" },
    @{ minecraft = "1.21.1"; yarn = "1.21.1+build.1"; fabric_api = "0.132.0+1.21.1" },
    @{ minecraft = "1.21"; yarn = "1.21+build.1"; fabric_api = "0.126.0+1.21" },
    # 1.20.x versions
    @{ minecraft = "1.20.5"; yarn = "1.20.5+build.1"; fabric_api = "0.100.0+1.20.5" },
    @{ minecraft = "1.20.4"; yarn = "1.20.4+build.1"; fabric_api = "0.100.0+1.20.4" },
    @{ minecraft = "1.20.3"; yarn = "1.20.3+build.1"; fabric_api = "0.100.0+1.20.3" },
    @{ minecraft = "1.20.2"; yarn = "1.20.2+build.1"; fabric_api = "0.92.2+1.20.2" },
    @{ minecraft = "1.20.1"; yarn = "1.20.1+build.1"; fabric_api = "0.92.2+1.20.1" },
    # 1.19.x versions
    @{ minecraft = "1.19.4"; yarn = "1.19.4+build.1"; fabric_api = "0.90.7+1.19.4" },
    @{ minecraft = "1.19.3"; yarn = "1.19.3+build.1"; fabric_api = "0.82.2+1.19.3" },
    @{ minecraft = "1.19.2"; yarn = "1.19.2+build.1"; fabric_api = "0.80.0+1.19.2" },
    @{ minecraft = "1.19"; yarn = "1.19+build.1"; fabric_api = "0.76.0+1.19" }
)

$buildDir = "build/libs"
if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir -Force | Out-Null }

foreach ($ver in $versions) {
    $mc = $ver.minecraft
    $yarn = $ver.yarn
    $api = $ver.fabric_api
    
    Write-Host "=========================" -ForegroundColor Cyan
    Write-Host "Building for Minecraft $mc" -ForegroundColor Green
    Write-Host "=========================" -ForegroundColor Cyan
    
    # Update gradle.properties
    $gradleProps = @"
minecraft_version=$mc
yarn_mappings=$yarn
loader_version=0.16.9
fabric_api_version=$api
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@
    Set-Content -Path "gradle.properties" -Value $gradleProps
    
    # Clean and build
    Write-Host "Running gradlew clean build..." -ForegroundColor Yellow
    & .\gradlew.bat clean build --no-daemon --quiet
    
    if ($LASTEXITCODE -eq 0) {
        # Copy JAR with version in name
        $jarFile = Get-ChildItem "build/libs/statusmod-*.jar" -Exclude "*-sources.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jarFile) {
            $versionedName = "statusmod-$mc.jar"
            Copy-Item $jarFile.FullName "$buildDir/$versionedName" -Force
            Write-Host "✓ Built: $versionedName" -ForegroundColor Green
        }
    } else {
        Write-Host "✗ Build failed for $mc" -ForegroundColor Red
        break
    }
}

Write-Host "`n=========================" -ForegroundColor Cyan
Write-Host "Build complete! JARs in $buildDir" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Cyan

# Restore latest version
$latestVersion = "1.21.10"
$latestYarn = "1.21.10+build.1"
$latestApi = "0.136.0+1.21.10"

$gradleProps = @"
minecraft_version=$latestVersion
yarn_mappings=$latestYarn
loader_version=0.16.9
fabric_api_version=$latestApi
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@
Set-Content -Path "gradle.properties" -Value $gradleProps
Write-Host "Restored gradle.properties to latest version ($latestVersion)" -ForegroundColor Yellow
