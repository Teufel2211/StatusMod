# StatusMod Notes

## Reminder (2026-03-11)

- Ziel: Multiloader-Kompatibilitaet von Minecraft 1.21.11 bis 1.19.
- Loader: Fabric, Quilt, Forge, NeoForge, Datapack.
- Struktur: alles im `core` des Repos (keine separaten Module).
- Falls ich wieder stuck werde: zuerst die Build-Pipeline (Gradle + `scripts/local/build-multiversion.ps1`),
  `gradle.properties`/Versionsmatrix und die Ressourcen-Templates (`src/main/resources/fabric.mod.json`)
  pruefen.
