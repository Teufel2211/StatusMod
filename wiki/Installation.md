# Installation

1. Download the release JAR that matches your **loader** and **Minecraft version** (24 combinations: fabric/forge/neoforge/quilt × 1.21.11/26.1/26.1.1/26.1.2/26.2/26.3).
2. Place the JAR in your `mods/` folder.
3. Make sure the corresponding mod loader is installed:
   - **Fabric**: Fabric Loader + Fabric API matching your MC version
   - **Forge**: Forge matching your MC version
   - **NeoForge**: NeoForge matching your MC version
   - **Quilt**: Quilt Loader + Fabric API matching your MC version
4. Start or restart the game or server.

## Notes

- 1.21.11 needs **Java 21**; 26.x needs **Java 25** (the JARs are compiled for it — older Java rejects them).
- StatusMod stores settings locally in `config/statusmod/`.
- Singleplayer works out of the box (no dashboard needed). For server sync, see [Dashboard](Dashboard).
- Quilt JARs are repackaged Fabric builds (same content, `quilt.mod.json` instead of `fabric.mod.json`).
