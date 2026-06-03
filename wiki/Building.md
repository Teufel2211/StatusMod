# Building

Use these commands to build only the Fabric paths.

## Shared Fabric line

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric -ContinueOnError:$false
```

## Dedicated Fabric 26.1 branch

```powershell
.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon
```

## What gets built

- Shared Fabric builds cover Minecraft `1.19` through `1.21.11`.
- The multiversion script also handles `26.1`, `26.1.1`, and `26.1.2` through the dedicated module.
- Output is written under `dist/multiversion/` for the shared line and `Loader/fabric26.1/build/` for the special branch.
