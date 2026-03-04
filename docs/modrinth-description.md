# StatusMod

Simple and reliable player status tags for Fabric.

Set a custom status text and color that appears in TAB and name rendering using scoreboard teams.
Great for AFK, Busy, Building, Streaming, Trading, or custom role labels.

## Features

- `/status <text> [color]`
- `/status clear`
- `/color <name|hex|reset>`
- German + English color aliases
- Hex colors supported (`#RRGGBB`, `#RGB`)
- Status/settings persistence in `config/statusmod/`
- Auto-restore on join
- Periodic reapply for stability (`statusReapplyTicks`) if other mods/plugins override team formatting
- Admin tools: block/unblock and admin status override commands

## Quick Usage

- `/status AFK red`
- `/status Working #ffaa00`
- `/settings brackets on`
- `/settings position before`
- `/settings words 2`

## Config

`config/statusmod/config.json`

Key options:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (default `100`, 20 ticks = 1 second)

## Permissions

- Admin commands use OP level and optional LuckPerms node checks.
- Regular player status commands are available unless a player is blocked.

## Data and Privacy

- Stores only local JSON files in `config/statusmod/`
- No external telemetry

## Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues
- License: MIT
