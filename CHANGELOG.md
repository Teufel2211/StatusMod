# Changelog

All notable changes to this project are documented in this file.

## Unreleased (1.4.0)

- Self-status GUI: bare `/status` opens a beginner-friendly menu (colored wool presets including own customs, live status display, next/clear buttons).
- Admin player GUI: `/status gui` with skinned heads, search and pages; clicking a head prepares the status/color command.
- Dashboard link: automatic server setup via console code, live two-way sync (status, badge, mute, block, online presence), `/status transfer`, `/status apikey`, `/status setup-secret`, `/status owner-code`, `/status sync`.
- Owner one-time login: `/fleet owner-code` (Fleet mod, server console only) prints a 10-minute single-use code; redeem it via the "Log in as owner" button landing on the fleet admin page.
- Fleet mod (separate jar): pulls dashboard URL + setup secret from the dashboard (own `config/statusmodfleet/config.json`, serverId/apiKey stay per server); StatusMod prefers fleet values when present.
- Custom dashboard avatars: `/status avatar <name|url|off>` (self or, for admins, other players).
- Bedrock (Geyser/Floodgate) output channels: boss bar/action bar, sidebar and `/status list`; warning when running on unsupported MC 26.3.
- New toggles/commands: `/status sidebar`, `/status topbar`, `/status version`, clickable status history.
- Per-world status/color, placeholders (e.g. `{time}`), emoji support, bracket styles, font styles.
- Auto-AFK rework: configurable text/color, restores the previous status on return.
- Pretty-printed `config.json`; reload merges instead of clobbering manual edits.
- Quilt loader support and MC 26.3 support: 24 jars (fabric/forge/neoforge/quilt x 1.21.11/26.1/26.1.1/26.1.2/26.2/26.3).
- GUI hardening: menu items can no longer be taken, dropped or duplicated (restore + purge + resync + per-tick reconcile + close cleanup).
- Item names/lore work on all runtimes (fixes paper/default names on Fabric/Quilt 1.21.11).
- Permissions: UUID-based operator checks (no username spoofing), strict command-permission tiers.
- Dashboard URLs strictly validated (HTTPS or loopback host only).
- Input sanitization for status/badge/preset/avatar/transfer/sync text; preset names normalized (max 500 customs).
- Sync/transfer HTTP bodies capped (8 MB); transfer capped at 5000 players; fetched API keys length-checked.
- Avatar URLs restricted to https.
- Forge `mods.toml` version now expands correctly in standalone builds.

## 1.3.0

- Added Forge and NeoForge loader support.
- Forge: MC 1.19 - 25.x via Architectury Loom (`loom.platform=forge`).
- Forge: MC 26.1 - 26.2 via standalone ForgeGradle 7 (`Loader/forge26.1/`).
- NeoForge: MC 1.21+ via Architectury Loom (`loom.platform=neoforge`).
- MC 26.x builds use unobfuscated game code (no mappings needed).
- Multiversion build script now builds all 15 combinations: fabric/forge/neoforge × 26.2/26.1.2/26.1.1/26.1/1.21.11.
- Removed vestigial `forge_minecraft_version` and `forge_gradle_version` properties.
- Cleaned up `.gitignore` and temp files.

## 1.2.9

- Added local multi-version build and publish scripts.
- Added auto-detected Fabric API selection from Maven for 1.21.x.
- Added robust permission checks across Minecraft/Fabric versions.
- Added rainbow1530 palette support from bundled RGB list.
