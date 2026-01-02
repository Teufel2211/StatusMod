# Quick build script for main Minecraft versions
# Builds: 1.21.10, 1.21, 1.20.4, 1.20.1, 1.19.4

$versions = @(
    @{ mc = "1.21.10"; yarn = "1.21.10+build.1"; api = "0.136.0+1.21.10" },
    @{ mc = "1.21"; yarn = "1.21+build.1"; api = "0.126.0+1.21" },
    @{ mc = "1.20.4"; yarn = "1.20.4+build.1"; api = "0.100.0+1.20.4" },
    @{ mc = "1.20.1"; yarn = "1.20.1+build.1"; api = "0.92.2+1.20.1" },
    @{ mc = "1.19.4"; yarn = "1.19.4+build.1"; api = "0.90.7+1.19.4" }
)

Write-Host "Building StatusMod for 5 main versions`n"

foreach ($v in $versions) {
    Write-Host "Building for $($v.mc)..." -ForegroundColor Yellow
    
    @"
minecraft_version=$($v.mc)
yarn_mappings=$($v.yarn)
loader_version=0.16.9
fabric_api_version=$($v.api)
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@ | Set-Content gradle.properties
    
    $result = & .\gradlew.bat clean build --no-daemon 2>&1 | Select-String "BUILD SUCCESSFUL"
    
    if ($result) {
        $jar = ls build/libs/statusmod-*.jar | Where-Object {$_ -notmatch "sources"} | Select-Object -First 1
        if ($jar) {
            Copy-Item $jar.FullName "build/libs/statusmod-$($v.mc).jar" -Force
            Write-Host "  ✓ Built: statusmod-$($v.mc).jar`n" -ForegroundColor Green
        }
    } else {
        Write-Host "  ✗ Build failed`n" -ForegroundColor Red
    }
}

# Restore
@"
minecraft_version=1.21.10
yarn_mappings=1.21.10+build.1
loader_version=0.16.9
fabric_api_version=0.136.0+1.21.10
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
"@ | Set-Content gradle.properties

Write-Host "`nDone! JARs in build/libs:" -ForegroundColor Green
ls build/libs/statusmod-*.jar | Where-Object {$_ -notmatch "sources"} | ForEach-Object { Write-Host "  - $($_.Name)" }
