param(
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt", "datapack"),
    [string]$MatrixPath = "scripts/local/mc-matrix-26.json",
    [string]$PackFormatMapPath = "scripts/local/pack-format.json",
    [string]$LoaderVersionsPath = "scripts/local/loader-versions.json",
    [string]$OutputDir = "dist/multiversion",
    [string]$LogDir = "",
    [switch]$UseIsolatedGradleHome = $true,
    [switch]$DryRun,
    [switch]$ContinueOnError = $true
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

function Get-McVersionParts {
    param([string]$Mc)
    if ([string]::IsNullOrWhiteSpace($Mc)) { return $null }
    $m = [regex]::Match($Mc.Trim(), '^(\d+)\.(\d+)(?:\.(\d+))?$')
    if (-not $m.Success) { return $null }
    $maj = [int]$m.Groups[1].Value
    $min = [int]$m.Groups[2].Value
    $pat = if ($m.Groups[3].Success) { [int]$m.Groups[3].Value } else { 0 }
    return @($maj, $min, $pat)
}

function Get-McVersionKey {
    param([string]$Mc)
    $parts = Get-McVersionParts -Mc $Mc
    if ($null -eq $parts) { return $null }
    return ($parts[0] * 1000000) + ($parts[1] * 1000) + $parts[2]
}

function Compare-McVersion {
    param([string]$A, [string]$B)
    $a = Get-McVersionParts -Mc $A
    $b = Get-McVersionParts -Mc $B
    if ($null -eq $a -or $null -eq $b) { return $null }
    if ($a[0] -ne $b[0]) { return [Math]::Sign($a[0] - $b[0]) }
    if ($a[1] -ne $b[1]) { return [Math]::Sign($a[1] - $b[1]) }
    if ($a[2] -ne $b[2]) { return [Math]::Sign($a[2] - $b[2]) }
    return 0
}

function Is-BaseRelease {
    param([string]$Mc)
    return ($Mc -match '^\d+\.\d+$')
}

function Get-PackFormatForMinecraft {
    param([string]$MinecraftVersion, [object[]]$PackFormatRanges)
    if ([string]::IsNullOrWhiteSpace($MinecraftVersion)) { return $null }
    $mcKey = Get-McVersionKey -Mc $MinecraftVersion
    if ($null -eq $mcKey) { return $null }
    foreach ($entry in @($PackFormatRanges)) {
        if ($null -eq $entry) { continue }
        $min = [string]$entry.min
        $max = [string]$entry.max
        $pf = $entry.pack_format
        if ([string]::IsNullOrWhiteSpace($min) -or [string]::IsNullOrWhiteSpace($max)) { continue }
        $minKey = Get-McVersionKey -Mc $min
        $maxKey = Get-McVersionKey -Mc $max
        if ($null -eq $minKey -or $null -eq $maxKey) { continue }
        if ($mcKey -lt $minKey) { continue }
        if ($mcKey -gt $maxKey) { continue }
        return $pf
    }
    return $null
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
        $mcParts = Get-McVersionParts -Mc $mc
        if ($mcParts -ne $null) {
            $minorPrefix = "$($mcParts[0]).$($mcParts[1])."
            $escapedMinor = [Regex]::Escape($minorPrefix)
            $withinMinor = @($AllFabricApiVersions | Where-Object { $_ -match ("\+" + $escapedMinor + "\d+$") })
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
            return (Test-Path (Join-Path $Root "forge/build.gradle")) -or
                   (Test-Path (Join-Path $Root "src/main/resources/META-INF/mods.toml")) -or
                   (Test-Path (Join-Path $Root "forge/build.gradle")) -or
                   (Test-Path (Join-Path $Root "forge"))
        }
        "neoforge" {
            return (Test-Path (Join-Path $Root "src/main/resources/META-INF/neoforge.mods.toml")) -or
                   (Test-Path (Join-Path $Root "neoforge/build.gradle")) -or
                   (Test-Path (Join-Path $Root "neoforge"))
        }
        "quilt" {
            return (Test-Path (Join-Path $Root "quilt/build.gradle")) -or
                   (Test-Path (Join-Path $Root "quilt.mod.json")) -or
                   (Test-Path (Join-Path $Root "src/main/resources/quilt.mod.json")) -or
                   (Test-Path (Join-Path $Root "quilt"))
        }
        "datapack" {
            return (Test-Path (Join-Path $Root "datapack")) -or
                   (Test-Path (Join-Path $Root "src/main/resources/datapack"))
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
$packFormatFile = Join-Path $root $PackFormatMapPath
$loaderVersionsFile = Join-Path $root $LoaderVersionsPath
$outputRoot = Join-Path $root $OutputDir
$reportFile = Join-Path $outputRoot "build-report.json"
$modVersion = (Get-Content -LiteralPath (Join-Path $root "version.txt") -Raw).Trim()
$logRoot = if ([string]::IsNullOrWhiteSpace($LogDir)) { Join-Path $outputRoot "logs" } else { Join-Path $root $LogDir }
$isolatedGradleHome = Join-Path $outputRoot ".gradle-user-home"

if (-not (Test-Path $matrixFile)) {
    throw "Matrix file not found: $matrixFile"
}
if (-not (Test-Path $packFormatFile)) {
    Write-Host "Warning: pack format mapping not found: $packFormatFile"
}
if (-not (Test-Path $loaderVersionsFile)) {
    Write-Host "Warning: loader versions mapping not found: $loaderVersionsFile"
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
$packFormatRanges = @()
if (Test-Path $packFormatFile) {
    try {
        $packFormatRanges = Get-Content -LiteralPath $packFormatFile -Raw | ConvertFrom-Json
    } catch {
        Write-Host "Warning: failed to parse pack format mapping: $packFormatFile"
        $packFormatRanges = @()
    }
}
$loaderVersionMap = $null
if (Test-Path $loaderVersionsFile) {
    try {
        $loaderVersionMap = Get-Content -LiteralPath $loaderVersionsFile -Raw | ConvertFrom-Json
    } catch {
        Write-Host "Warning: failed to parse loader versions mapping: $loaderVersionsFile"
        $loaderVersionMap = $null
    }
}

function Get-LoaderVersionConfig {
    param(
        [string]$Loader,
        [string]$MinecraftVersion,
        $Map
    )
    if ($null -eq $Map -or [string]::IsNullOrWhiteSpace($Loader) -or [string]::IsNullOrWhiteSpace($MinecraftVersion)) {
        return $null
    }
    $loaderObj = $Map.$Loader
    if ($null -eq $loaderObj) { return $null }
    $prop = $loaderObj.PSObject.Properties | Where-Object { $_.Name -eq $MinecraftVersion } | Select-Object -First 1
    if ($null -eq $prop) { return $null }
    return $prop.Value
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

        if ($loader -eq "datapack") {
            $loaderOut = Join-Path $outputRoot $loader
            New-Item -ItemType Directory -Force -Path $loaderOut | Out-Null

            foreach ($entry in $matrix) {
                $mc = [string]$entry.minecraft_version
                $packFormat = Get-PackFormatForMinecraft -MinecraftVersion $mc -PackFormatRanges $packFormatRanges
                Write-Host "==> [datapack] Minecraft $mc"

                if ($null -eq $packFormat) {
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "skipped"
                        note = "pack_format not mapped"
                        artifact = ""
                    }) | Out-Null
                    continue
                }

                if ($DryRun) {
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "dry-run"
                        note = "datapack build skipped by -DryRun; pack_format=$packFormat"
                        artifact = ""
                    }) | Out-Null
                    continue
                }

                $tempDir = Join-Path $outputRoot ".tmp-datapack-$mc"
                if (Test-Path $tempDir) {
                    Remove-Item -LiteralPath $tempDir -Recurse -Force
                }
                New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
                $sourcePack = Join-Path $root "datapack"
                if (Test-Path $sourcePack) {
                    Copy-Item -Path (Join-Path $sourcePack "*") -Destination $tempDir -Recurse -Force
                }

                $mcmeta = @{
                    pack = @{
                        pack_format = [int]$packFormat
                        description = "StatusMod datapack for MC $mc (v$modVersion)"
                    }
                } | ConvertTo-Json -Depth 4

                Set-Content -LiteralPath (Join-Path $tempDir "pack.mcmeta") -Value $mcmeta -NoNewline

                $artifactName = "statusmod-$modVersion-$loader-$mc.zip"
                $artifactPath = Join-Path $loaderOut $artifactName
                if (Test-Path $artifactPath) {
                    Remove-Item -LiteralPath $artifactPath -Force
                }
                Compress-Archive -Path (Join-Path $tempDir "*") -DestinationPath $artifactPath -Force

                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "ok"
                    note = "pack_format=$packFormat"
                    artifact = $artifactPath
                }) | Out-Null
            }
            continue
        }

        if ($loader -ne "fabric") {
            $loaderOut = Join-Path $outputRoot $loader
            New-Item -ItemType Directory -Force -Path $loaderOut | Out-Null

            foreach ($entry in $matrix) {
                $mc = [string]$entry.minecraft_version
                $yarn = [string]$entry.yarn_mappings
                $cfg = Get-LoaderVersionConfig -Loader $loader -MinecraftVersion $mc -Map $loaderVersionMap
                if ($null -eq $cfg) {
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "skipped"
                        note = "missing loader version mapping"
                        artifact = ""
                    }) | Out-Null
                    continue
                }

                if ($loader -eq "forge") {
                    if ([string]::IsNullOrWhiteSpace($cfg.forge_version) -or [string]::IsNullOrWhiteSpace($cfg.forge_gradle_version)) {
                        $results.Add([pscustomobject]@{
                            loader = $loader
                            minecraft = $mc
                            status = "skipped"
                            note = "forge_version/forge_gradle_version missing"
                            artifact = ""
                        }) | Out-Null
                        continue
                    }
                }
                if ($loader -eq "neoforge") {
                    if ([string]::IsNullOrWhiteSpace($cfg.neoforge_version) -or [string]::IsNullOrWhiteSpace($cfg.neogradle_version)) {
                        $results.Add([pscustomobject]@{
                            loader = $loader
                            minecraft = $mc
                            status = "skipped"
                            note = "neoforge_version/neogradle_version missing"
                            artifact = ""
                        }) | Out-Null
                        continue
                    }
                }
                if ($loader -eq "quilt") {
                    if ([string]::IsNullOrWhiteSpace($cfg.quilt_loader_version) -or
                        [string]::IsNullOrWhiteSpace($cfg.quilt_mappings) -or
                        [string]::IsNullOrWhiteSpace($cfg.quilt_loom_version)) {
                        $results.Add([pscustomobject]@{
                            loader = $loader
                            minecraft = $mc
                            status = "skipped"
                            note = "quilt_loader_version/quilt_mappings/quilt_loom_version missing"
                            artifact = ""
                        }) | Out-Null
                        continue
                    }
                }

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

                Set-GradlePropertyValue -FilePath $gradleProps -Key "minecraft_version" -Value $mc
                if (-not [string]::IsNullOrWhiteSpace($yarn)) {
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "yarn_mappings" -Value $yarn
                }

                if ($loader -eq "forge") {
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "forge_version" -Value ([string]$cfg.forge_version)
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "forge_gradle_version" -Value ([string]$cfg.forge_gradle_version)
                } elseif ($loader -eq "neoforge") {
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "neoforge_version" -Value ([string]$cfg.neoforge_version)
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "neogradle_version" -Value ([string]$cfg.neogradle_version)
                } elseif ($loader -eq "quilt") {
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "quilt_loader_version" -Value ([string]$cfg.quilt_loader_version)
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "quilt_mappings" -Value ([string]$cfg.quilt_mappings)
                    if ($null -ne $cfg.PSObject.Properties["qsl_version"]) {
                        Set-GradlePropertyValue -FilePath $gradleProps -Key "qsl_version" -Value ([string]$cfg.qsl_version)
                    }
                    Set-GradlePropertyValue -FilePath $gradleProps -Key "quilt_loom_version" -Value ([string]$cfg.quilt_loom_version)
                }

                Write-Host "==> [$loader] Minecraft $mc"
                $logFile = Join-Path $logRoot "$loader-$mc.log"
                $prevEap = $ErrorActionPreference
                $ErrorActionPreference = "Continue"
                try {
                    $gradlewToUse = $gradlew
                    if ($loader -eq "neoforge") {
                        $gradlewToUse = Join-Path $root "neoforge\gradlew.bat"
                    }
                    $cmdLine = '""' + $gradlewToUse + '" -p "' + (Join-Path $root $loader) + '" clean build writeVersion --no-daemon"'
                    cmd /c $cmdLine 2>&1 | Tee-Object -FilePath $logFile
                } finally {
                    $ErrorActionPreference = $prevEap
                }
                $exit = if (Test-Path Variable:\LASTEXITCODE) { $LASTEXITCODE } else { 0 }
                if ($exit -eq 0) {
                    $jar = Get-ChildItem -Path (Join-Path $root $loader "build/libs") -Filter *.jar -File |
                        Where-Object { $_.Name -notlike "*-sources.jar" } |
                        Sort-Object Name |
                        Select-Object -First 1
                    if (-not $jar) {
                        $results.Add([pscustomobject]@{
                            loader = $loader
                            minecraft = $mc
                            status = "failed"
                            note = "no runtime jar found"
                            artifact = ""
                        }) | Out-Null
                        continue
                    }
                    $artifactName = "statusmod-$modVersion-$loader-$mc.jar"
                    $artifactPath = Join-Path $loaderOut $artifactName
                    Copy-Item -LiteralPath $jar.FullName -Destination $artifactPath -Force
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "ok"
                        note = "log=$logFile"
                        artifact = $artifactPath
                    }) | Out-Null
                } else {
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "failed"
                        note = "gradle exit code $exit; log=$logFile"
                        artifact = ""
                    }) | Out-Null
                    if (-not $ContinueOnError) {
                        throw "Build failed for loader=$loader mc=$mc (exit $exit)"
                    }
                }
            }
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
                $candidateCount = 0
                try { $candidateCount = @($apiCandidates).Count } catch { $candidateCount = 0 }
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "dry-run"
                    note = "build skipped by -DryRun; candidates=$candidateCount"
                    artifact = ""
                }) | Out-Null
                continue
            }

            $built = $false
            $lastError = ""
            foreach ($apiVersion in $apiCandidates) {
                Set-GradlePropertyValue -FilePath $gradleProps -Key "minecraft_version" -Value $mc
                # Use Mojang mappings consistently (code uses Mojang names).
                Set-GradlePropertyValue -FilePath $gradleProps -Key "mappings_mode" -Value "mojang"
                Set-GradlePropertyValue -FilePath $gradleProps -Key "yarn_mappings" -Value ""
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
                    try {
                        if (Test-Path $logFile) {
                            $logText = Get-Content -LiteralPath $logFile -Raw
                            if ($logText -match "Failed to find minecraft version") {
                                $lastError = "minecraft version not available"
                                break
                            }
                        }
                    } catch { }
                }
            }

            if (-not $built) {
                if ($lastError -eq "minecraft version not available") {
                    $results.Add([pscustomobject]@{
                        loader = $loader
                        minecraft = $mc
                        status = "skipped"
                        note = $lastError
                        artifact = ""
                    }) | Out-Null
                    continue
                }
                $candidateCount = 0
                try { $candidateCount = @($apiCandidates).Count } catch { $candidateCount = 0 }
                $results.Add([pscustomobject]@{
                    loader = $loader
                    minecraft = $mc
                    status = "failed"
                    note = "$lastError; candidates_tried=$candidateCount"
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
