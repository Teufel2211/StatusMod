# StatusMod (Fabric)

StatusMod adds configurable player status labels shown in TAB and above player names via scoreboard formatting.

Use it for AFK tags, role tags, work mode labels, or temporary communication states.

## Main Commands

- `/status <text> [color]`
- `/status clear`
- `/color <name|hex|reset>`
- `/settings brackets <on|off>`
- `/settings position <before|after>`
- `/settings words <number>`

Admin/moderation:

- `/status admin set <player> <status> [color]`
- `/status admin clear <player>`
- `/block <player>`
- `/unblock <player>`
- `/status config show`
- `/status config reload`

## Why this mod is stable

- Status is persisted to local config files
- Status is restored on player join
- Status is periodically re-applied (`statusReapplyTicks`) to recover from external team overrides
- Storage uses safer write behavior and fallback handling for malformed JSON

## Color support

- Named colors (English/German aliases)
- Hex colors (`#RRGGBB` / `#RGB`)
- Status text color only (player name remains readable)

## Config file

`config/statusmod/config.json`

Important options:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (20 ticks = 1 second)

## Compatibility

Built against Fabric + Minecraft versions defined in this repository's `gradle.properties`.

## Data & Privacy

- Local-only JSON storage in `config/statusmod/`
- No telemetry or external status sync

## Project Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues
- License: MIT
