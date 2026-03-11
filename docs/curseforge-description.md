# StatusMod (Fabric)

## Summary

Lightweight status labels for TAB and nameplates with presets, timed statuses, per-world overrides, cooldowns, history, placeholders, and admin tools. Stable via local storage and periodic reapply.

## Description

EN: StatusMod is designed for stability: local JSON data, automatic restore on join, and periodic reapply so external overrides don’t stick permanently.

DE: StatusMod ist auf Stabilität ausgelegt: lokale JSON-Daten, automatische Wiederherstellung beim Join und periodisches Reapply, damit externe Overrides nicht dauerhaft wirken.

## Main Commands (EN)

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

Admin/moderation:

- `/status admin set <player> <status> [color]`
- `/status admin clear <player>`
- `/block <player>`
- `/unblock <player>`
- `/status config show`
- `/status config reload`

## Main Commands (DE)

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

Admin/Moderation:

- `/status admin set <player> <status> [color]`
- `/status admin clear <player>`
- `/block <player>`
- `/unblock <player>`
- `/status config show`
- `/status config reload`

## Why this mod is stable (EN)

- Status is persisted to local config files
- Status is restored on player join
- Status is periodically re-applied (`statusReapplyTicks`) to recover from external team overrides
- Storage uses safer write behavior and fallback handling for malformed JSON

## Why this mod is stable (DE)

- Status wird lokal gespeichert
- Status wird beim Join wiederhergestellt
- Periodisches Reapply (`statusReapplyTicks`) bei externen Team-Overrides
- Sicheres Schreiben und Fallbacks bei fehlerhaftem JSON

## Color support (EN)

- Named colors (English/German aliases)
- Hex colors (`#RRGGBB` / `#RGB`)
- Status text color only (player name remains readable)
- Placeholders: `{world}`, `{ping}`

## Color support (DE)

- Farb-Namen (Englisch/Deutsch)
- Hex-Farben (`#RRGGBB` / `#RGB`)
- Nur Status-Text wird gefärbt (Name bleibt lesbar)
- Platzhalter: `{world}`, `{ping}`

## Feature Details (EN)

- Presets: AFK, Busy, Stream, Shop (color + font)
- Timed status: auto-reset after X minutes
- Random status color: stable per player
- Per-world status and color overrides
- Status history suggestions (recent entries)
- Cooldowns/rate limits to reduce spam
- Staff badges for admins/operators (optional)

## Feature Details (DE)

- Presets: AFK, Busy, Stream, Shop (Farbe + Schrift)
- Timed Status: automatische Rücksetzung nach X Minuten
- Zufällige Statusfarbe: stabil pro Spieler
- Pro-Welt Status- und Farb-Overrides
- Status-History-Vorschläge (letzte Einträge)
- Cooldowns/Rate-Limits gegen Spam
- Staff-Badges für Admins/Operatoren (optional)

## Config file (EN)

`config/statusmod/config.json`

Important options:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (20 ticks = 1 second)
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

Notes:

- `statusCooldownSeconds` applies to non-admin players.
- Staff badges are appended to the status tag when enabled.

## Config file (DE)

`config/statusmod/config.json`

Wichtige Optionen:

- `defaultColor`
- `enableAdminOverrides`
- `adminOpLevel`
- `statusPermissionNode`
- `adminPermissionNode`
- `statusReapplyTicks` (20 Ticks = 1 Sekunde)
- `statusCooldownSeconds`
- `statusHistorySize`
- `enableStaffBadge`
- `staffBadgeText`
- `staffBadgeColor`

Hinweise:

- `statusCooldownSeconds` gilt für Nicht-Admins.
- Staff-Badges werden ans Status-Tag angehängt, wenn aktiviert.

## Compatibility (EN)

Built against Fabric + Minecraft versions defined in this repository's `gradle.properties`.

## Compatibility (DE)

Gebaut gegen die Fabric- und Minecraft-Versionen aus `gradle.properties`.

## Data & Privacy (EN)

- Local-only JSON storage in `config/statusmod/`
- No telemetry or external status sync

## Data & Privacy (DE)

- Lokale JSON-Speicherung in `config/statusmod/`
- Keine Telemetrie oder externe Datensynchronisation

## Project Links

- Source: https://github.com/Teufel2211/StatusMod
- Issues: https://github.com/Teufel2211/StatusMod/issues
- License: MIT
