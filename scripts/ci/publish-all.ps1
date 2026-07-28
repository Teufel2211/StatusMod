param()

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$modVersion = (Get-Content (Join-Path $root "version.txt") -Raw).Trim()
$distDir = Join-Path $root "dist\multiversion"
$changelog = if (Test-Path (Join-Path $root "CHANGELOG.md")) {
    (Get-Content (Join-Path $root "CHANGELOG.md") -Raw).Trim()
} else { "" }

$modrinthToken = [string]$env:MODRINTH_TOKEN
$modrinthProjectId = [string]$env:MODRINTH_PROJECT_ID
$curseforgeToken = [string]$env:CURSEFORGE_TOKEN
$curseforgeProjectId = [string]$env:CURSEFORGE_PROJECT_ID

if ([string]::IsNullOrWhiteSpace($modrinthToken)) { throw "MODRINTH_TOKEN not set" }
if ([string]::IsNullOrWhiteSpace($modrinthProjectId)) { throw "MODRINTH_PROJECT_ID not set" }

$hasCurseForge = (-not [string]::IsNullOrWhiteSpace($curseforgeToken)) -and
                  (-not [string]::IsNullOrWhiteSpace($curseforgeProjectId))

$jars = @(Get-ChildItem -Path $distDir -Recurse -File -Filter *.jar)
if ($jars.Count -eq 0) { throw "No JARs found under $distDir" }

function Parse-JarName {
    param([string]$Name)
    $n = [System.IO.Path]::GetFileNameWithoutExtension($Name)
    if ($n -match "^statusmod-$([Regex]::Escape($modVersion))-(fabric|forge|neoforge)-(.+)$") {
        return @($matches[1], $matches[2])
    }
    if ($n -match "^(.+)-(fabric|forge|neoforge)$") {
        return @($matches[2], $matches[1])
    }
    return $null
}

Add-Type -AssemblyName System.IO.Compression.FileSystem

$ok = 0; $fail = 0

foreach ($jar in $jars) {
    $parsed = Parse-JarName -Name $jar.Name
    if ($null -eq $parsed) { Write-Warning "Skipping: $($jar.Name)"; continue }
    $loader = $parsed[0]; $mcVersion = $parsed[1]
    $versionName = "StatusMod $modVersion ($loader $mcVersion)"
    $versionNumber = "$modVersion+$loader+$mcVersion"
    $vType = if ($modVersion -match '-') { "beta" } else { "release" }

    Write-Host "==> $versionName"

    # --- Modrinth ---
    $body = @{
        name = $versionName
        version_number = $versionNumber
        changelog = $changelog
        dependencies = @()
        game_versions = @($mcVersion)
        version_type = $vType
        loaders = @($loader)
        featured = $false
        project_id = $modrinthProjectId
    }
    try {
        Write-Host "   Modrinth..."
        $response = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/versions" -Method Post `
            -Headers @{ "Authorization" = $modrinthToken } `
            -Form @{
                data = ($body | ConvertTo-Json -Depth 4 -Compress)
                file = $jar
            }
        Write-Host "   OK id=$($response.id)"
    } catch {
        Write-Warning "   Modrinth FAILED: $($_.Exception.Message)"
        $fail++
        continue
    }

    # --- CurseForge ---
    if (-not $hasCurseForge) { $ok++; continue }
    try {
        Write-Host "   CurseForge..."
        $cfMetadata = @{
            changelog = $changelog
            changelogType = if ([string]::IsNullOrWhiteSpace($changelog)) { "" } else { "markdown" }
            displayName = $versionName
            releaseType = $vType
            gameVersions = @($mcVersion)
        }
        $null = Invoke-RestMethod -Uri "https://minecraft.curseforge.com/api/projects/$curseforgeProjectId/upload-file" -Method Post `
            -Headers @{ "X-API-Key" = $curseforgeToken } `
            -Form @{
                metadata = ($cfMetadata | ConvertTo-Json -Depth 4 -Compress)
                file = $jar
            }
        Write-Host "   CurseForge OK"
    } catch {
        Write-Warning "   CurseForge FAILED: $($_.Exception.Message)"
        Write-Warning "   (CurseForge may need numeric version IDs; publish manually or add id mapping)"
    }
    $ok++
}

Write-Host "Published: $ok, Failed: $fail"
if ($fail -gt 0) { exit 1 }
