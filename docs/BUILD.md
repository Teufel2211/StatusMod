# Developer Guide: Building StatusMod

This page holds the technical details that don't belong in the `README.md`.
For installation, commands and features, see `README.md`.

## Repository Layout

- `Loader/fabric/` - main Fabric source tree (MC 1.19 - 1.21.11)
- `Loader/fabric26.1/` - dedicated Fabric 26.x branch (MC 26.1 - 26.2)
- `Loader/forge26.1/` - dedicated Forge 26.x branch (MC 26.1 - 26.2)
- `forge/` - Forge source tree (MC 1.19 - 25.x, uses Architectury Loom)
- `neoforge/` - NeoForge source tree (MC 1.21+, uses Architectury Loom)
- `common/` - shared code across all loaders
- `scripts/local/` - local build and release helpers
- `src_legacy/` - archived legacy source tree kept for reference during migration

The active source of truth is the Fabric loader directories. `src_legacy/` is intentionally preserved as reference material and should not be treated as the primary build tree.

Version subfolders inside each loader directory are reserved for release assets and version-specific metadata. The Java source itself stays centralized inside each loader's `src/` tree so features are maintained once and packaged many times.

## Compatibility Matrix

| Loader | Minecraft versions | Build system |
|--------|-------------------|--------------|
| Fabric | 1.19 - 1.21.11 | Architectury Loom |
| Fabric | 26.1 - 26.2 | Standalone Loom (`Loader/fabric26.1/`) |
| Forge | 1.19 - 25.x | Architectury Loom (`loom.platform=forge`) |
| Forge | 26.1 - 26.2 | Standalone ForgeGradle 7 (`Loader/forge26.1/`) |
| NeoForge | 1.21+ | Architectury Loom (`loom.platform=neoforge`) |

Release workflows can publish additional game-version variants depending on your CI setup.

## Building

### All loaders (recommended)

Build all 15 combinations (fabric/forge/neoforge × 26.2/26.1.2/26.1.1/26.1/1.21.11):

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge -ContinueOnError:$false
```

### Single loader

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders forge -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders neoforge -ContinueOnError:$false
```

### Standalone 26.x branches

```powershell
.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon
.\gradlew.bat -p Loader\forge26.1 clean build --no-daemon
```

Output is written to `dist/multiversion/` for the shared line and `Loader/<loader>26.1/build/` for the 26.x branches.

### Local release publishing

```powershell
.\scripts\local\run-workflow-local.ps1 -Workflow build
.\scripts\local\run-workflow-local.ps1 -Workflow publish -DryRun
.\scripts\local\run-workflow-local.ps1 -Workflow publish-multi-version -ReleaseType release
```

Output JARs are named `statusmod-<modversion>-<modloader>-<minecraftversion>.jar`.

Required env vars for publishing: `MODRINTH_PROJECT_ID`, `MODRINT_TOKEN`, `CURSEFORGE_PROJECT_ID`, `CURSEFORGE_API_KEY`.
Optional: copy `scripts/local/.release.env.example` to `scripts/local/.release.env`.

## Storage and Reliability (internals)

StatusMod stores only local JSON files in `config/statusmod/`:

- `players.json`
- `blocked_players.json`
- `config.json`

Reliability improvements include:

- Atomic file writes (`.tmp` + replace) to reduce corruption risk
- Input sanitization for invalid values
- Recovery path for malformed JSON (keeps the mod running and backs up broken files)
- Periodic status reapply (`statusReapplyTicks`, 20 ticks = 1 second, default 100):
  lower value = faster recovery if other mods/plugins override teams, but slightly
  more server work; higher value = less overhead, but longer visible gaps if overridden.
