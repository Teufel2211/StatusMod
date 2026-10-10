# Fleet multiversion build: StatusMod Fleet (fleet config sync) for
# fabric/forge/neoforge/quilt x 1.21.11/26.1/26.1.1/26.1.2/26.2/26.3.
# 1.21.11 via root Gradle modules (Loom remap); 26.x via plain javac
# (unobfuscated Mojang names); Forge 26.x via Loader/fleet-forge26.1
# (ForgeGradle, one build copied to all 26.x); Quilt via repackage.
param(
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt"),
    [string[]]$McVersions = @("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"),
    [string]$FleetVersion = "",
    [string]$FabricApiVersion26 = "",
    [string]$LoaderVersion = "0.19.3",
    [string]$LoaderDepVersion = "0.16.14",
    [string]$JavaHome = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if ([string]::IsNullOrWhiteSpace($FleetVersion)) { $FleetVersion = (Get-Content (Join-Path $root "version.txt") -Raw).Trim() }
$distRoot = Join-Path $root "dist\fleet"

$FapiMap = @{
    "26.1"   = "0.145.1+26.1"
    "26.1.1" = "0.145.4+26.1.1"
    "26.1.2" = "0.155.3+26.1.2"
    "26.2"   = "0.161.0+26.2"
    "26.3"   = "0.162.0+26.3"
}
$NeoMap = @{
    "26.1"   = "26.1.0.19-beta"
    "26.1.1" = "26.1.1.15-beta"
    "26.1.2" = "26.1.2.114"
    "26.2"   = "26.2.0.88"
    "26.3"   = "26.3.0.48-beta"
}
$QuiltLoaderVersion = "0.30.0"

$gradleCache = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"
$libDir = Join-Path $root ".plain-libs"
function Test-FileExists([string]$p) { return [System.IO.File]::Exists($p) }
function Test-DirExists([string]$p) { return [System.IO.Directory]::Exists($p) }

function Get-CachedJar([string]$subPath, [string]$version) {
    $base = Join-Path $gradleCache $subPath
    $dir = $null
    if (-not [string]::IsNullOrWhiteSpace($version)) {
        $d = Join-Path $base $version
        if (Test-DirExists $d) { $dir = $d }
    }
    if (-not $dir) {
        $dir = Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
        if ($dir) { $dir = $dir.FullName } else { return $null }
    }
    $j = Get-ChildItem -LiteralPath $dir -Recurse -File -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } | Select-Object -First 1
    if ($j) { return $j.FullName } else { return $null }
}

function Get-MavenJar([string]$repo, [string]$groupPath, [string]$artifact, [string]$version) {
    $local = Get-CachedJar $groupPath $version
    if ($local) { return $local }
    $file = "$artifact-$version.jar"
    $url = "$repo/$($groupPath.Replace('\','/'))/$artifact/$version/$file"
    $dst = Join-Path $libDir "$artifact-$version.jar"
    if (-not (Test-FileExists $dst)) {
        Write-Host "Downloading $artifact $version ..."
        Invoke-WebRequest $url -OutFile $dst -TimeoutSec 300
    }
    return $dst
}

function Get-McClasspath([string]$mc) {
    $cp = @()
    $mcLibDir = Join-Path $libDir "mc-$mc"
    if (Test-DirExists $mcLibDir) {
        $cp += @(Get-ChildItem -LiteralPath $mcLibDir -File -Filter "*.jar" | ForEach-Object { $_.FullName })
    }
    $mcJar = Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\$mc\minecraft-merged-deobf-$mc.jar"
    if (-not (Test-FileExists $mcJar)) { throw "No deobf MC jar for $mc (run a StatusMod plain build first)" }
    $cp += $mcJar
    $cp += (Get-CachedJar "com.google.code.gson\gson" "")
    $cp += (Get-CachedJar "com.mojang\authlib" "")
    $cp += (Get-CachedJar "com.mojang\brigadier" "")
    $jb = Get-CachedJar "org.jetbrains\annotations" ""
    if ($jb) { $cp += $jb }
    $lenientJar = Join-Path $libDir "jspecify-lenient.jar"
    if (Test-FileExists $lenientJar) { $cp = @($lenientJar) + $cp }
    return ($cp | Where-Object { $_ } | Select-Object -Unique)
}

