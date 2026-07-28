# Building

## All loaders (recommended)

Build all 15 combinations (fabric/forge/neoforge × 26.2/26.1.2/26.1.1/26.1/1.21.11):

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge -ContinueOnError:$false
```

## Single loader

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders forge -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders neoforge -ContinueOnError:$false
```

## Standalone 26.x branches

```powershell
.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon
.\gradlew.bat -p Loader\forge26.1 clean build --no-daemon
```

## What gets built

- Shared Fabric: MC 1.19 - 1.21.11 via Loom
- Shared Forge: MC 1.19 - 25.x via Loom (`loom.platform=forge`)
- Shared NeoForge: MC 1.21+ via Loom (`loom.platform=neoforge`)
- Dedicated Fabric 26.x: MC 26.1 - 26.2 via standalone Loom
- Dedicated Forge 26.x: MC 26.1 - 26.2 via standalone ForgeGradle 7

Output is written to `dist/multiversion/` for the shared line and `Loader/<loader>26.1/build/` for the 26.x branches.

## Requirements

- Java 21 for MC < 26
- Java 25 for MC >= 26 (Forge/NeoForge 26.x)
- Gradle is invoked via wrapper (`gradlew.bat`) -- no separate install needed
