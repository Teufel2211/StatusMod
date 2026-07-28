# Changelog

All notable changes to this project are documented in this file.

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
