param(
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt"),
    [string]$MatrixPath = "scripts/local/mc-matrix-1.21.json",
    [string]$OutputDir = "dist/multiversion",
    [switch]$DryRun,
    [switch]$ContinueOnError
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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
    Set-Content -LiteralPath $FilePath -Value $raw -NoNewline
}

$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
$gradlew = Join-Path $root "gradlew.bat"
$gradleProps = Join-Path $root "gradle.properties"
$matrixFile = Join-Path $root $MatrixPath
$outputRoot = Join-Path $root $OutputDir
$reportFile = Join-Path $outputRoot "build-report.json"
$modVersion = (Get-Content -LiteralPath (Join-Path $root "version.txt") -Raw).Trim()

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

$originalGradleProps = Get-Content -LiteralPath $gradleProps -Raw
$results = New-Object System.Collections.Generic.List[object]

try {
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
            $apiCandidates = @($entry.fabric_api_candidates)
            if ($apiCandidates.Count -eq 0) {
                $apiCandidates = @("0.136.0+$mc", "0.136.0+1.21")
            }

            Write-Host "==> [$loader] Minecraft $mc"
            if ($DryRun) {
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "dry-run"
                    note = "build skipped by -DryRun"
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
                & $gradlew clean build writeVersion --no-daemon
                $exit = $LASTEXITCODE
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
                        note = "fabric_api_version=$apiVersion"
                        artifact = $artifactPath
                    }) | Out-Null
                    $built = $true
                    break
                } else {
                    $lastError = "gradle exit code $exit with fabric_api_version=$apiVersion"
                }
            }

            if (-not $built) {
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "failed"
                    note = $lastError
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
    Set-Content -LiteralPath $gradleProps -Value $originalGradleProps -NoNewline
}

$results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportFile
Write-Host ""
Write-Host "Build report: $reportFile"
$results | Format-Table -AutoSize | Out-String | Write-Host

$failed = @($results | Where-Object { $_.status -eq "failed" }).Count
if ($failed -gt 0 -and -not $ContinueOnError) {
    exit 1
}
