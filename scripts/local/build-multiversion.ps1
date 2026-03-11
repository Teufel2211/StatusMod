param(
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt"),
    [string]$MatrixPath = "scripts/local/mc-matrix-1.21.json",
    [string]$OutputDir = "dist/multiversion",
    [string]$LogDir = "",
    [switch]$UseIsolatedGradleHome = $true,
    [switch]$DryRun,
    [switch]$ContinueOnError
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FabricApiVersionsFromMaven {
    param([string]$MavenBase = "https://maven.fabricmc.net")
    $url = "$MavenBase/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
    try {
        $xmlText = (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 30).Content
        if ([string]::IsNullOrWhiteSpace($xmlText)) { return @() }
        [xml]$doc = $xmlText
        $versions = @($doc.metadata.versioning.versions.version | ForEach-Object { [string]$_ })
        # metadata is typically sorted ascending
        return $versions
    } catch {
        return @()
    }
}

function Parse-McVersion {
    param([string]$Mc)
    $mcText = if ($null -eq $Mc) { "" } else { [string]$Mc }
    $m = [regex]::Match($mcText, '^(\d+)\.(\d+)(?:\.(\d+))?$')
    if (-not $m.Success) { return $null }
    $maj = [int]$m.Groups[1].Value
    $min = [int]$m.Groups[2].Value
    $pat = if ($m.Groups[3].Success) { [int]$m.Groups[3].Value } else { $null }
    return [pscustomobject]@{ major=$maj; minor=$min; patch=$pat }
}

function Select-FabricApiCandidates {
    param(
        [string]$MinecraftVersion,
        [string[]]$ProvidedCandidates,
        [string[]]$AllFabricApiVersions
    )

    $out = New-Object System.Collections.Generic.List[string]
    foreach ($c in @($ProvidedCandidates)) {
        if (-not [string]::IsNullOrWhiteSpace($c)) { $out.Add($c.Trim()) | Out-Null }
    }

    if (-not $AllFabricApiVersions -or $AllFabricApiVersions.Count -eq 0) {
        return @($out | Select-Object -Unique)
    }

    $mc = if ($null -eq $MinecraftVersion) { "" } else { [string]$MinecraftVersion }
    $mc = $mc.Trim()
    if ([string]::IsNullOrWhiteSpace($mc)) {
        return @($out | Select-Object -Unique)
    }

    $exact = @($AllFabricApiVersions | Where-Object { $_ -like "*+$mc" })
    foreach ($v in ($exact | Sort-Object -Descending | Select-Object -First 12)) {
        $out.Add($v) | Out-Null
    }

    # Fallback: pick newest within same minor (e.g. 1.21.*) if exact doesn't exist.
    if ($exact.Count -eq 0) {
        $mcParsed = Parse-McVersion -Mc $mc
        if ($mcParsed -ne $null) {
            $minorPrefix = "$($mcParsed.major).$($mcParsed.minor)."
            $withinMinor = @($AllFabricApiVersions | Where-Object { $_ -match "\+\Q$minorPrefix\E\d+$" })
            if ($withinMinor.Count -gt 0) {
                $best = $withinMinor[-1] # newest (metadata asc) => last
                $out.Add($best) | Out-Null
            }
        }
    }

    return @($out | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
}

function Test-LoaderAvailable {
    param([string]$Root, [string]$Loader)
    switch ($Loader) {
        "fabric" {
            return (Test-Path (Join-Path $Root "fabric.mod.json")) -or
                   (Test-Path (Join-Path $Root "src/main/resources/fabric.mod.json"))
        }
        "forge" {
            return (Test-Path (Join-Path $Root "src/main/resources/META-INF/mods.toml")) -or
                   (Test-Path (Join-Path $Root "forge/build.gradle")) -or
                   (Test-Path (Join-Path $Root "forge"))
        }
        "neoforge" {
            return (Test-Path (Join-Path $Root "neoforge/build.gradle")) -or
                   (Test-Path (Join-Path $Root "neoforge"))
        }
        "quilt" {
            return (Test-Path (Join-Path $Root "quilt.mod.json")) -or
                   (Test-Path (Join-Path $Root "src/main/resources/quilt.mod.json")) -or
                   (Test-Path (Join-Path $Root "quilt"))
        }
        default {
            return $false
        }
    }
}

function Write-TextFileWithRetry {
    param(
        [string]$Path,
        [string]$Content,
        [int]$Attempts = 10
    )
    for ($i = 1; $i -le $Attempts; $i++) {
        try {
            Set-Content -LiteralPath $Path -Value $Content -NoNewline
            return
        } catch {
            if ($i -eq $Attempts) { throw }
            Start-Sleep -Milliseconds (150 * $i)
        }
    }
}

function Set-GradlePropertyValue {
    param(
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )
    $raw = Get-Content -LiteralPath $FilePath -Raw
    $pattern = "(?m)^$([Regex]::Escape($Key))=.*$"
    if ($raw -match $pattern) {
        $raw = [Regex]::Replace($raw, $pattern, "$Key=$Value")
    } else {
        if (-not $raw.EndsWith("`n")) {
            $raw += "`r`n"
        }
        $raw += "$Key=$Value`r`n"
    }
    Write-TextFileWithRetry -Path $FilePath -Content $raw
}

$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
$gradlew = Join-Path $root "gradlew.bat"
$gradleProps = Join-Path $root "gradle.properties"
$matrixFile = Join-Path $root $MatrixPath
$outputRoot = Join-Path $root $OutputDir
$reportFile = Join-Path $outputRoot "build-report.json"
$modVersion = (Get-Content -LiteralPath (Join-Path $root "version.txt") -Raw).Trim()
$logRoot = if ([string]::IsNullOrWhiteSpace($LogDir)) { Join-Path $outputRoot "logs" } else { Join-Path $root $LogDir }
$isolatedGradleHome = Join-Path $outputRoot ".gradle-user-home"

if (-not (Test-Path $matrixFile)) {
    throw "Matrix file not found: $matrixFile"
}
if (-not (Test-Path $gradleProps)) {
    throw "gradle.properties not found: $gradleProps"
}
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found: $gradlew"
}

$matrix = Get-Content -LiteralPath $matrixFile -Raw | ConvertFrom-Json
if (-not $matrix -or $matrix.Count -eq 0) {
    throw "Matrix file is empty: $matrixFile"
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

$originalGradleProps = Get-Content -LiteralPath $gradleProps -Raw
$oldGradleUserHome = [Environment]::GetEnvironmentVariable("GRADLE_USER_HOME", "Process")
$results = New-Object System.Collections.Generic.List[object]
$allFabricApiVersions = Get-FabricApiVersionsFromMaven

try {
    if ($UseIsolatedGradleHome) {
        New-Item -ItemType Directory -Force -Path $isolatedGradleHome | Out-Null
        [Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", $isolatedGradleHome, "Process")
        Write-Host "Using isolated GRADLE_USER_HOME: $isolatedGradleHome"
    }
    if ($allFabricApiVersions.Count -gt 0) {
        Write-Host "Resolved Fabric API version list from maven.fabricmc.net ($($allFabricApiVersions.Count) entries)."
    } else {
        Write-Host "Warning: could not fetch Fabric API version list; using provided candidates only."
    }

    foreach ($loaderRaw in $Loaders) {
        $loader = if ($null -eq $loaderRaw) { "" } else { [string]$loaderRaw }
        $loader = $loader.Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($loader)) {
            continue
        }

        if (-not (Test-LoaderAvailable -Root $root -Loader $loader)) {
            Write-Host "[skip] Loader '$loader' is not configured in this repository."
            $results.Add([pscustomobject]@{
                loader = $loader
                minecraft = "-"
                status = "skipped"
                note = "loader not configured in repository"
                artifact = ""
            }) | Out-Null
            continue
        }

        if ($loader -ne "fabric") {
            Write-Host "[skip] Loader '$loader' detected but local build implementation is not wired yet."
            $results.Add([pscustomobject]@{
                loader = $loader
                minecraft = "-"
                status = "skipped"
                note = "loader build command not implemented"
                artifact = ""
            }) | Out-Null
            continue
        }

        $loaderOut = Join-Path $outputRoot $loader
        New-Item -ItemType Directory -Force -Path $loaderOut | Out-Null

        foreach ($entry in $matrix) {
            $mc = [string]$entry.minecraft_version
            $yarn = [string]$entry.yarn_mappings
            $apiCandidates = Select-FabricApiCandidates -MinecraftVersion $mc -ProvidedCandidates @($entry.fabric_api_candidates) -AllFabricApiVersions $allFabricApiVersions

            Write-Host "==> [$loader] Minecraft $mc"
            if ($DryRun) {
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "dry-run"
                    note = "build skipped by -DryRun; candidates=$($apiCandidates.Count)"
                    artifact = ""
                }) | Out-Null
                continue
            }

            $built = $false
            $lastError = ""
            foreach ($apiVersion in $apiCandidates) {
                Set-GradlePropertyValue -FilePath $gradleProps -Key "minecraft_version" -Value $mc
                Set-GradlePropertyValue -FilePath $gradleProps -Key "yarn_mappings" -Value $yarn
                Set-GradlePropertyValue -FilePath $gradleProps -Key "fabric_api_version" -Value ([string]$apiVersion)

                Write-Host "   trying fabric_api_version=$apiVersion"
                $safeApi = ([string]$apiVersion).Replace("+", "_").Replace(":", "_").Replace("/", "_")
                $logFile = Join-Path $logRoot "$loader-$mc-$safeApi.log"
                $prevEap = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                try {
                    $cmdLine = '""' + $gradlew + '" clean build writeVersion --no-daemon"'
                    cmd /c $cmdLine 2>&1 | Tee-Object -FilePath $logFile
                } finally {
                    $ErrorActionPreference = $prevEap
                }
                $exit = if (Test-Path Variable:\LASTEXITCODE) { $LASTEXITCODE } else { 0 }
                if ($exit -eq 0) {
                    $jar = Get-ChildItem -Path (Join-Path $root "build/libs") -Filter *.jar -File |
                        Where-Object { $_.Name -notlike "*-sources.jar" } |
                        Sort-Object Name |
                        Select-Object -First 1
                    if (-not $jar) {
                        $lastError = "No runtime JAR found in build/libs."
                        continue
                    }
                    $artifactName = "statusmod-$modVersion-$loader-$mc.jar"
                    $artifactPath = Join-Path $loaderOut $artifactName
                    Copy-Item -LiteralPath $jar.FullName -Destination $artifactPath -Force
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "ok"
                        note = "fabric_api_version=$apiVersion; log=$logFile"
                        artifact = $artifactPath
                    }) | Out-Null
                    $built = $true
                    break
                } else {
                    $lastError = "gradle exit code $exit with fabric_api_version=$apiVersion (log: $logFile)"
                }
            }

            if (-not $built) {
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "failed"
                    note = "$lastError; candidates_tried=$($apiCandidates.Count)"
                    artifact = ""
                }) | Out-Null
                if (-not $ContinueOnError) {
                    throw "Build failed for loader=$loader mc=$mc ($lastError)"
                }
            }
        }
    }
}
finally {
    [Environment]::SetEnvironmentVariable("GRADLE_USER_HOME", $oldGradleUserHome, "Process")
    Write-TextFileWithRetry -Path $gradleProps -Content $originalGradleProps
}

$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportFile
Write-Host ""
Write-Host "Build report: $reportFile"
$results | Format-Table -AutoSize | Out-String | Write-Host

$failed = @($results | Where-Object { $_.status -eq "failed" }).Count
if ($failed -gt 0 -and -not $ContinueOnError) {
    exit 1
}
