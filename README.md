# StatusMod

StatusMod adds lightweight status tags for players on Fabric servers and clients.
Players can set a custom status text and color that is shown in the player list (TAB) and name rendering via scoreboard team prefix/suffix.

## Repository Layout

- `Loader/fabric/` - main Fabric source tree for Minecraft 1.19 through 1.21.11
- `Loader/fabric26.1/` - dedicated Fabric 26.1 branch
- `scripts/local/` - local build and release helpers
- `src_legacy/` - archived legacy source tree kept for reference during migration

The active source of truth is the Fabric loader directories. `src_legacy/` is intentionally preserved as reference material and should not be treated as the primary build tree.

Version subfolders inside each Fabric loader directory are reserved for release assets and version-specific metadata. The Java source itself stays centralized inside each loader's `src/` tree so features are maintained once and packaged many times.

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

This repository currently builds against the Fabric versions in the multiversion workflow:

- Minecraft `1.19` through `1.21.11`
- Fabric API selected per Minecraft version by `scripts/local/build-multiversion.ps1`
- Dedicated Fabric 26.1 builds for `26.1`, `26.1.1`, and `26.1.2`

Release workflows can publish additional game-version variants depending on your CI setup.

## Building

Recommended local build flow:

1. Build the shared Fabric line with `scripts/local/build-multiversion.ps1`.
2. Build the dedicated 26.1 branch with `Loader/fabric26.1`.
3. Inspect the generated artifacts in `dist/multiversion/` and `Loader/fabric26.1/build/`.

Examples:

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric -ContinueOnError:$false
.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon
```

## Installation

1. Download the release JAR that matches your Fabric Minecraft version.
2. Put it in the `mods/` folder on the server or client.
3. Start or restart Minecraft/server.

## Privacy and Security

- No external telemetry.
- No remote transmission of player status data by the mod itself.
- Only minimal local data is stored (UUID + status/settings/block state).

## Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues

## License

MIT License. See `LICENSE`.
