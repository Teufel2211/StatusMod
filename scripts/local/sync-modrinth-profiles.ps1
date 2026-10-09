# Kopiert die jeweils neuesten StatusMod-JARs aus dist/multiversion in die
# passenden Modrinth-App-Profile ("Status Mod <Loader> <MC>"/mods).
# Bestehende Statusmod-*.jar werden vorher entfernt (keine Duplikate).
param(
    [string]$RepoRoot = "",
    [string]$ProfilesRoot = "",
    [string]$ModVersion = "",
    [string[]]$Exclude = @("Status mod test fabric")
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($RepoRoot)) { $RepoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent }
if ([string]::IsNullOrWhiteSpace($ProfilesRoot)) {
    $ProfilesRoot = Join-Path $env:APPDATA "ModrinthApp\profiles"
}
if ([string]::IsNullOrWhiteSpace($ModVersion)) { $ModVersion = (Get-Content (Join-Path $RepoRoot "version.txt") -Raw).Trim() }
$dist = Join-Path $RepoRoot "dist\multiversion"
$fapiCache = Join-Path $RepoRoot ".plain-libs\fapi-profiles"
New-Item -ItemType Directory -Force -Path $fapiCache | Out-Null

# Fabric API (fat JarJar bundle) pro MC - Checksum aus Modrinth-API.
$FapiMap = @{
    "1.21.11" = "0.141.6+1.21.11"
    "26.1"    = "0.145.1+26.1"
    "26.1.1"  = "0.145.4+26.1.1"
    "26.1.2"  = "0.155.3+26.1.2"
    "26.2"    = "0.161.0+26.2"
    "26.3"    = "0.162.0+26.3"
}

function Get-FabricApiJar([string]$Mc) {
    if (-not $FapiMap.ContainsKey($Mc)) { return $null }
    $ver = $FapiMap[$Mc]
    $local = Join-Path $fapiCache "fabric-api-$ver.jar"
    if (Test-Path -LiteralPath $local) { return $local }
    $q = [uri]::EscapeDataString('["' + $Mc + '"]')
    $l = [uri]::EscapeDataString('["fabric"]')
    $v = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$q&loaders=$l&limit=5" -TimeoutSec 60 |
        Where-Object { $_.version_number -eq $ver } | Select-Object -First 1
    if (-not $v) {
        $v = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$q&loaders=$l&limit=1" -TimeoutSec 60 |
            Select-Object -First 1
    }
    if (-not $v) { return $null }
    $file = @($v.files | Where-Object { $_.primary })[0]
    if (-not $file) { $file = $v.files[0] }
    if (-not $file) { return $null }
    Invoke-WebRequest $file.url -OutFile $local -TimeoutSec 600
    return $local
}

$done = 0
$skipped = @()
foreach ($dir in @(Get-ChildItem -LiteralPath $ProfilesRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "Status Mod *" })) {
    if ($Exclude -contains $dir.Name) { continue }
    $rest = $dir.Name.Substring(("Status Mod ").Length).Trim()
    $loader = ""
    $mc = ""
    foreach ($cand in @("fabric", "forge", "neoforge", "neforge", "quilt")) {
        if ($rest -like "$cand *") {
            $loader = $cand
            $mc = $rest.Substring($cand.Length).Trim()
            break
        }
    }
    if ($loader -eq "neforge") { $loader = "neoforge" }
    if ([string]::IsNullOrWhiteSpace($loader) -or [string]::IsNullOrWhiteSpace($mc)) {
        $skipped += "$($dir.Name) (Name nicht parsbar)"
        continue
    }
    if ($loader -eq "fabric") {
        $jar = Join-Path $dist "fabric\$mc\Statusmod-$ModVersion-fabric-$mc.jar"
    } else {
        $jar = Join-Path $dist "$loader\Statusmod-$ModVersion-$loader-$mc.jar"
    }
    if (-not (Test-Path -LiteralPath $jar)) {
        $skipped += "$($dir.Name) (keine JAR: $jar)"
        continue
    }
    $modsDir = Join-Path $dir.FullName "mods"
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    Get-ChildItem -LiteralPath $modsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "Statusmod-*.jar" -or $_.Name -like "statusmod-*.jar" } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    Copy-Item -LiteralPath $jar -Destination (Join-Path $modsDir (Split-Path $jar -Leaf)) -Force
    Write-Output "OK: $($dir.Name) <- $(Split-Path $jar -Leaf)"
    $done++
    # Fabric/Quilt brauchen die Fabric API in der passenden MC-Version
    # (falsche Versionen ablehnen den Start, fehlende crashen).
    if ($loader -eq "fabric" -or $loader -eq "quilt") {
        $fapi = Get-FabricApiJar $mc
        if ($fapi) {
            $wantName = Split-Path $fapi -Leaf
            Get-ChildItem -LiteralPath $modsDir -File -ErrorAction SilentlyContinue |
                Where-Object { ($_.Name -like "fabric-api-*.jar") -and ($_.Name -ne $wantName) } |
                ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
            if (-not (Test-Path -LiteralPath (Join-Path $modsDir $wantName))) {
                Copy-Item -LiteralPath $fapi -Destination (Join-Path $modsDir $wantName) -Force
                Write-Output "   + FAPI: $wantName"
            }
        } else {
            Write-Output "   ! FAPI fuer $mc nicht gefunden"
        }
    }
}

Write-Output ""
Write-Output "$done Profile aktualisiert, $($skipped.Count) uebersprungen."
foreach ($s in $skipped) { Write-Output "  SKIP: $s" }
