param(
    [string[]]$Loaders = @("fabric", "forge", "neoforge", "quilt", "datapack"),
    [string[]]$Versions = @("1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "../..")

foreach ($loader in $Loaders) {
    $loaderRoot = Join-Path $root $loader
    foreach ($version in $Versions) {
        $versionDir = Join-Path $loaderRoot $version
        New-Item -ItemType Directory -Force -Path $versionDir | Out-Null

        $readmePath = Join-Path $versionDir "README.md"
        if (-not (Test-Path $readmePath)) {
            @(
                "# $loader $version",
                "",
                "This directory is reserved for loader-specific, version-specific release assets.",
                "The shared source stays in `$loader/src` and is assembled by the build scripts."
            ) | Set-Content -LiteralPath $readmePath
        }

        $gitkeepPath = Join-Path $versionDir ".gitkeep"
        if (-not (Test-Path $gitkeepPath)) {
            Set-Content -LiteralPath $gitkeepPath -Value ""
        }
    }
}

Write-Host "Generated version folders for loaders: $($Loaders -join ', ')"
