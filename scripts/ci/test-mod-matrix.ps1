<#
.SYNOPSIS
    Startet alle Mods (StatusMod, TimerWave, MapSwitch) mit allen Loadern
    und Versionen als echte Minecraft-Server (Smoke-Test) und berichtet.

.DESCRIPTION
    Fuer jede Kombination Mod x Loader x MC-Version wird ein isolierter
    Server installiert (Fabric-/Quilt-/Forge-/NeoForge-Installer), die Mod-JAR
    (+ Fabric-API bei Fabric/Quilt) in mods/ gelegt und der Server gebootet.
    Erfolg = Mod-Init-Marker im Log + "Done (". Es werden nur Server
    gestartet (kein Singleplayer-Client).

    Aufruf aus StatusMod-Repo:
      .\scripts\ci\test-mod-matrix.ps1 -DryRun
      .\scripts\ci\test-mod-matrix.ps1 -Mods timerwave -Loaders fabric -McVersions 1.21.11
#>
param(
    [string[]]$Mods = @("statusmod", "timerwave", "mapswitch"),
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt"),
    [string[]]$McVersions = @("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"),
    [string]$WorkRoot = "",
    [string]$StatusModDir = "",
    [string]$TimerWaveDir = "",
    [string]$MapSwitchDir = "",
    [int]$TimeoutInitSec = 90,
    [int]$TimeoutDoneSec = 240,
    [int]$SpTimeoutSec = 300,
    [int]$BasePort = 25565,
    [int]$StartIndex = 1,
    [switch]$BuildMissing,
    [switch]$SkipClient,
    [switch]$NoCleanup,
    [switch]$NoQuickPlay,
    [switch]$NoC2,
    [string]$ClientJavaHome = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Komma-Listen von CLI robust aufsplitten ("a,b" -> @("a","b")).
$Mods = @($Mods | ForEach-Object { ($_ -split ',') } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
$Loaders = @($Loaders | ForEach-Object { ($_ -split ',') } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
$McVersions = @($McVersions | ForEach-Object { ($_ -split ',') } | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if ([string]::IsNullOrWhiteSpace($StatusModDir)) { $StatusModDir = $repoRoot.Path }
if ([string]::IsNullOrWhiteSpace($TimerWaveDir)) { $TimerWaveDir = "C:\Users\Steven\Desktop\Mods\TimerWave" }
if ([string]::IsNullOrWhiteSpace($MapSwitchDir)) { $MapSwitchDir = "C:\Users\Steven\Desktop\Mods\MapSwitch" }
if ([string]::IsNullOrWhiteSpace($WorkRoot)) { $WorkRoot = Join-Path ([System.IO.Path]::GetTempPath()) "opencode\mod-matrix" }
New-Item -ItemType Directory -Force -Path $WorkRoot | Out-Null
$sharedDir = Join-Path $WorkRoot "shared"
New-Item -ItemType Directory -Force -Path $sharedDir | Out-Null

if ($WorkRoot -match '\s' -and ($Loaders -contains "quilt")) {
    throw "WorkRoot darf keine Leerzeichen enthalten (quilt-installer): $WorkRoot"
}

$loaderVersionsPath = Join-Path $StatusModDir "scripts\local\loader-versions.json"
$loaderVersionMap = $null
if (Test-Path -LiteralPath $loaderVersionsPath) {
    $loaderVersionMap = Get-Content -LiteralPath $loaderVersionsPath -Raw | ConvertFrom-Json
}

$script:logOffsets = @{}

# ---------------------------------------------------------------------------
# Helpers (aus test-runtime.ps1 uebernommen, MP-Teil)
# ---------------------------------------------------------------------------

function Invoke-Download {
    param([string]$Url, [string]$OutFile)
    $p = $ProgressPreference; $ProgressPreference = "SilentlyContinue"
    try {
        Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing -TimeoutSec 180
    } finally {
        $ProgressPreference = $p
    }
    if (-not (Test-Path -LiteralPath $OutFile)) { throw "Download failed: $Url" }
}

function ConvertTo-ArgumentString {
    param([string[]]$Items)
    if ($null -eq $Items -or $Items.Count -eq 0) { return "" }
    return (($Items | ForEach-Object {
        if ($_ -match '[ "]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }) -join " ")
}

function Start-McProcess {
    param([string]$JavaPath, [string[]]$StartArgs, [string]$WorkingDir, [string]$StdoutFile, [string]$StderrFile)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $JavaPath
    $psi.Arguments = ConvertTo-ArgumentString -Items $StartArgs
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.WorkingDirectory = $WorkingDir
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    [void]$p.Start()
    $runspaces = @()
    $rsOut = [powershell]::Create()
    $null = $rsOut.AddScript({
        param($p, $logFile)
        try {
            while ($null -ne ($line = $p.StandardOutput.ReadLine())) {
                [System.IO.File]::AppendAllText($logFile, $line + [Environment]::NewLine)
            }
        } catch {}
    }).AddArgument($p).AddArgument($StdoutFile)
    $null = $rsOut.BeginInvoke()
    $runspaces += $rsOut
    $rsErr = [powershell]::Create()
    $null = $rsErr.AddScript({
        param($p, $logFile)
        try {
            while ($null -ne ($line = $p.StandardError.ReadLine())) {
                [System.IO.File]::AppendAllText($logFile, $line + [Environment]::NewLine)
            }
        } catch {}
    }).AddArgument($p).AddArgument($StderrFile)
    $null = $rsErr.BeginInvoke()
    $runspaces += $rsErr
    $p | Add-Member -NotePropertyName "SmRunspaces" -NotePropertyValue $runspaces -Force
    return $p
}

function Wait-LogContains {
    param([string]$LogFile, [string]$Pattern, [int]$TimeoutSec = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $LogFile) {
            $offset = 0
            if ($script:logOffsets.ContainsKey($LogFile)) { $offset = $script:logOffsets[$LogFile] }
            $fileLen = (Get-Item -LiteralPath $LogFile).Length
            if ($fileLen -gt $offset) {
                $newContent = $null
                $fs = $null
                try {
                    $fs = [System.IO.File]::Open($LogFile, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
                    $fs.Position = $offset
                    $sr = New-Object System.IO.StreamReader($fs, [System.Text.Encoding]::Default)
                    $newContent = $sr.ReadToEnd()
                    $script:logOffsets[$LogFile] = $fs.Position
                } finally {
                    if ($null -ne $fs) { $fs.Close() }
                }
                if ($null -ne $newContent -and $newContent -match $Pattern) { return $true }
            }
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-LastLogLines {
    param([string]$LogFile, [int]$Count = 25)
    if (-not (Test-Path -LiteralPath $LogFile)) { return @("<no log file: $LogFile>") }
    return @(Get-Content -LiteralPath $LogFile -Tail $Count -ErrorAction SilentlyContinue)
}

function Stop-McProcess {
    param($Process, [switch]$Force)
    if ($null -eq $Process) { return }
    try {
        if (-not $Process.HasExited) {
            if ($Force) {
                # Client: kein stop-Befehl, direkt beenden (spart 30s Wartezeit).
                $Process.Kill()
                [void]$Process.WaitForExit(15000)
            } else {
                $Process.StandardInput.WriteLine("stop")
                $Process.StandardInput.Flush()
                if (-not $Process.WaitForExit(30000)) {
                    $Process.Kill()
                    [void]$Process.WaitForExit(5000)
                }
            }
        }
    } catch {
        try { $Process.Kill() } catch { }
    }
    Start-Sleep -Milliseconds 500
    try {
        if ($Process.SmRunspaces) {
            foreach ($rs in $Process.SmRunspaces) {
                try { $rs.Stop() } catch { }
                try { $rs.Dispose() } catch { }
            }
        }
    } catch { }
}

function Get-MojangVersionJson {
    param([string]$Mc)
    $manifest = Invoke-RestMethod -Uri "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -TimeoutSec 60
    $entry = $manifest.versions | Where-Object { $_.id -eq $Mc } | Select-Object -First 1
    if ($null -eq $entry) { throw "MC $Mc not found in Mojang version manifest" }
    if ([string]::IsNullOrWhiteSpace($entry.url)) { throw "No version JSON URL for MC $Mc" }
    return Invoke-RestMethod -Uri $entry.url -TimeoutSec 60
}

function Get-MatrixLibraryClasspath {
    param($VersionJson, [string]$BaseDir, [hashtable]$ExcludeKeys = @{})
    $paths = New-Object System.Collections.Generic.List[string]
    $libDir = Join-Path $BaseDir "libraries"
    New-Item -ItemType Directory -Force -Path $libDir | Out-Null
    foreach ($lib in $VersionJson.libraries) {
        $key = ""
        if ($lib.PSObject.Properties['name']) { $key = Get-LibKey -Name ([string]$lib.name) }
        if ($key -ne "" -and $ExcludeKeys.ContainsKey($key)) { continue }
        $artifact = $null
        $downloads = $null
        if ($lib.PSObject.Properties['downloads']) { $downloads = $lib.downloads }
        if ($null -ne $downloads -and $downloads.PSObject.Properties['artifact']) { $artifact = $downloads.artifact }
        if (-not $artifact -or -not $artifact.path) { continue }
        # Natives (Fremd-OS eingeschlossen) gehoeren nie auf den Classpath.
        if ($artifact.path -like "*natives*") { continue }
        if ($key -ne "") { $ExcludeKeys[$key] = $true }
        $target = Join-Path $libDir ($artifact.path -replace "/", "\")
        if (Test-Path -LiteralPath $target) { $paths.Add($target); continue }
        $url = ""
        if ($artifact.url) { $url = $artifact.url }
        elseif ($lib.url) { $url = ($lib.url.TrimEnd("/")) + "/" + ($artifact.path -replace "\\", "/") }
        else { $url = "https://libraries.minecraft.net/" + ($artifact.path -replace "\\", "/") }
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        $done = $false
        foreach ($cand in @($url, ("https://repo1.maven.org/maven2/" + ($artifact.path -replace "\\", "/")))) {
            if ([string]::IsNullOrWhiteSpace($cand)) { continue }
            try { Invoke-Download -Url $cand -OutFile $target; $done = $true; break } catch { }
        }
        if ($done) { $paths.Add($target) }
    }
    return $paths
}

function Install-ClientBase {
    # Einmal pro (Loader, EnvMc): Installer-Client, Vanilla-Jar, Natives, Assets.
    # Mods/saves bleiben pro Kombi (GameDir). Erkennt fertige Base an installed.ok.
    param([string]$Loader, [string]$EnvMc, [string]$LoaderVer, [string]$InstallerUrl, [string]$JavaExe, [string]$BaseDir)
    $marker = Join-Path $BaseDir "installed.ok"
    $versionJson = Get-MojangVersionJson -Mc $EnvMc
    if (-not (Test-Path -LiteralPath $marker)) {
        New-Item -ItemType Directory -Force -Path $BaseDir | Out-Null
        $dlDir = Join-Path $BaseDir "dl"
        New-Item -ItemType Directory -Force -Path $dlDir | Out-Null
        $installerJar = Join-Path $dlDir "installer.jar"
        if (-not (Test-Path -LiteralPath $installerJar)) {
            Invoke-Download -Url $InstallerUrl -OutFile $installerJar
        }
        if ($Loader -in @("forge", "neoforge", "quilt")) {
            # forge/neoforge/quilt-Installer verlangen ein Launcher-Profil,
            # forge/neoforge zusaetzlich die Vanilla-Version im versions-Ordner.
            if ($Loader -in @("forge", "neoforge")) {
                $mf = Invoke-RestMethod -Uri "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -TimeoutSec 60
                $me = $mf.versions | Where-Object { $_.id -eq $EnvMc } | Select-Object -First 1
                if ($null -eq $me) { throw "MC $EnvMc not in Mojang manifest" }
                $vanillaDir = Join-Path $BaseDir "versions\$EnvMc"
                New-Item -ItemType Directory -Force -Path $vanillaDir | Out-Null
                $vanillaJson = Join-Path $vanillaDir "$EnvMc.json"
                if (-not (Test-Path -LiteralPath $vanillaJson)) {
                    Invoke-Download -Url $me.url -OutFile $vanillaJson
                }
            }
            $lpFile = Join-Path $BaseDir "launcher_profiles.json"
            if (-not (Test-Path -LiteralPath $lpFile)) {
                $lp = [ordered]@{
                    profiles = @{ "matrix-test" = [ordered]@{
                        name = "matrix-test"; type = "custom"
                        created = "2024-01-01T00:00:00.000Z"; lastUsed = "2024-01-01T00:00:00.000Z"
                        lastVersionId = $EnvMc } }
                    selectedProfile = "matrix-test"
                    clientToken = "00000000-0000-0000-0000-000000000001"
                    authenticationDatabase = @{}
                    selectedUser = @{}
                    launcherVersion = @{ name = "2.0"; format = 21 }
                }
                $lp | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $lpFile -Encoding UTF8
            }
        }
        switch ($Loader) {
            "fabric" {
                $p = Start-Process -FilePath $JavaExe -ArgumentList @("-jar", $installerJar, "client", "-dir", $BaseDir, "-mcversion", $EnvMc, "-loader", $LoaderVer, "-noprofile") -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "fabric-installer client exited $($p.ExitCode)" }
            }
            "quilt" {
                $p = Start-Process -FilePath $JavaExe -ArgumentList @("-jar", $installerJar, "install", "client", $EnvMc, $LoaderVer, "--install-dir=$BaseDir") -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "quilt-installer client exited $($p.ExitCode)" }
            }
            default {
                $p = Start-Process -FilePath $JavaExe -ArgumentList @("-jar", $installerJar, "--installClient", $BaseDir) -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "$Loader installer client exited $($p.ExitCode)" }
            }
        }
        $versionJson = Get-MojangVersionJson -Mc $EnvMc
        Set-Content -LiteralPath $marker -Value "ok $Loader $EnvMc" -Encoding ASCII
    }

    $versionsDir = Join-Path $BaseDir "versions"
    $vanillaDir = Join-Path $versionsDir $EnvMc
    New-Item -ItemType Directory -Force -Path $vanillaDir | Out-Null
    $clientJar = Join-Path $vanillaDir "$EnvMc.jar"
    if (-not (Test-Path -LiteralPath $clientJar)) {
        Invoke-Download -Url $versionJson.downloads.client.url -OutFile $clientJar
    }

    # Natives flach in EIN Verzeichnis (Launcher-Layout).
    # Modernes Mojang-Format: eigene Library-Eintraege "…:natives-windows"
    # (downloads.classifiers ist Legacy und leer).
    $nativesDir = Join-Path $vanillaDir "natives"
    $nativesMarker = Join-Path $vanillaDir "natives.ok"
    $nativesDlls = @(Get-ChildItem -LiteralPath $nativesDir -Filter "*.dll" -File -ErrorAction SilentlyContinue).Count
    if ((-not (Test-Path -LiteralPath $nativesMarker)) -or ($nativesDlls -eq 0)) {
        if (Test-Path -LiteralPath $nativesDir) { Remove-Item -LiteralPath $nativesDir -Recurse -Force }
        if (Test-Path -LiteralPath $nativesMarker) { Remove-Item -LiteralPath $nativesMarker -Force }
        New-Item -ItemType Directory -Force -Path $nativesDir | Out-Null
        foreach ($lib in $versionJson.libraries) {
            $lname = [string]$lib.name
            if ($lname -notlike "*:natives-windows") { continue }
            $allow = $true
            if ($lib.PSObject.Properties['rules']) {
                $allow = $false
                foreach ($rule in $lib.rules) {
                    $applies = $true
                    if ($rule.PSObject.Properties['os'] -and $rule.os.PSObject.Properties['name']) {
                        $applies = ($rule.os.name -eq "windows")
                    }
                    if ($applies) { $allow = ($rule.action -eq "allow") }
                }
            }
            if (-not $allow) { continue }
            $art = $lib.downloads.artifact
            if (-not $art -or -not $art.url) { continue }
            $zip = Join-Path $vanillaDir ("natives-" + ($lname -replace "[:/\\]", "-") + ".zip")
            if (-not (Test-Path -LiteralPath $zip)) { Invoke-Download -Url $art.url -OutFile $zip }
            Expand-Archive -LiteralPath $zip -DestinationPath $nativesDir -Force
        }
        Set-Content -LiteralPath $nativesMarker -Value "ok" -Encoding ASCII
    }

    $assetsDir = Join-Path $BaseDir "assets"
    $indexesDir = Join-Path $assetsDir "indexes"
    New-Item -ItemType Directory -Force -Path $indexesDir | Out-Null
    $assetIndexId = $versionJson.assetIndex.id
    $indexFile = Join-Path $indexesDir "$assetIndexId.json"
    if (-not (Test-Path -LiteralPath $indexFile)) {
        Invoke-Download -Url $versionJson.assetIndex.url -OutFile $indexFile
    }
    $objDir = Join-Path $assetsDir "objects"
    $objMap = (Get-Content -LiteralPath $indexFile -Raw | ConvertFrom-Json).objects
    $missing = @(foreach ($prop in @($objMap.PSObject.Properties)) {
        $h = [string]$prop.Value.hash
        $dst = Join-Path $objDir ($h.Substring(0, 2) + "\" + $h)
        if (-not (Test-Path -LiteralPath $dst)) { $h }
    })
    if ($missing.Count -gt 0) {
        Write-Host "  Lade $($missing.Count) Asset-Objekte ..."
        $staging = Join-Path $BaseDir "asset-dl"
        New-Item -ItemType Directory -Force -Path $staging, $objDir | Out-Null
        $cfg = Join-Path $staging "urls.txt"
        $stagingFwd = $staging.Replace('\', '/')
        $lines = @($missing | ForEach-Object {
            'url = "https://resources.download.minecraft.net/' + $_.Substring(0, 2) + "/" + $_ + '"' +
            [Environment]::NewLine +
            'output = "' + $stagingFwd + '/' + $_ + '"'
        })
        [System.IO.File]::WriteAllLines($cfg, $lines)
        $curlLog = Join-Path $staging "curl.log"
        & curl.exe -s -S -f --parallel --parallel-max 32 --config $cfg *> $curlLog
        foreach ($f in @(Get-ChildItem -LiteralPath $staging -File | Where-Object { $_.Name -ne 'urls.txt' -and $_.Name -ne 'curl.log' })) {
            $targetDir = Join-Path $objDir $f.Name.Substring(0, 2)
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
            Move-Item -LiteralPath $f.FullName -Destination (Join-Path $targetDir $f.Name) -Force
        }
        Remove-Item -LiteralPath $cfg, $curlLog, $staging -Force -Recurse -ErrorAction SilentlyContinue
    }

    $mainClass = ""
    $profileJson = $null
    if ($Loader -eq "fabric") {
        $mainClass = "net.fabricmc.loader.launch.knot.KnotClient"
    } elseif ($Loader -eq "quilt") {
        # Quilt 0.30.x: KnotClient liegt unter impl.launch.knot (Profil-mainClass ist massgeblich).
        $mainClass = "org.quiltmc.loader.impl.launch.knot.KnotClient"
        $qpDir0 = Get-ChildItem -Path $versionsDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "quilt-loader-*" } | Select-Object -First 1
        $qpFile0 = if ($qpDir0) { Get-ChildItem -Path $qpDir0.FullName -Filter "*.json" -File | Select-Object -First 1 } else { $null }
        if ($qpFile0) {
            try {
                $qpMain = (Get-Content -LiteralPath $qpFile0.FullName -Raw | ConvertFrom-Json).mainClass
                if (-not [string]::IsNullOrWhiteSpace($qpMain)) { $mainClass = $qpMain }
            } catch { }
        }
        # Quilt-Client-Installer legt KEINE Libraries ab (Vanilla-Launcher-Modell):
        # Loader-Jars aus dem Quilt-Profil nach base\libraries laden.
        $qpDir = Get-ChildItem -Path $versionsDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "quilt-loader-*" } | Select-Object -First 1
        $qpFile = if ($qpDir) { Get-ChildItem -Path $qpDir.FullName -Filter "*.json" -File | Select-Object -First 1 } else { $null }
        if ($qpFile) {
            # Quilt-Profil-Format: {name, url} ohne downloads -> Pfad ableiten.
            $qpJson = Get-Content -LiteralPath $qpFile.FullName -Raw | ConvertFrom-Json
            foreach ($ql in @($qpJson.libraries)) {
                $qn = [string]$ql.name
                $parts = $qn -split ":"
                if ($parts.Count -ne 3) { continue }
                $qbase = "https://maven.quiltmc.org/repository/release"
                if ($ql.PSObject.Properties['url'] -and -not [string]::IsNullOrWhiteSpace($ql.url)) {
                    $qbase = ([string]$ql.url).TrimEnd("/")
                }
                $qrel = ($parts[0] -replace "\.", "/") + "/" + $parts[1] + "/" + $parts[2] + "/" + $parts[1] + "-" + $parts[2] + ".jar"
                if ($qrel -like "*natives*") { continue }
                $qtarget = Join-Path $BaseDir ("libraries\" + ($qrel -replace "/", "\"))
                if (-not (Test-Path -LiteralPath $qtarget)) {
                    New-Item -ItemType Directory -Force -Path (Split-Path $qtarget) | Out-Null
                    Invoke-Download -Url ($qbase + "/" + $qrel) -OutFile $qtarget
                }
            }
        }
    } else {
        # Forge: "<mc>-forge-<ver>", NeoForge: "neoforge-<ver>" (ohne mc-Prefix).
        $profPattern = if ($Loader -eq "neoforge") { "$Loader-*" } else { "$EnvMc-$Loader-*" }
        $profDir = Get-ChildItem -Path $versionsDir -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like $profPattern } | Select-Object -First 1
        $profFile = if ($profDir) { Get-ChildItem -Path $profDir.FullName -Filter "*.json" -File | Select-Object -First 1 } else { $null }
        if (-not $profFile) { throw "$Loader client profile JSON fehlt in $versionsDir" }
        $profileJson = Get-Content -LiteralPath $profFile.FullName -Raw | ConvertFrom-Json
        $mainClass = $profileJson.mainClass
    }

    return [ordered]@{
        mainClass = $mainClass; profileJson = $profileJson; clientJar = $clientJar
        nativesDir = $nativesDir; assetsDir = $assetsDir; assetIndexId = $assetIndexId
        versionJson = $versionJson
    }
}

function Invoke-MatrixClientTest {
    param($Combo, [string]$JarPath, [string]$JavaExe, [string]$FabricApiUrl, [string]$FabricApiVer,
        $Base, [string]$ClientDir, [string]$ServerDir, [string]$WorkDir, [string]$SpInitMarker, [int]$TimeoutSec,
        [string]$EnvMc = "")
    $sp = [ordered]@{ status = ""; log = ""; error = "" }
    $clientProc = $null
    try {
        New-Item -ItemType Directory -Force -Path $ClientDir | Out-Null
        $clientMods = Join-Path $ClientDir "mods"
        New-Item -ItemType Directory -Force -Path $clientMods | Out-Null
        Get-ChildItem -LiteralPath $clientMods -Filter "*.jar" -File -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $JarPath -Destination (Join-Path $clientMods (Split-Path $JarPath -Leaf)) -Force
        if ($FabricApiUrl -ne "") {
            $apiFile = Join-Path $clientMods ("fabric-api-" + $FabricApiVer + ".jar")
            if (-not (Test-Path -LiteralPath $apiFile)) {
                Invoke-Download -Url $FabricApiUrl -OutFile $apiFile
            }
        }

        $cp = New-Object System.Collections.Generic.List[string]
        $libsBase = Split-Path (Split-Path (Split-Path $Base.clientJar))
        if ($null -ne $Base.profileJson) {
            # forge/neoforge: Profil-Libs (exakt), ergaenzt um Vanilla-Libs fuer
            # Koordinaten, die das Profil nicht hat (z.B. log4j). Profil gewinnt.
            $seen = @{}
            foreach ($e in @(Get-ProfileLibraryPaths -ProfileJson $Base.profileJson -LibBase (Join-Path $libsBase "libraries"))) {
                if (-not $cp.Contains($e.Path)) { $cp.Add($e.Path) }
                if ($e.Key -ne "") { $seen[$e.Key] = $true }
            }
            foreach ($libPath in @(Get-MatrixLibraryClasspath -VersionJson $Base.versionJson -BaseDir $libsBase -ExcludeKeys $seen)) {
                if (-not $cp.Contains($libPath)) { $cp.Add($libPath) }
            }
        } else {
            foreach ($libPath in @(Get-MatrixLibraryClasspath -VersionJson $Base.versionJson -BaseDir $libsBase)) {
                $cp.Add($libPath)
            }
            $installedLibs = @(Get-ChildItem -Path (Join-Path $libsBase "libraries") -Recurse -Filter *.jar -File -ErrorAction SilentlyContinue)
            foreach ($lj in $installedLibs) {
                # Natives kommen ueber librarypath (flach extrahiert), nicht auf den Classpath.
                if ($lj.Name -like "*-natives-*") { continue }
                if (-not $cp.Contains($lj.FullName)) { $cp.Add($lj.FullName) }
            }
        }
        if (-not $cp.Contains($Base.clientJar)) { $cp.Add($Base.clientJar) }
        $classpath = $cp -join ";"

        $worldSrc = Join-Path $ServerDir "world"
        $worldDst = Join-Path $ClientDir "saves\testworld"
        if (-not (Test-Path -LiteralPath $worldSrc)) { throw "Keine MP-Welt zum Seeden: $worldSrc" }
        if (Test-Path -LiteralPath $worldDst) { Remove-Item -LiteralPath $worldDst -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $worldDst | Out-Null
        Copy-Item -Path (Join-Path $worldSrc "*") -Destination $worldDst -Recurse -Force

        if ($null -ne $Base.profileJson) {
            $subs = @{
                "version_name"      = $Base.profileJson.id
                "game_directory"    = $ClientDir
                "assets_root"       = $Base.assetsDir
                "assets_index_name" = $Base.assetIndexId
                "auth_player_name"  = "CI_Test"
                "auth_uuid"         = "00000000-0000-0000-0000-000000000001"
                "auth_access_token" = "0"
                "auth_session"      = "token:0:CI_Test"
                "auth_xuid"         = "0"
                "user_type"         = "msa"
                "user_properties"   = "{}"
                "clientid"          = "0"
                "resolution_width"  = "854"
                "resolution_height" = "480"
                "version_type"      = "release"
            }
            if ($Base.profileJson.arguments -and $Base.profileJson.arguments.game) {
                $rawArgs = $Base.profileJson.arguments.game
            } elseif ($Base.profileJson.minecraftArguments) {
                $rawArgs = $Base.profileJson.minecraftArguments -split "\s+"
            } else {
                $rawArgs = @()
            }
            $launchArgs = @()
            foreach ($a in $rawArgs) {
                $expanded = "$a"
                foreach ($k in $subs.Keys) { $expanded = $expanded.Replace("`${$k}", $subs[$k]) }
                $launchArgs += $expanded
            }
            if (-not $NoQuickPlay -and ($launchArgs -notcontains "--quickPlaySingleplayer")) {
                $launchArgs += @("--quickPlaySingleplayer", "testworld")
            }
            # Standard-Launcher-Args ergaenzen (Vanilla-Launcher sendet sie immer).
            $stdArgs = [ordered]@{
                "--username"   = "CI_Test"
                "--version"    = "$($Base.profileJson.id)"
                "--gameDir"    = $ClientDir
                "--assetsDir"  = $Base.assetsDir
                "--assetIndex" = $Base.assetIndexId
                "--uuid"       = "00000000-0000-0000-0000-000000000001"
                "--accessToken"= "0"
                "--userType"   = "msa"
                "--versionType"= "release"
            }
            foreach ($k in $stdArgs.Keys) {
                if ($launchArgs -notcontains $k) { $launchArgs += @($k, $stdArgs[$k]) }
            }
        } else {
            $launchArgs = @("--gameDir", $ClientDir, "--assetsDir", $Base.assetsDir, "--assetIndex", $Base.assetIndexId, "--username", "CI_Test")
            if (-not $NoQuickPlay) { $launchArgs += @("--quickPlaySingleplayer", "testworld") }
            if ($Combo.loader -eq "quilt") {
                $launchArgs += @("--version", $Combo.mc, "--uuid", "00000000-0000-0000-0000-000000000001",
                    "--accessToken", "0", "--userType", "msa", "--versionType", "release")
            }
        }

        $profileJvmArgs = @()
        if ($null -ne $Base.profileJson -and $Base.profileJson.arguments -and $Base.profileJson.arguments.jvm) {
            foreach ($a in $Base.profileJson.arguments.jvm) {
                $expanded = "$a"
                $expanded = $expanded.Replace('${library_directory}', (Join-Path $libsBase "libraries"))
                $profileJvmArgs += $expanded
            }
        }
        $clientArgs = @(
            "-Xms1024M", "-Xmx2048M"
        )
        if ($NoC2) {
            # HotSpot-C2-Absturz (26.3-Shaderpipeline, jvm.dll, ohne Mod-Frames):
            # problematische Methode von der Kompilierung ausschliessen.
            $clientArgs += @("-XX:CompileCommand=exclude,java/lang/invoke/InvokerBytecodeGenerator.loadMethod")
        }
        $clientArgs += @(
            "-Dorg.lwjgl.librarypath=$($Base.nativesDir)",
            "-Dorg.lwjgl.librarypath=$($Base.nativesDir)",
            "-Djava.library.path=$($Base.nativesDir)",
            "-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true"
        ) + $profileJvmArgs + @(
            "-cp", $classpath,
            $Base.mainClass
        ) + $launchArgs

        $stdoutFile = Join-Path $WorkDir "sp-client.out.log"
        $stderrFile = Join-Path $WorkDir "sp-client.err.log"
        $latestLog = Join-Path $ClientDir "logs\latest.log"
        $sp.log = $stdoutFile
        Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
        $script:logOffsets[$stdoutFile] = 0

        Set-Content -LiteralPath (Join-Path $WorkDir "sp-cmdline.txt") -Value ("cd /d `"$ClientDir`"`r`n`"$JavaExe`" " + (ConvertTo-ArgumentString -Items $clientArgs)) -Encoding UTF8
        $clientProc = Start-McProcess -JavaPath $JavaExe -StartArgs $clientArgs -WorkingDir $ClientDir -StdoutFile $stdoutFile -StderrFile $stderrFile -CaptureStdout

        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        $startTime = Get-Date
        $initOk = $false
        $doneOk = $false
        $exitInfo = ""
        while ((Get-Date) -lt $deadline) {
            $text = ""
            if (Test-Path -LiteralPath $stdoutFile) { $text += (Get-Content $stdoutFile -Raw -ErrorAction SilentlyContinue) }
            if (Test-Path -LiteralPath $latestLog) { $text += (Get-Content $latestLog -Raw -ErrorAction SilentlyContinue) }
            if (-not $initOk -and $text -match $SpInitMarker) { $initOk = $true }
            if (-not $doneOk -and $text -match "(?:Done \(|joined the game)") { $doneOk = $true }
            if ($initOk -and $doneOk) { break }
            if ($clientProc.HasExited) {
                $secs = [int]((Get-Date) - $startTime).TotalSeconds
                try { $exitInfo = " (Prozess-Exit Code $($clientProc.ExitCode) nach ${secs}s)" } catch { $exitInfo = " (Prozess beendet nach ${secs}s)" }
                break
            }
            Start-Sleep -Milliseconds 1000
        }
        if (-not $initOk) { throw "Client-Init fehlt nach ${TimeoutSec}s: $SpInitMarker$exitInfo" }
        if (-not $doneOk) { throw "Client-Welt kein Done/joined nach ${TimeoutSec}s$exitInfo" }
        $allText = ""
        if (Test-Path -LiteralPath $stdoutFile) { $allText += (Get-Content $stdoutFile -Raw -ErrorAction SilentlyContinue) }
        if (Test-Path -LiteralPath $latestLog) { $allText += (Get-Content $latestLog -Raw -ErrorAction SilentlyContinue) }
        $fatalLines = @($allText -split "`r?`n" | Where-Object { $_ -match "(?i)Caused by:|Fatal Error|Exception in thread" })
        $realErrors = @($fatalLines | Where-Object { $_ -notmatch "(?i)MinecraftClientHttpException|InvalidCredentialsException|authlib|Unsupported JNI|SocketTimeoutException|SocketException|UnknownHostException|ConnectException|RealmsServiceException" })
        if ($realErrors.Count -gt 0) { throw "Client-Log Fehler: " + ($realErrors -join "; ") }
        $sp.status = "passed"
    } catch {
        $sp.status = "failed"
        $sp.error = $_.Exception.Message
    } finally {
        if ($clientProc) { Stop-McProcess -Process $clientProc -Force }
    }
    return $sp
}

function Get-LibKey {
    param($Name)
    if ([string]::IsNullOrWhiteSpace($Name)) { return "" }
    $parts = $Name -split ":"
    if ($parts.Count -lt 2) { return "" }
    return ($parts[0] + ":" + $parts[1])
}

function Get-ProfileLibraryPaths {
    # Launcher-treue Classpath aus Installer-Profil (forge/neoforge):
    # genau EINE Version pro Artefakt, Regeln ausgewertet, keine Natives.
    # Gibt @{ Key; Path }-Eintraege zurueck (Key = group:artifact).
    param($ProfileJson, [string]$LibBase)
    $out = @()
    foreach ($lib in $ProfileJson.libraries) {
        $allow = $true
        if ($lib.PSObject.Properties['rules']) {
            $allow = $false
            foreach ($rule in $lib.rules) {
                $applies = $true
                if ($rule.PSObject.Properties['os'] -and $rule.os.PSObject.Properties['name']) {
                    $applies = ($rule.os.name -eq "windows")
                }
                if ($applies) { $allow = ($rule.action -eq "allow") }
            }
        }
        if (-not $allow) { continue }
        $rel = ""
        $dlPath = ""
        if ($lib.PSObject.Properties['downloads'] -and $null -ne $lib.downloads `
                -and $lib.downloads.PSObject.Properties['artifact'] -and $null -ne $lib.downloads.artifact `
                -and $lib.downloads.artifact.PSObject.Properties['path']) {
            $dlPath = [string]$lib.downloads.artifact.path
        }
        if ($dlPath -ne "") {
            $rel = $dlPath -replace "/", "\"
        } elseif ($lib.PSObject.Properties['name'] -and $lib.name) {
            $parts = $lib.name -split ":"
            if ($parts.Count -lt 3) { continue }
            if ($parts.Count -gt 3) { continue }  # classifier (z.B. natives) -> skip
            $rel = ($parts[0] -replace "\.", "\") + "\" + $parts[1] + "\" + $parts[2] + "\" + $parts[1] + "-" + $parts[2] + ".jar"
        }
        if ($rel -eq "" -or $rel -like "*natives*") { continue }
        $target = Join-Path $LibBase $rel
        if (Test-Path -LiteralPath $target) {
            $out += [pscustomobject]@{ Key = (Get-LibKey -Name ([string]$lib.name)); Path = $target }
        }
    }
    return $out
}

function Get-ForgeServerArgs {
    param([string]$Dir)
    $runBat = Join-Path $Dir "run.bat"
    if (-not (Test-Path -LiteralPath $runBat)) { throw "No run.bat after Forge/NeoForge install: $Dir" }
    $batContent = Get-Content $runBat -Raw
    $m = [regex]::Match($batContent, '@(libraries[^\s"%]+win_args\.txt)')
    if (-not $m.Success) { throw "Could not parse win_args.txt from run.bat" }
    $args = @()
    $userArgs = Join-Path $Dir "user_jvm_args.txt"
    if (Test-Path -LiteralPath $userArgs) { $args += "@$userArgs" }
    $args += "@$(Join-Path $Dir ($m.Groups[1].Value))"
    return $args
}

function Get-StableFabricLoaderVersion {
    param([string]$Mc)
    $j = Invoke-RestMethod -Uri "https://meta.fabricmc.net/v2/versions/loader/$Mc" -TimeoutSec 30
    foreach ($e in $j) {
        if ($e.loader.stable -eq $true) { return $e.loader.version }
    }
    if ($j.Count -gt 0) { return $j[0].loader.version }
    throw "No fabric loader version for MC $Mc"
}

function Get-LatestFabricApiForMc {
    param([string]$Mc)
    $q = [uri]::EscapeDataString("[`"$Mc`"]")
    $l = [uri]::EscapeDataString('["fabric"]')
    $j = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$q&loaders=$l" -TimeoutSec 30
    if ($j.Count -eq 0) { throw "No fabric-api version for MC $Mc on Modrinth" }
    return $j[0].version_number
}

function Get-LoaderVersionConfig {
    param([string]$Loader, [string]$Mc, $Map)
    if ($null -eq $Map) { return $null }
    $loaderObj = $Map.$Loader
    if ($null -eq $loaderObj) { return $null }
    $prop = $loaderObj.PSObject.Properties | Where-Object { $_.Name -eq $Mc } | Select-Object -First 1
    if ($null -eq $prop) { return $null }
    return $prop.Value
}

function Resolve-ClientJavaExe {
    # 26.x-Clients: Azul Zulu 25 (Modrinth-Runtime) bevorzugen - Oracle HotSpot
    # stuerzt dort sporadisch ab (AV in jvm.dll, ohne Mod-Frames). Explizites
    # -ClientJavaHome hat Vorrang, sonst Oracle/Default.
    param([string]$DefaultExe, [string]$EnvMc)
    if (-not [string]::IsNullOrWhiteSpace($ClientJavaHome)) {
        $cand = Join-Path $ClientJavaHome "bin\java.exe"
        if (Test-Path -LiteralPath $cand) { return $cand }
    }
    if ($EnvMc -match "^26\.") {
        $zulus = @(Get-ChildItem -Path "C:\Users\Steven\AppData\Roaming\ModrinthApp\meta\java_versions" -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "zulu25*" } | Sort-Object Name -Descending)
        foreach ($z in $zulus) {
            $exe = Join-Path $z.FullName "bin\java.exe"
            if (Test-Path -LiteralPath $exe) { return $exe }
        }
    }
    return $DefaultExe
}

function Resolve-JavaExe {
    param([string]$Which)
    $cands = @()
    if ($Which -eq "25") {
        $cands = @("C:\Program Files\Java\jdk-25.0.3", "C:\Program Files\Java\jdk-25", $env:JAVA_HOME)
    } else {
        $cands = @("C:\Program Files\Java\jdk-21", $env:JAVA_HOME)
    }
    foreach ($c in $cands) {
        if ([string]::IsNullOrWhiteSpace($c)) { continue }
        $exe = Join-Path $c "bin\java.exe"
        if (Test-Path -LiteralPath $exe) { return $exe }
    }
    throw "Java $Which nicht gefunden (jdk-21 / jdk-25.0.3 erwartet)."
}

function Find-ModJar {
    param([string]$Dir, [string]$Pattern)
    if (-not (Test-Path -LiteralPath $Dir)) { return $null }
    $hits = @(Get-ChildItem -LiteralPath $Dir -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*sources*" } |
        Sort-Object LastWriteTime -Descending)
    if ($hits.Count -eq 0) { return $null }
    return $hits[0].FullName
}

# ---------------------------------------------------------------------------
# Matrix-Definition
# ---------------------------------------------------------------------------

$java25 = Resolve-JavaExe -Which "25"
$java21 = Resolve-JavaExe -Which "21"

$combos = @()

# StatusMod: fabric/forge/neoforge/quilt x 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3
foreach ($mc in @("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3")) {
    foreach ($loader in @("fabric", "forge", "neoforge", "quilt")) {
        if ($loader -eq "fabric") {
            $jarDir = Join-Path $StatusModDir "dist\multiversion\fabric\$mc"
        } else {
            $jarDir = Join-Path $StatusModDir "dist\multiversion\$loader"
        }
        $combos += [ordered]@{
            mod = "statusmod"; loader = $loader; mc = $mc
            jarDir = $jarDir; jarPattern = "Statusmod-*-$loader-$mc.jar"
            initMarker = "\[StatusMod\] Initializing"; javaExe = $java25
        }
    }
}

# TimerWave: fabric x 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3
$twVers = @(
    @{ mc = "1.21.11"; dir = "build\libs"; pat = "timerwave-*.jar"; java = $java21 },
    @{ mc = "26.1"; dir = "Loader\fabric26.1\build\libs"; pat = "timerwave-fabric26.1-*.jar"; java = $java25 },
    @{ mc = "26.1.1"; dir = "Loader\fabric26.1.1\build\libs"; pat = "timerwave-fabric26.1.1-*.jar"; java = $java25 },
    @{ mc = "26.1.2"; dir = "Loader\fabric26.1.2\build\libs"; pat = "timerwave-fabric26.1.2-*.jar"; java = $java25 },
    @{ mc = "26.2"; dir = "Loader\fabric26.2\build\libs"; pat = "timerwave-fabric26.2-*.jar"; java = $java25 },
    @{ mc = "26.3"; dir = "Loader\fabric26.3\build\libs"; pat = "timerwave-fabric26.3-*.jar"; java = $java25 }
)
foreach ($t in $twVers) {
    $combos += [ordered]@{
        mod = "timerwave"; loader = "fabric"; mc = $t.mc
        jarDir = (Join-Path $TimerWaveDir $t.dir); jarPattern = $t.pat
        initMarker = "TimerWave initialized\."; javaExe = $t.java
    }
}

# MapSwitch: fabric x 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3
$msVers = @(
    @{ mc = "1.21.11"; dir = "build\libs"; pat = "mapswitch-*.jar"; java = $java21 },
    @{ mc = "26.1"; dir = "Loader\fabric26.1\build\libs"; pat = "mapswitch-fabric26.1-*.jar"; java = $java25 },
    @{ mc = "26.1.1"; dir = "Loader\fabric26.1.1\build\libs"; pat = "mapswitch-fabric26.1.1-*.jar"; java = $java25 },
    @{ mc = "26.1.2"; dir = "Loader\fabric26.1.2\build\libs"; pat = "mapswitch-fabric26.1.2-*.jar"; java = $java25 },
    @{ mc = "26.2"; dir = "Loader\fabric26.2\build\libs"; pat = "mapswitch-fabric26.2-*.jar"; java = $java25 },
    @{ mc = "26.3"; dir = "Loader\fabric26.3\build\libs"; pat = "mapswitch-fabric26.3-*.jar"; java = $java25 }
)
foreach ($t in $msVers) {
    $combos += [ordered]@{
        mod = "mapswitch"; loader = "fabric"; mc = $t.mc
        jarDir = (Join-Path $MapSwitchDir $t.dir); jarPattern = $t.pat
        initMarker = "Server entrypoint loaded"; javaExe = $t.java
    }
}

# Filter aus Params anwenden
$combos = @($combos | Where-Object { ($Mods -contains $_.mod) -and ($Loaders -contains $_.loader) -and ($McVersions -contains $_.mc) })

function Build-MissingJar {
    param($Combo)
    if ($Combo.mod -eq "timerwave" -and $Combo.mc -eq "1.21.11") {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
        & "$TimerWaveDir\gradlew.bat" -p $TimerWaveDir clean build --no-daemon | Out-Null
        return $?
    }
    if ($Combo.mod -eq "timerwave" -and ($Combo.mc -eq "26.2" -or $Combo.mc -eq "26.3")) {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.3"
        & "$TimerWaveDir\Loader\fabric$($Combo.mc)\gradlew.bat" -p "$TimerWaveDir\Loader\fabric$($Combo.mc)" clean build --no-daemon | Out-Null
        return $?
    }
    if ($Combo.mod -eq "mapswitch" -and $Combo.mc -eq "1.21.11") {
        $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
        & "$MapSwitchDir\gradlew.bat" -p $MapSwitchDir clean build --no-daemon | Out-Null
        return $?
    }
    return $false
}

# ---------------------------------------------------------------------------
# DryRun: Matrix + JAR-Aufloesung zeigen
# ---------------------------------------------------------------------------

$results = @()
$port = $BasePort
foreach ($c in $combos) {
    $jar = Find-ModJar -Dir $c.jarDir -Pattern $c.jarPattern
    $r = [ordered]@{
        mod = $c.mod; loader = $c.loader; mc = $c.mc
        envMc = $c.mc; jar = $jar; status = ""; error = ""; log = ""
        port = $port; mpStatus = ""; spStatus = "skipped"; spLog = ""
    }
    $port++
    # Quilt-JARs sind pro MC-Version eigene Builds (Mojang-Mappings, nicht
    # byte-identisch mit 1.21.11) und laufen in echter 26.x-Umgebung.
    # (Alte Regel "quilt-26.x auf 1.21.11 testen" ist widerlegt: Crash mit
    #  NoClassDefFoundError CommandBuildContext am 2026-10-03.)
    if ($c.Contains("blockedReason") -and -not [string]::IsNullOrWhiteSpace($c.blockedReason)) {
        $r.status = "blocked"; $r.error = $c.blockedReason
    } elseif ([string]::IsNullOrWhiteSpace($jar)) {
        $r.status = "missing-jar"
        $r.error = "Keine JAR in $($c.jarDir) ($($c.jarPattern))"
    } else {
        $r.status = "ready"
    }
    $results += $r
}

Write-Host ""
Write-Host "WICHTIG: Minecraft-Fenster waehrend des Laufs bitte nur MINIMIEREN, nie schliessen."
Write-Host "Ein geschlossenes Fenster beendet den Test mit FAIL (orderly exit, kein Mod-Fehler)."
Write-Host ""
Write-Host "=== Mod x Loader x Version Matrix ($($results.Count) Kombinationen) ==="
foreach ($r in $results) {
    $jarShort = if ($r.jar) { Split-Path $r.jar -Leaf } else { "-" }
    Write-Host ("{0,-10} {1,-9} {2,-8} env={3,-8} port={4} [{5}] {6} {7}" -f $r.mod, $r.loader, $r.mc, $r.envMc, $r.port, $r.status, $jarShort, $r.error)
}

$reportJson = Join-Path $WorkRoot "matrix-report.json"
$reportMd = Join-Path $WorkRoot "matrix-report.md"

if ($DryRun) {
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportJson -Encoding UTF8
    Write-Host ""
    Write-Host "DryRun: Bericht unter $reportJson"
    exit 0
}

# ---------------------------------------------------------------------------
# Echte Starts
# ---------------------------------------------------------------------------

$passCount = 0
$failCount = 0
$skipCount = 0
$idx = 0

foreach ($r in $results) {
    $idx++
    if ($idx -lt $StartIndex) {
        $r.status = "skipped"
        $r.error = "StartIndex"
        $skipCount++
        Write-Host "[$idx/$($results.Count)] SKIP $($r.mod) $($r.loader) $($r.mc) (StartIndex)"
        continue
    }
    if ($r.status -ne "ready") {
        if ($BuildMissing -and $r.status -eq "missing-jar") {
            $combo = @($combos | Where-Object { $_.mod -eq $r.mod -and $_.loader -eq $r.loader -and $_.mc -eq $r.mc })[0]
            Write-Host ""
            Write-Host "[$idx/$($results.Count)] Baue fehlende JAR: $($r.mod) $($r.loader) $($r.mc) ..."
            try {
                if (Build-MissingJar -Combo $combo) {
                    $jar = Find-ModJar -Dir $combo.jarDir -Pattern $combo.jarPattern
                    if ($jar) { $r.jar = $jar; $r.status = "ready"; $r.error = "" }
                }
            } catch {
                $r.error = "Build fehlgeschlagen: $($_.Exception.Message)"
            }
        }
        if ($r.status -ne "ready") {
            $skipCount++
            Write-Host "[$idx/$($results.Count)] SKIP $($r.mod) $($r.loader) $($r.mc): $($r.error)"
            continue
        }
    }

    $tag = "$($r.mod)-$($r.loader)-$($r.mc)"
    $workDir = Join-Path $WorkRoot $tag
    $serverDir = Join-Path $workDir "server"
    $dlDir = Join-Path $workDir "dl"
    New-Item -ItemType Directory -Force -Path $serverDir | Out-Null
    New-Item -ItemType Directory -Force -Path $dlDir | Out-Null
    $stdoutFile = Join-Path $workDir "server.out.log"
    $stderrFile = Join-Path $workDir "server.err.log"
    $r.log = $stdoutFile
    Remove-Item -LiteralPath $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
    $script:logOffsets[$stdoutFile] = 0

    Write-Host ""
    Write-Host "[$idx/$($results.Count)] Starte $tag (Port $($r.port)) ..."
    $proc = $null
    try {
        $javaExe = if ($r.mod -eq "statusmod") { $java25 } else {
            $combo = @($combos | Where-Object { $_.mod -eq $r.mod -and $_.loader -eq $r.loader -and $_.mc -eq $r.mc })[0]
            $combo.javaExe
        }

        # Loader-Version + Installer aufloesen
        $loaderVer = ""
        $installerUrl = ""
        $fabricApiUrl = ""
        $fabricApiVer = ""
        switch ($r.loader) {
            "fabric" {
                $loaderVer = Get-StableFabricLoaderVersion -Mc $r.envMc
                $installerUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar"
                $fabricApiVer = Get-LatestFabricApiForMc -Mc $r.envMc
                $fabricApiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$fabricApiVer/fabric-api-$fabricApiVer.jar"
            }
            "quilt" {
                $cfg = Get-LoaderVersionConfig -Loader "quilt" -Mc $r.envMc -Map $loaderVersionMap
                $loaderVer = if ($cfg) { $cfg.quilt_loader_version } else { "0.30.0" }
                $installerUrl = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/0.15.0/quilt-installer-0.15.0.jar"
                $fabricApiVer = Get-LatestFabricApiForMc -Mc $r.envMc
                $fabricApiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$fabricApiVer/fabric-api-$fabricApiVer.jar"
            }
            "forge" {
                $cfg = Get-LoaderVersionConfig -Loader "forge" -Mc $r.mc -Map $loaderVersionMap
                if ($null -eq $cfg -or [string]::IsNullOrWhiteSpace($cfg.forge_version)) { throw "Keine forge_version fuer MC $($r.mc) in loader-versions.json" }
                $installerUrl = "https://maven.minecraftforge.net/net/minecraftforge/forge/$($r.mc)-$($cfg.forge_version)/forge-$($r.mc)-$($cfg.forge_version)-installer.jar"
            }
            "neoforge" {
                $cfg = Get-LoaderVersionConfig -Loader "neoforge" -Mc $r.mc -Map $loaderVersionMap
                if ($null -eq $cfg -or [string]::IsNullOrWhiteSpace($cfg.neoforge_version)) { throw "Keine neoforge_version fuer MC $($r.mc) in loader-versions.json" }
                # Builds nutzen die 1.21.11-NeoForge (Mojang-Mappings sind versionsstabil),
                # der Lauf testet aber auf echtem Ziel-MC, sobald runtime_neoforge_version gesetzt ist.
                $nfVer = [string]$cfg.neoforge_version
                if ($cfg.PSObject.Properties["runtime_neoforge_version"] -and -not [string]::IsNullOrWhiteSpace($cfg.runtime_neoforge_version)) {
                    $nfVer = [string]$cfg.runtime_neoforge_version
                }
                $installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$nfVer/neoforge-$nfVer-installer.jar"
            }
        }

        $installerJar = Join-Path $dlDir "installer.jar"
        if (-not (Test-Path -LiteralPath $installerJar)) {
            Invoke-Download -Url $installerUrl -OutFile $installerJar
        }

        switch ($r.loader) {
            "fabric" {
                $p = Start-Process -FilePath $javaExe -ArgumentList @("-jar", $installerJar, "server", "-dir", $serverDir, "-mcversion", $r.envMc, "-loader", $loaderVer, "-downloadMinecraft") -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "fabric-installer exited $($p.ExitCode)" }
            }
            "quilt" {
                $p = Start-Process -FilePath $javaExe -ArgumentList @("-jar", $installerJar, "install", "server", $r.envMc, $loaderVer, "--install-dir=$serverDir", "--download-server", "--create-scripts") -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "quilt-installer exited $($p.ExitCode)" }
            }
            default {
                $p = Start-Process -FilePath $javaExe -ArgumentList @("-jar", $installerJar, "--installServer") -WorkingDirectory $serverDir -Wait -PassThru -NoNewWindow
                if ($p.ExitCode -ne 0) { throw "$($r.loader) installer exited $($p.ExitCode)" }
            }
        }

        $modsDir = Join-Path $serverDir "mods"
        New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
        # Alte JARs aus frueheren Laeufen entfernen, sonst sieht der Loader
        # doppelte Mods (z.B. zwei fabric-api) und bricht ab.
        Get-ChildItem -LiteralPath $modsDir -Filter "*.jar" -File -ErrorAction SilentlyContinue |
            Remove-Item -Force -ErrorAction SilentlyContinue
        Copy-Item -LiteralPath $r.jar -Destination (Join-Path $modsDir (Split-Path $r.jar -Leaf)) -Force
        if ($fabricApiUrl -ne "") {
            $apiFile = Join-Path $modsDir ("fabric-api-" + $fabricApiVer + ".jar")
            if (-not (Test-Path -LiteralPath $apiFile)) {
                Invoke-Download -Url $fabricApiUrl -OutFile $apiFile
            }
        }

        Set-Content -LiteralPath (Join-Path $serverDir "eula.txt") -Value "eula=true" -Encoding ASCII
        $props = @("online-mode=false", "server-port=$($r.port)", "level-type=minecraft:flat",
            "spawn-protection=0", "view-distance=6", "simulation-distance=6", "max-players=1")
        Set-Content -LiteralPath (Join-Path $serverDir "server.properties") -Value ($props -join "`n") -Encoding ASCII
        Write-Host "  Install fertig, starte Server (warte max. ${TimeoutInitSec}s auf Init-Marker) ..."

        if ($r.loader -eq "fabric") {
            $launcher = Get-ChildItem -Path $serverDir -Filter "fabric-server-mc*.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if (-not $launcher) { $launcher = Get-ChildItem -Path $serverDir -Filter "fabric-server-launch.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1 }
            if (-not $launcher) { throw "Kein Fabric-Launcher nach Install in $serverDir" }
            $startArgs = @("-Xms1024M", "-Xmx2048M", "-jar", $launcher.Name, "nogui")
        } elseif ($r.loader -eq "quilt") {
            $launcher = Get-ChildItem -Path $serverDir -Filter "quilt-server-launch.jar" -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if (-not $launcher) { throw "Kein Quilt-Launcher nach Install in $serverDir" }
            $startArgs = @("-Xms1024M", "-Xmx2048M", "-jar", $launcher.Name, "nogui")
        } else {
            $startArgs = @(Get-ForgeServerArgs -Dir $serverDir) + @("nogui")
        }

        $proc = Start-McProcess -JavaPath $javaExe -StartArgs $startArgs -WorkingDir $serverDir -StdoutFile $stdoutFile -StderrFile $stderrFile

        # Init-Marker MUSS vor "Done" geprueft werden (Offset-Logik).
        $combo = @($combos | Where-Object { $_.mod -eq $r.mod -and $_.loader -eq $r.loader -and $_.mc -eq $r.mc })[0]
        $initOk = Wait-LogContains -LogFile $stdoutFile -Pattern $combo.initMarker -TimeoutSec $TimeoutInitSec
        if (-not $initOk) { throw "Init-Marker fehlt nach ${TimeoutInitSec}s: $($combo.initMarker)" }
        Write-Host "  Init gefunden, warte max. ${TimeoutDoneSec}s auf Done ..."
        $doneOk = Wait-LogContains -LogFile $stdoutFile -Pattern "Done \(" -TimeoutSec $TimeoutDoneSec
        if (-not $doneOk) { throw "Server erreichte 'Done' nicht in ${TimeoutDoneSec}s" }

        $r.mpStatus = "passed"
        Write-Host "  PASS $tag (Server)"
        if ($proc -and -not $SkipClient) {
            # Server stoppen (Welt speichern), damit der Client die Welt kopieren kann.
            Stop-McProcess -Process $proc
            $proc = $null
        }
        if ($SkipClient) {
            $r.spStatus = "skipped"
        } else {
            Write-Host "  Starte Client (warte max. ${SpTimeoutSec}s auf Init+Join) ..."
            $spMarker = if ($r.mod -eq "mapswitch") { "Loaded maps:" } else { $combo.initMarker }
            $clientBaseDir = Join-Path $sharedDir ("client-" + $r.loader + "-" + ($r.envMc -replace '\.', '_'))
            $clientBase = Install-ClientBase -Loader $r.loader -EnvMc $r.envMc -LoaderVer $loaderVer -InstallerUrl $installerUrl -JavaExe $javaExe -BaseDir $clientBaseDir
            $clientJavaExe = Resolve-ClientJavaExe -DefaultExe $javaExe -EnvMc $r.envMc
            Write-Host "  Client-Java: $clientJavaExe"
            $spRes = Invoke-MatrixClientTest -Combo $combo -JarPath $r.jar -JavaExe $clientJavaExe -FabricApiUrl $fabricApiUrl -FabricApiVer $fabricApiVer -Base $clientBase -ClientDir (Join-Path $workDir "client") -ServerDir $serverDir -WorkDir $workDir -SpInitMarker $spMarker -TimeoutSec $SpTimeoutSec -EnvMc $r.envMc
            $r.spStatus = $spRes.status
            $r.spLog = $spRes.log
            if ($spRes.status -ne "passed") { throw "Client: $($spRes.error)" }
            Write-Host "  PASS $tag (Client)"
        }
        $r.status = "passed"
        $passCount++
    } catch {
        $r.status = "failed"
        $r.error = $_.Exception.Message
        if ([string]::IsNullOrWhiteSpace($r.mpStatus)) { $r.mpStatus = "failed" }
        $failCount++
        Write-Warning "  FAIL $tag : $($_.Exception.Message)"
    } finally {
        if ($proc) { Stop-McProcess -Process $proc }
    }
    # Client-Game-Logs sichern, dann schwere Dirs loeschen (Kombi-Root-Logs bleiben).
    $cliDir = Join-Path $workDir "client"
    foreach ($lf in @(Get-ChildItem -LiteralPath $cliDir -Filter "hs_err_pid*.log" -File -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })) {
        Copy-Item -LiteralPath (Join-Path $cliDir $lf) -Destination (Join-Path $workDir $lf) -Force -ErrorAction SilentlyContinue
    }
    foreach ($lf in @("logs\latest.log", "logs\debug.log")) {
        $src = Join-Path $cliDir $lf
        if (Test-Path -LiteralPath $src) {
            Copy-Item -LiteralPath $src -Destination (Join-Path $workDir ("sp-" + (Split-Path $lf -Leaf))) -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -LiteralPath $serverDir, $dlDir, $cliDir -Recurse -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------------------
# Bericht
# ---------------------------------------------------------------------------

$spPass = @($results | Where-Object { $_.spStatus -eq "passed" }).Count
$spFail = @($results | Where-Object { $_.spStatus -eq "failed" }).Count
$summary = [ordered]@{
    timestamp = (Get-Date).ToString("o")
    total = $results.Count
    passed = $passCount
    failed = $failCount
    skipped = $skipCount
    spPassed = $spPass
    spFailed = $spFail
    results = $results
}
$summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportJson -Encoding UTF8

$mpPass = @($results | Where-Object { $_.mpStatus -eq "passed" }).Count
$md = @()
$md += "# Mod x Loader x Version - Start-Matrix (Server + Client)"
$md += ""
$md += "Zeitpunkt: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
$md += ""
$md += "Server: **$mpPass/$($results.Count)** ($failCount Kombis mit Fehlern, $skipCount uebersprungen); Client: **$spPass** passed, $spFail failed"
$md += ""
$md += "| Mod | Loader | MC | Server | Client | JAR / Fehler |"
$md += "|-----|--------|----|--------|--------|--------------|"
foreach ($r in $results) {
    $detail = if ($r.status -eq "passed") { Split-Path $r.jar -Leaf } else { $r.error }
    $md += "| $($r.mod) | $($r.loader) | $($r.mc) | $($r.mpStatus) | $($r.spStatus) | $detail |"
}
$md += ""
$md += "Details/Logs: $WorkRoot/<mod>-<loader>-<mc>/server.out.log (Server), sp-client.out.log (Client)"
$md += ""
$md += "Status: passed = Mod-Init + Done (Server) bzw. Init + Join (Client); missing-jar = JAR fehlt (-BuildMissing baut TimerWave/MapSwitch nach); blocked = bekannt nicht baubar."
$md -join "`r`n" | Set-Content -LiteralPath $reportMd -Encoding UTF8

Write-Host ""
Write-Host "=== Ergebnis Server: $mpPass/$($results.Count) passed ($failCount Kombis mit Fehlern, $skipCount skipped); Client: $spPass passed, $spFail failed ==="
Write-Host "JSON: $reportJson"
Write-Host "Markdown: $reportMd"

if (-not $DryRun -and -not $NoCleanup) {
    Write-Host ""
    Write-Host "Raeume auf (Berichte bleiben, Failed-Logs bleiben) ..."
    foreach ($r in $results) {
        if ($r.status -eq "failed") {
            $keepDir = Join-Path $WorkRoot ("failed-" + $r.mod + "-" + $r.loader + "-" + $r.mc)
            New-Item -ItemType Directory -Force -Path $keepDir | Out-Null
            foreach ($lf in @($r.log, $r.spLog)) {
                if (-not [string]::IsNullOrWhiteSpace($lf) -and (Test-Path -LiteralPath $lf)) {
                    Copy-Item -LiteralPath $lf -Destination (Join-Path $keepDir (Split-Path $lf -Leaf)) -Force -ErrorAction SilentlyContinue
                }
            }
            $comboDir = ""
            try { $comboDir = Split-Path $r.log -ErrorAction Stop } catch { $comboDir = "" }
            if (-not [string]::IsNullOrWhiteSpace($comboDir) -and (Test-Path -LiteralPath $comboDir)) {
                foreach ($pat in @("sp-latest.log", "sp-debug.log", "sp-cmdline.txt", "sp-client.err.log", "server.err.log", "hs_err_pid*.log")) {
            foreach ($match in @(Get-ChildItem -LiteralPath $comboDir -Filter $pat -File -Recurse -ErrorAction SilentlyContinue)) {
                    $destName = $match.Name
                    if (Test-Path -LiteralPath (Join-Path $keepDir $destName)) { $destName = $match.Directory.Name + "-" + $match.Name }
                    Copy-Item -LiteralPath $match.FullName -Destination (Join-Path $keepDir $destName) -Force -ErrorAction SilentlyContinue
                }
                }
            }
        }
    }
    foreach ($d in @(Get-ChildItem -LiteralPath $WorkRoot -Directory -ErrorAction SilentlyContinue)) {
        if ($d.Name -like "failed-*") { continue }
        if ($d.Name -eq "shared") {
            Write-Host "  loesche shared/ ..."
        }
        Remove-Item -LiteralPath $d.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Aufgeraeumt. Behalten: matrix-report.json/.md + failed-*-Logs."
}

if ($failCount -gt 0) { exit 1 }