function Invoke-PlainBuild([string]$loader, [string]$mc, [string[]]$extraCp) {
    $javac = Join-Path $JavaHome "bin\javac.exe"
    $jarTool = Join-Path $JavaHome "bin\jar.exe"
    $cp = @(Get-McClasspath $mc) + $extraCp
    $work = Join-Path ([System.IO.Path]::GetTempPath()) "fleet-plain-$loader-$mc"
    if (Test-Path $work) { Remove-Item $work -Recurse -Force }
    $cls = Join-Path $work "classes"; New-Item -ItemType Directory -Force -Path $cls | Out-Null
    $srcs = @()
    foreach ($s in @("fleet\fleet-common\src\main\java", "fleet\fleet-$loader\src\main\java")) {
        $d = Join-Path $root $s
        if (Test-Path $d) { $srcs += @(Get-ChildItem $d -Recurse -File -Filter "*.java" | ForEach-Object { $_.FullName }) }
    }
    if ($srcs.Count -eq 0) { throw "No fleet sources for $loader" }
    $srcList = Join-Path $work "sources.txt"
    Set-Content -LiteralPath $srcList -Value $srcs
    Write-Host "Compiling $($srcs.Count) fleet sources ($loader $mc) ..."
    & $javac -encoding UTF-8 -nowarn -cp ($cp -join ";") -d $cls "@$srcList"
    if ($LASTEXITCODE -ne 0) { throw "javac failed for fleet $loader $mc" }
    # Resources: fleet-common (none expected) + loader metadata (expanded).
    $resBase = Join-Path $root "fleet\fleet-$loader\src\main\resources"
    if ($loader -eq "fabric") {
        $fmj = (Get-Content (Join-Path $resBase "fabric.mod.json") -Raw) `
            -replace '\$\{version\}', $FleetVersion `
            -replace '\$\{loader_version\}', $LoaderDepVersion
        [System.IO.File]::WriteAllText((Join-Path $cls "fabric.mod.json"), $fmj)
    } elseif ($loader -eq "neoforge") {
        $dst = Join-Path $cls "META-INF"; New-Item -ItemType Directory -Force -Path $dst | Out-Null
        $toml = (Get-Content (Join-Path $resBase "META-INF\neoforge.mods.toml") -Raw) `
            -replace '\$\{version\}', $FleetVersion
        [System.IO.File]::WriteAllText((Join-Path $dst "neoforge.mods.toml"), $toml)
    }
    # Mapping gate.
    $latin1 = [System.Text.Encoding]::GetEncoding("ISO-8859-1")
    $bad = $false
    Get-ChildItem -LiteralPath $cls -Recurse -File -Filter "*.class" | ForEach-Object {
        $t = $latin1.GetString([System.IO.File]::ReadAllBytes($_.FullName))
        if ($t -match "net/minecraft/class_") { Write-Host "INTERMEDIARY: $($_.Name)"; $bad = $true }
    }
    if ($bad) { throw "Intermediary refs in fleet $loader $mc" }
    Write-Host "Mapping gate passed."
    $outDir = Join-Path $distRoot $loader; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $out = Join-Path $outDir "Fleetmod-$FleetVersion-$loader-$mc.jar"
    if (Test-Path $out) { Remove-Item $out -Force }
    Push-Location $cls
    & $jarTool --create --file $out .
    Pop-Location
    if ($LASTEXITCODE -ne 0) { throw "jar failed for fleet $loader $mc" }
    $h = (Get-FileHash $out -Algorithm SHA256).Hash.Substring(0, 12)
    Write-Host "OK: $out (sha12=$h)"
    return $out
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $cand = Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "^jdk-25" } | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $cand) { throw "No JDK 25 found (pass -JavaHome)" }
    $JavaHome = $cand.FullName
}

$results = @()
foreach ($mc in $McVersions) {
    foreach ($loader in $Loaders) {
        if ($loader -eq "quilt") { continue } # repackaged after fabric
        try {
            if ($mc -eq "1.21.11") {
                $mod = "fleet-$loader"
                Write-Host "===== fleet $loader 1.21.11 (gradle) ====="
                & (Join-Path $root "gradlew.bat") -p $root "-Ptarget_loader=$mod" clean build writeVersion --no-daemon 2>&1 | Select-Object -Last 2 | Out-String | Write-Output
                $built = Get-ChildItem -LiteralPath (Join-Path $root "fleet\$mod\build\libs") -Filter "*.jar" |
                    Where-Object { $_.Name -notlike "*sources*" } | Select-Object -First 1
                if (-not $built) { throw "no jar built for $mod" }
                $outDir = Join-Path $distRoot $loader; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
                $out = Join-Path $outDir "Fleetmod-$FleetVersion-$loader-1.21.11.jar"
                Copy-Item -LiteralPath $built.FullName -Destination $out -Force
                Write-Host "OK: $out"
            } elseif ($loader -eq "forge") {
                # Forge 26.x: standalone ForgeGradle (built once per script run, copied per MC).
                Write-Host "===== fleet forge $mc (standalone, shared build) ====="
            } else {
                $extra = @()
                if ($loader -eq "fabric") {
                    if (-not $FapiMap.ContainsKey($mc)) { throw "No FAPI mapping for $mc" }
                    $fapiVer = $FapiMap[$mc]
                    $fapiPom = Get-CachedJar "net.fabricmc.fabric-api\fabric-api" $fapiVer
                    if (-not $fapiPom -or $fapiPom -notlike "*.pom") {
                        $fapiPom = Join-Path $libDir "fabric-api-$fapiVer.pom"
                    }
                    $pomText = Get-Content -LiteralPath $fapiPom -Raw
                    $mods = [regex]::Matches($pomText, "<dependency>.*?</dependency>", "Singleline") | ForEach-Object {
                        if ($_.Value -match "<artifactId>(fabric-[^<]+)</artifactId>\s*<version>([^<]+)</version>") {
                            [pscustomobject]@{ artifact = $Matches[1]; version = $Matches[2] }
                        }
                    } | Where-Object { $_ -and (@("fabric-command-api-v2", "fabric-api-base") -contains $_.artifact) }
                    foreach ($m in $mods) {
                        $extra += (Get-MavenJar "https://maven.fabricmc.net" "net.fabricmc.fabric-api\$($m.artifact)" $m.artifact $m.version)
                    }
                    $extra += (Get-MavenJar "https://maven.fabricmc.net" "net.fabricmc\fabric-loader" "fabric-loader" $LoaderVersion)
                } elseif ($loader -eq "neoforge") {
                    if (-not $NeoMap.ContainsKey($mc)) { throw "No NeoForge mapping for $mc" }
                    $nv = $NeoMap[$mc]
                    $neoJar = Join-Path $libDir "neoforge-$nv-universal.jar"
                    if (-not (Test-FileExists $neoJar)) { throw "Missing $neoJar (run a StatusMod neoforge build first)" }
                    $extra += $neoJar
                    $busJar = Join-Path $libDir "bus-8.0.5.jar"
                    if (Test-FileExists $busJar) { $extra += $busJar }
                    $fmlJar = Join-Path $libDir "fml-loader-11.0.16.jar"
                    if (Test-FileExists $fmlJar) { $extra += $fmlJar }
                }
                Write-Host "===== fleet $loader $mc (plain) ====="
                Invoke-PlainBuild $loader $mc $extra | Out-Null
            }
            $results += [pscustomobject]@{ loader = $loader; mc = $mc; status = "ok" }
        } catch {
            Write-Host "FAIL fleet $loader $mc : $($_.Exception.Message)"
            $results += [pscustomobject]@{ loader = $loader; mc = $mc; status = "failed: $($_.Exception.Message)" }
        }
    }
}

# Forge 26.x standalone (one build, copied per requested 26.x MC like StatusMod).
if ($Loaders -contains "forge" -and @($McVersions | Where-Object { $_ -ne "1.21.11" }).Count -gt 0) {
    Write-Host "===== fleet forge 26.x (ForgeGradle standalone) ====="
    & (Join-Path $root "gradlew.bat") -p (Join-Path $root "Loader\fleet-forge26.1") clean build --no-daemon 2>&1 | Select-Object -Last 2 | Out-String | Write-Output
    $src = Get-ChildItem -LiteralPath (Join-Path $root "Loader\fleet-forge26.1\build\libs") -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*slim*" } | Select-Object -First 1
    if (-not $src) { throw "no fleet forge26.1 jar built" }
    foreach ($mc in @($McVersions | Where-Object { $_ -ne "1.21.11" })) {
        $outDir = Join-Path $distRoot "forge"; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        Copy-Item -LiteralPath $src.FullName -Destination (Join-Path $outDir "Fleetmod-$FleetVersion-forge-$mc.jar") -Force
        Write-Host "OK: fleet forge $mc (shared build)"
    }
}

# Quilt: repackage fabric jars.
if ($Loaders -contains "quilt") {
    $quiltJsonSrc = Join-Path $root "fleet\fleet-quilt\src\main\resources\quilt.mod.json"
    foreach ($mc in $McVersions) {
        $fabricJar = Join-Path $distRoot "fabric\Fleetmod-$FleetVersion-fabric-$mc.jar"
        if (-not (Test-Path $fabricJar)) { Write-Host "SKIP quilt $mc (no fabric jar)"; continue }
        $qj = (Get-Content -LiteralPath $quiltJsonSrc -Raw) `
            -replace '\$\{version\}', $FleetVersion `
            -replace '\$\{quilt_loader_version\}', $QuiltLoaderVersion
        $tmp = Join-Path $env:TEMP "fleet-quilt-$mc"
        if (Test-Path $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $tmp | Out-Null
        Push-Location $tmp
        & (Join-Path $JavaHome "bin\jar.exe") xf $fabricJar 2>$null
        if (Test-Path (Join-Path $tmp "fabric.mod.json")) { Remove-Item (Join-Path $tmp "fabric.mod.json") -Force }
        [System.IO.File]::WriteAllText((Join-Path $tmp "quilt.mod.json"), $qj)
        $outDir = Join-Path $distRoot "quilt"; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
        & (Join-Path $JavaHome "bin\jar.exe") cf (Join-Path $outDir "Fleetmod-$FleetVersion-quilt-$mc.jar") -C $tmp . 2>$null
        Pop-Location
        Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "OK: fleet quilt $mc (repackaged)"
    }
}

# Verify metadata versions.
$fail = 0
Get-ChildItem -LiteralPath $distRoot -Filter "Fleetmod-$FleetVersion-*.jar" -Recurse -File | ForEach-Object {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
    try {
        $names = @($z.Entries | ForEach-Object { $_.FullName })
        $ok = $false
        if ($names -contains "fabric.mod.json") {
            $e = $z.GetEntry("fabric.mod.json")
            $r = New-Object System.IO.StreamReader($e.Open())
            $ok = $r.ReadToEnd() -match [regex]::Escape($FleetVersion); $r.Close()
        } elseif ($names -contains "quilt.mod.json") {
            $e = $z.GetEntry("quilt.mod.json")
            $r = New-Object System.IO.StreamReader($e.Open())
            $ok = $r.ReadToEnd() -match [regex]::Escape($FleetVersion); $r.Close()
        } elseif ($names -contains "META-INF/mods.toml") {
            $e = $z.GetEntry("META-INF/mods.toml")
            $r = New-Object System.IO.StreamReader($e.Open())
            $ok = $r.ReadToEnd() -match "version=`"$FleetVersion`""; $r.Close()
        } elseif ($names -contains "META-INF/neoforge.mods.toml") {
            $e = $z.GetEntry("META-INF/neoforge.mods.toml")
            $r = New-Object System.IO.StreamReader($e.Open())
            $ok = $r.ReadToEnd() -match "version=`"$FleetVersion`""; $r.Close()
        }
        if ($ok) { Write-Host "verify OK: $($_.Name)" } else { Write-Host "verify FAIL: $($_.Name)"; $fail++ }
    } finally { $z.Dispose() }
}
if ($fail -gt 0) { throw "$fail fleet JAR(s) failed verification" }
Write-Host "All fleet JARs verified OK (version=$FleetVersion)"
$results | Format-Table | Out-String | Write-Output
