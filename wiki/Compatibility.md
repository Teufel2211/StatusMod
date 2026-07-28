# Compatibility

StatusMod builds against three loaders across multiple Minecraft versions:

| Loader | Minecraft versions | Build system |
|--------|-------------------|--------------|
| Fabric | 1.19 - 1.21.11 | Architectury Loom |
| Fabric | 26.1 - 26.2 | Standalone Loom (`Loader/fabric26.1/`) |
| Forge | 1.19 - 25.x | Architectury Loom (`loom.platform=forge`) |
| Forge | 26.1 - 26.2 | Standalone ForgeGradle 7 (`Loader/forge26.1/`) |
| NeoForge | 1.21+ | Architectury Loom (`loom.platform=neoforge`) |

## Notes

- MC 26.x ships unobfuscated (Mojang stopped obfuscating starting 26.1), so no mappings are needed for 26.x builds.
- Forge 26.x uses ForgeGradle 7 (`net.minecraftforge.gradle` `[7.0.29,8.0)`) instead of Loom.
- NeoForge uses Loom with `loom.platform=neoforge` (same as Forge for older versions).

## Build inputs

- `Loader/fabric/` -- shared Fabric line
- `Loader/fabric26.1/` -- standalone Fabric 26.x
- `Loader/forge26.1/` -- standalone Forge 26.x
- `forge/` -- shared Forge line (Loom)
- `neoforge/` -- shared NeoForge line (Loom)
- `common/` -- shared code
- `scripts/local/build-multiversion.ps1` -- orchestrates all builds
