param(
    [ValidateSet("build", "publish", "build-multi-version", "publish-multi-version", "bump-version")]
    [string]$Workflow,
    [ValidateSet("release", "beta", "alpha")]
    [string]$ReleaseType = "release",
    [string[]]$GameVersions = @(),
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt"),
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-GradleWithRetry {
    param([string[]]$GradleArgs, [int]$Attempts = 3)
    $gradlew = Join-Path $root "gradlew.bat"
    for ($i = 1; $i -le $Attempts; $i++) {
        Write-Host "Gradle attempt $i/${Attempts}: $($GradleArgs -join ' ')"
        & $gradlew @GradleArgs
        if ($LASTEXITCODE -eq 0) {
            return
        }
        if ($i -eq $Attempts) {
            throw "Gradle failed after $Attempts attempts."
        }
        Start-Sleep -Seconds (4 * $i)
    }
}

function Get-LastExitCodeSafe {
    if (Test-Path Variable:\LASTEXITCODE) {
        return $LASTEXITCODE
    }
    return 0
}

function Get-Version {
    return (Get-Content -LiteralPath (Join-Path $root "version.txt") -Raw).Trim()
}

function Get-MinecraftVersionFromGradleProperties {
    $line = (Get-Content -LiteralPath (Join-Path $root "gradle.properties") | Where-Object { $_ -match '^minecraft_version=' } | Select-Object -First 1)
    if (-not $line) {
        return ""
    }
    return ($line -replace '^minecraft_version=', '').Trim()
}

function Ensure-PythonRequests {
    & python -c "import requests" 2>$null
    if ($LASTEXITCODE -eq 0) {
        return
    }
    Write-Host "Installing Python dependency: requests"
    & python -m pip install --upgrade requests
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install python package 'requests'."
    }
}

function Get-LatestSemverTag {
    $tags = (& git tag --list "v*" 2>$null) | Where-Object { $_ -match "^v\d+\.\d+\.\d+([.-][0-9A-Za-z.-]+)?$" }
    if (-not $tags) { return "" }
    return ($tags | Sort-Object { [Version](($_ -replace '^v','') -replace '-.*$','') } -Descending | Select-Object -First 1)
}

function New-ReleaseNotes {
    param([string]$OutFile, [string]$Version)
    $tag = "v$Version"
    $last = Get-LatestSemverTag
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("## StatusMod $tag")
    $lines.Add("")
    if ([string]::IsNullOrWhiteSpace($last)) {
        $lines.Add("Initial release notes:")
        $lines.Add("")
        foreach ($line in @((& git log --pretty='- %h %s (@%an)' --no-merges -n 50 2>$null))) {
            $lines.Add([string]$line)
        }
    } else {
        $lines.Add("Changes since `$last`:")
        $lines.Add("")
        foreach ($line in @((& git log "$last..HEAD" --pretty='- %h %s (@%an)' --no-merges 2>$null))) {
            $lines.Add([string]$line)
        }
    }
    if (-not ($lines | Where-Object { $_ -like "- *" })) {
        $lines.Add("- No user-facing commit messages found.")
    }
    $lines -join "`r`n" | Set-Content -LiteralPath $OutFile
}

$root = Resolve-Path (Join-Path $PSScriptRoot "../..")
Set-Location $root
New-Item -ItemType Directory -Force -Path (Join-Path $root "dist/local") | Out-Null

switch ($Workflow) {
    "build" {
        Invoke-GradleWithRetry -GradleArgs @("clean", "build", "writeVersion", "--no-daemon")
        Write-Host "Local build complete."
    }
    "publish" {
        Invoke-GradleWithRetry -GradleArgs @("clean", "build", "writeVersion", "--no-daemon")
        $version = Get-Version
        $mcVersion = Get-MinecraftVersionFromGradleProperties
        $jar = Get-ChildItem -Path (Join-Path $root "build/libs") -Filter *.jar -File |
            Where-Object { $_.Name -notlike "*-sources.jar" } |
            Sort-Object Name |
            Select-Object -First 1
        if (-not $jar) { throw "No runtime JAR found in build/libs." }

        $targetDir = Join-Path $root "dist/local"
        $artifactName = "statusmod-$version-fabric-$mcVersion.jar"
        $jarCopy = Join-Path $targetDir $artifactName
        Copy-Item -LiteralPath $jar.FullName -Destination $jarCopy -Force

        $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $jarCopy).Hash.ToLowerInvariant()
        "$sha  $($jar.Name)" | Set-Content -LiteralPath "$jarCopy.sha256"
        New-ReleaseNotes -OutFile (Join-Path $targetDir "RELEASE_NOTES.md") -Version $version

        if (-not $DryRun) {
            Ensure-PythonRequests
            $env:RELEASE_TYPE = $ReleaseType
            & python (Join-Path $root "scripts/local/publish_multiversion.py") $targetDir
            $uploadExit = Get-LastExitCodeSafe
            if ($uploadExit -ne 0) {
                throw "Upload failed with exit code $uploadExit."
            }
            Write-Host "Local publish finished: uploaded $artifactName"
        } else {
            Write-Host "Dry run complete. Package created, no publish attempted."
        }
    }
    "build-multi-version" {
        & (Join-Path $root "scripts/local/build-multiversion.ps1") -Loaders $Loaders -DryRun:$DryRun -ContinueOnError
        $exitCode = Get-LastExitCodeSafe
        if ($exitCode -ne 0) {
            throw "Multi-version build script failed with exit code $exitCode."
        }
    }
    "publish-multi-version" {
        & (Join-Path $root "scripts/local/build-multiversion.ps1") -Loaders $Loaders -DryRun:$DryRun -ContinueOnError
        $exitCode = Get-LastExitCodeSafe
        if ($exitCode -ne 0) {
            throw "Multi-version build script failed with exit code $exitCode."
        }
        if ($DryRun) {
            Write-Host "Dry run complete. Multi-version build matrix validated, upload skipped."
            break
        }
        if (-not $DryRun) {
            Ensure-PythonRequests
        }
        $env:RELEASE_TYPE = $ReleaseType
        $publishArgs = @((Join-Path $root "scripts/local/publish_multiversion.py"), (Join-Path $root "dist/multiversion"))
        & python @publishArgs
        $uploadExit = Get-LastExitCodeSafe
        if ($uploadExit -ne 0) {
            throw "Multi-version upload failed with exit code $uploadExit."
        }
        Write-Host "Local multi-version publish completed."
    }
    "bump-version" {
        $current = Get-Version
        if ($current -notmatch "^(\d+)\.(\d+)\.(\d+)$") {
            throw "version.txt must be strict semver (x.y.z) for local bump. Current: $current"
        }
        $major = [int]$Matches[1]
        $minor = [int]$Matches[2]
        $patch = [int]$Matches[3] + 1
        $next = "$major.$minor.$patch"
        Set-Content -LiteralPath (Join-Path $root "version.txt") -Value $next
        Write-Host "Version bumped: $current -> $next"
    }
}
