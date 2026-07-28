param()

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$modVersion = (Get-Content (Join-Path $root "version.txt") -Raw).Trim()
$distDir = Join-Path $root "dist\multiversion"

# --- changelog from git since last tag ---
$lastTag = git describe --tags --abbrev=0 2>$null
$changelog = if ($lastTag) {
    $lines = git log "$lastTag..HEAD" --oneline 2>$null
    if ($lines) { "Changes since $lastTag`n`n$($lines -join "`n")" } else { "" }
} else {
    $lines = git log --oneline -20 2>$null
    if ($lines) { "Changes`n`n$($lines -join "`n")" } else { "" }
}
if ([string]::IsNullOrWhiteSpace($changelog)) { $changelog = "No changelog available" }

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

$pattern = "(?i)^Statusmod-$([Regex]::Escape($modVersion))-(fabric|forge|neoforge)-(\d+(?:\.\d+)*)\.jar$"

function Parse-JarName {
    param([string]$Name)
    $m = [regex]::Match($Name, $pattern)
    if (-not $m.Success) { return $null }
    return @($m.Groups[1].Value, $m.Groups[2].Value)
}

# --- CurseForge: resolve numeric game version IDs ---
$cfGameVersionCache = $null
function Get-CfVersionId {
    param([string]$Name)
    if ($null -eq $cfGameVersionCache) { return $null }
    $entry = $cfGameVersionCache | Where-Object { $_ -is [pscustomobject] -and $_.name -eq $Name }
    if ($null -eq $entry) { return $null }
    return $entry.id
}

if ($hasCurseForge) {
    try {
        Write-Host "Fetching CurseForge game versions..."
        $cfGameVersionCache = Invoke-RestMethod -Uri "https://minecraft.curseforge.com/api/game/versions" -Method Get `
            -Headers @{ "X-API-Key" = $curseforgeToken } -ErrorAction Stop
        Write-Host "   OK ($($cfGameVersionCache.Count) versions)"
    } catch {
        Write-Warning "   FAILED to fetch game versions: $($_.Exception.Message)"
        Write-Warning "   CurseForge publishing will be skipped; add IDs manually to continue"
        $hasCurseForge = $false
    }
}

# --- Modrinth: skip already-published ---
$existingVersions = @()
try {
    $existingVersions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$modrinthProjectId/version" -Method Get `
        -Headers @{ "Authorization" = $modrinthToken } -ErrorAction SilentlyContinue
} catch { }
$existingVersionNumbers = New-Object System.Collections.Generic.HashSet[string]
if ($existingVersions -is [array]) {
    foreach ($v in $existingVersions) {
        $existingVersionNumbers.Add([string]$v.version_number) | Out-Null
    }
}

# --- publish ---
$ok = 0; $fail = 0; $skipped = 0

foreach ($jar in $jars) {
    $parsed = Parse-JarName -Name $jar.Name
    if ($null -eq $parsed) { Write-Warning "Skipping unrecognized: $($jar.Name)"; continue }
    $loader = $parsed[0]; $mcVersion = $parsed[1]
    $versionName = "StatusMod $modVersion ($loader $mcVersion)"
    $versionNumber = "$modVersion+$loader+$mcVersion"
    $vType = if ($modVersion -match '-') { "beta" } else { "release" }

    # Skip if already published on Modrinth
    if ($existingVersionNumbers.Contains($versionNumber)) {
        Write-Host "==> $versionName (already published, skipping)"
        $skipped++
        continue
    }

    Write-Host "==> $versionName"

    # --- Modrinth (v2 API: POST /v2/version) ---
    $body = @{
        project_id = $modrinthProjectId
        file_parts = @("file")
        name = $versionName
        version_number = $versionNumber
        changelog = $changelog
        game_versions = @($mcVersion)
        version_type = $vType
        loaders = @($loader)
        featured = $false
        dependencies = @()
        primary_file = "file"
    }
    try {
        Write-Host "   Modrinth..."
        $response = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/version" -Method Post `
            -Headers @{ "Authorization" = $modrinthToken } `
            -Form @{
                data = ($body | ConvertTo-Json -Depth 4 -Compress)
                file = $jar
            }
        Write-Host "   OK id=$($response.id)"
    } catch {
        Write-Warning "   Modrinth FAILED: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            try { $err = $_.Exception.Response.GetResponseStream(); $reader = New-Object System.IO.StreamReader($err); $errBody = $reader.ReadToEnd(); Write-Warning "   Response: $errBody" } catch {}
        }
        $fail++
        continue
    }

    # --- CurseForge ---
    if (-not $hasCurseForge) { $ok++; continue }

    $cfGameVersions = New-Object System.Collections.Generic.List[int]
    $mcId = Get-CfVersionId -Name $mcVersion
    if ($mcId) { $cfGameVersions.Add($mcId) }
    $loaderId = Get-CfVersionId -Name ($loader.Substring(0,1).ToUpper() + $loader.Substring(1))
    if (-not $loaderId) {
        $cfAliases = @{ "fabric" = "Fabric"; "forge" = "Forge"; "neoforge" = "NeoForge" }
        $loaderId = Get-CfVersionId -Name $cfAliases[$loader]
    }
    if ($loaderId) { $cfGameVersions.Add($loaderId) }
    $envId = Get-CfVersionId -Name "Client and Server"  # environment: both
    if ($envId) { $cfGameVersions.Add($envId) }

    if ($cfGameVersions.Count -lt 3) {
        Write-Warning "   CurseForge: missing game version IDs (found $($cfGameVersions.Count)/3), skipping"
        Write-Warning "   Found MC=$mcId, Loader=$loaderId, Env=$envId"
        $ok++
        continue
    }

    try {
        Write-Host "   CurseForge..."
        $cfMetadata = @{
            changelog = $changelog
            changelogType = "markdown"
            displayName = $versionName
            releaseType = $vType
            gameVersions = @($cfGameVersions)
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
    }
    $ok++
}

Write-Host "Published: $ok, Skipped: $skipped, Failed: $fail"
if ($fail -gt 0) { exit 1 }
