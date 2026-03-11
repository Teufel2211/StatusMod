# StatusMod

## Summary

Lightweight status tags for Fabric: text + color in TAB and above names, with presets, per-world overrides, timed reset, history, cooldowns, placeholders, and admin tools. Stable via local storage and periodic reapply.

## Description

EN: StatusMod is a small, stable status-tag system for Fabric. It stores settings locally, restores them on join, and re-applies tags periodically so other mods/plugins can’t permanently override the display.

DE: StatusMod ist ein kleines, stabiles Status-Tag-System für Fabric. Einstellungen werden lokal gespeichert, beim Join wiederhergestellt und regelmäßig neu angewendet, damit andere Mods/Plugins die Anzeige nicht dauerhaft überschreiben.

## Features (EN)

- `/status <text> [color]`
- `/status clear`
- `/status preset <name>`
- `/status timed <minutes> <status>`
- `/status random <status>`
- `/status world <status> [color]`
- `/status world clear`
- `/status history`
- `/color <name|hex|reset>`
- Presets: AFK, Busy, Stream, Shop (color + font)
- Timed status: auto-reset after X minutes
- Random status color: stable per player (same result each time)
- Per-world status + color overrides
- Status history suggestions (recent entries)
- Cooldown/rate-limit to reduce spam
- Placeholders: `{world}`, `{ping}`
- Staff badges (optional) for admins/operators
- German + English color aliases
- Hex colors supported (`#RRGGBB`, `#RGB`)
- Status/settings persistence in `config/statusmod/`
- Auto-restore on join
- Periodic reapply for stability (`statusReapplyTicks`) if other mods/plugins override team formatting
- Admin tools: block/unblock and admin status override commands

## Features (DE)

- `/status <text> [color]`
- `/status clear`
- `/status preset <name>`
- `/status timed <minutes> <status>`
- `/status random <status>`
- `/status world <status> [color]`
- `/status world clear`
- `/status history`
- `/color <name|hex|reset>`
- Presets: AFK, Busy, Stream, Shop (Farbe + Schrift)
- Timed Status: automatische Rücksetzung nach X Minuten
- Zufällige Statusfarbe: stabil pro Spieler (gleiches Ergebnis)
- Pro-Welt Status + Farb-Overrides
- Status-History-Vorschläge (letzte Einträge)
- Cooldown/Rate-Limit gegen Spam
- Platzhalter: `{world}`, `{ping}`
- Staff-Badges (optional) für Admins/Operatoren
- Deutsche + Englische Farb-Aliase
- Hex-Farben (`#RRGGBB`, `#RGB`)
- Status/Settings persistent in `config/statusmod/`
- Auto-Restore beim Join
- Periodisches Reapply (`statusReapplyTicks`) falls andere Mods/Plugins Teams überschreiben
- Admin-Tools: block/unblock und Admin-Override-Commands

## Quick Usage (EN)

- `/status AFK red`
- `/status Working #ffaa00`
- `/settings brackets on`
- `/settings position before`
- `/settings words 2`
- `/status preset afk`
- `/status timed 15 Busy red`
- `/status random Streaming`
- `/status world Mining gray`

## Quick Usage (DE)

- `/status AFK red`
- `/status Arbeiten #ffaa00`
- `/settings brackets on`
- `/settings position before`
- `/settings words 2`
- `/status preset afk`
- `/status timed 15 Busy red`
- `/status random Streaming`
- `/status world Mining gray`

## Config (EN)

`config/statusmod/config.json`

Key options:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (default `100`, 20 ticks = 1 second)
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

Notes:

- `statusCooldownSeconds` applies to non-admin players.
- Staff badges are appended to the status tag when enabled.

## Config (DE)

`config/statusmod/config.json`

Wichtige Optionen:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (Standard `100`, 20 Ticks = 1 Sekunde)
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

Hinweise:

- `statusCooldownSeconds` gilt für Nicht-Admins.
- Staff-Badges werden ans Status-Tag angehängt, wenn aktiviert.

## Permissions (EN)

- Admin commands use OP level and optional LuckPerms node checks.
- Regular player status commands are available unless a player is blocked.

## Permissions (DE)

- Admin-Commands nutzen OP-Level und optional LuckPerms-Nodes.
- Normale Spieler-Commands sind verfügbar, solange der Spieler nicht geblockt ist.

## Data and Privacy (EN)

- Stores only local JSON files in `config/statusmod/`
- No external telemetry

## Data and Privacy (DE)

- Speichert nur lokale JSON-Dateien in `config/statusmod/`
- Keine Telemetrie oder externe Datenübertragung

## Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues
- License: MIT
