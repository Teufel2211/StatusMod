# StatusMod

StatusMod adds lightweight status tags for players on Fabric servers and clients.
Players can set a custom status text and color that is shown in the player list (TAB) and name rendering via scoreboard team prefix/suffix.

## Highlights

- `/status <text> [color]` to set your status quickly
- `/status clear` to remove your status
- Color support for English and German names, plus hex (`#RRGGBB` / `#RGB`)
- Persistent player settings and statuses in `config/statusmod/`
- Automatic status restore on join
- Periodic status reapply (`statusReapplyTicks`) to recover from team/suffix overrides by other systems
- Admin tools for moderation (`/block`, `/unblock`, `/status admin ...`)

## Commands

- `/status <text> [color]`
- `/status clear`
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

This repository currently builds against the versions in `gradle.properties`:

- Minecraft `1.20.1`
- Fabric Loader `0.16.9`
- Fabric API `0.92.2+1.20.1`

Release workflows can publish additional game-version variants depending on your CI setup.

## Installation

1. Download the release JAR.
2. Put it in the `mods/` folder (server or client with matching Fabric/Minecraft versions).
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
