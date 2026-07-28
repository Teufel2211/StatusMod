# StatusMod

StatusMod adds lightweight status tags for players on Minecraft servers and clients.
Players can set a custom status text and color that is shown in the player list (TAB) and name rendering via scoreboard team prefix/suffix.

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

## Highlights

- `/status <text> [color]` to set your status quickly
- `/status clear` to remove your status
- `/status preset <name>`, `/status timed <minutes> <status>`, `/status random <status>`
- `/status world <status>` per-dimension override
- `/status history` to view recent statuses
- Color support for English and German names, plus hex (`#RRGGBB` / `#RGB`)
- Placeholders: `{world}`, `{ping}`
- Persistent player settings and statuses in `config/statusmod/`
- Automatic status restore on join
- Periodic status reapply (`statusReapplyTicks`) to recover from team/suffix overrides by other systems
- Admin tools for moderation (`/block`, `/unblock`, `/status admin ...`)

## Commands

- `/status <text> [color]`
- `/status clear`
- `/status preset <name>`
- `/status timed <minutes> <status>`
- `/status random <status>`
- `/status world <status> [color]`
- `/status world clear`
- `/status history`
- `/color <name|hex|reset>`
- `/settings brackets <on|off>`
- `/settings position <before|after>`
- `/settings words <number>`
- `/status admin set <player> <status> [color]` (if enabled in config)
- `/status admin clear <player>` (if enabled in config)
- `/block <player>`
- `/unblock <player>`
- `/status config show`
- `/status config reload`
- `/modinfo`

## Examples

- `/status AFK red`
- `/status Workshop #ffaa00`
- `/status Busy`
- `/settings words 2`
- `/status Not Disturb orange` (with `words=2`, status = `Not Disturb`, color = `orange`)

## Configuration

Config file: `config/statusmod/config.json`

Important fields:

- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `enableAdminOverrides`
- `defaultColor`
- `statusReapplyTicks` (20 ticks = 1 second, default 100)
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

Notes:

- `statusReapplyTicks` controls how often online statuses are re-applied.
- Lower value = faster recovery if other mods/plugins override teams, but slightly more server work.
- Higher value = less overhead, but longer visible gaps if overridden.

## Storage and Reliability

StatusMod stores only local JSON files in `config/statusmod/`:

- `players.json`
- `blocked_players.json`
- `config.json`

Reliability improvements include:

- Atomic file writes (`.tmp` + replace) to reduce corruption risk
- Input sanitization for invalid values
- Recovery path for malformed JSON (keeps the mod running and backs up broken files)

## Permissions

- Admin/moderation commands check operator level and can integrate with LuckPerms nodes from config.
- Base player status commands are available unless the player is blocked.

## Compatibility

This repository builds against multiple loaders and Minecraft versions:

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

## Installation

1. Download the release JAR that matches your loader and Minecraft version.
2. Put it in the `mods/` folder on the server or client.
3. Start or restart Minecraft/server.

**Note:** Forge/NeoForge builds require the corresponding mod loader installed on the server/client.

## Privacy and Security

- No external telemetry.
- No remote transmission of player status data by the mod itself.
- Only minimal local data is stored (UUID + status/settings/block state).

## Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues

## License

MIT License. See `LICENSE`.
