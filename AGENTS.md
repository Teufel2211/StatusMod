# AGENTS.md

Multi-version Minecraft mod built with **Architectury Loom**. Targets Fabric, Forge, NeoForge, and Quilt across MC 1.19–26.2.

---

## Build System

**Architectury Loom** (`dev.architectury.loom:1.17.491`) handles all loaders — no ForgeGradle.

| Module | Key Dependency Config | Platform |
|--------|----------------------|----------|
| `common/` | `dev.architectury:architectury:$architectury_version` | Shared code |
| `fabric/` | `fabricApi` | Fabric |
| `forge/` | `forge "net.minecraftforge:forge:..."` | Forge (`loom.platform=forge`) |
| `neoforge/` | `neoForge "net.neoforged:neoforge:..."` | NeoForge (`loom.platform=neoforge`) |

Each module's `gradle.properties` sets its platform: `loom.platform=forge` or `loom.platform=neoforge`. Fabric/Common use plain Loom (no platform property).

---

## Key Commands

```bash
# Build current version (all 4 loaders)
.\gradlew.bat clean build writeVersion

# Build all MC versions (uses build-multiversion.ps1)
powershell -File scripts\local\build-multiversion.ps1

# Update Forge mappings
powershell -File scripts\local\update-forge-mappings.ps1

# Update NeoForge mappings
powershell -File scripts\local\update-neoforge-mappings.ps1

# Update Quilt mappings
powershell -File scripts\local\update-quilt-mappings.ps1

# Update Fabric API
powershell -File scripts\local\update-fabric-api.ps1
```

**Important:** Use `.\gradlew.bat`, not `gradlew`. The wrapper is in `gradlew.bat`.

---

## Version Props

Controlled in `gradle.properties` (root) and overridden by `build-multiversion.ps1` per MC version:

- `minecraft_version` — MC version (default `1.21.11`)
- `yarn_mappings` — Fabric yarn version
- `loader_version` — Fabric loader version
- `fabric_api_version` — Fabric API version
- `neoforge_version` — NeoForge version
- `forge_version` — Forge version (no longer needs ForgeGradle version)
- `architectury_version` — Architectury API version
- `loom_version` — Loom version (for Quilt)

---

## Forge EventBus 7 (MC 26.x)

Forge 26.x uses EventBus 7.0.1 with Records-based event system:

```java
// New import
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

// Tick events are sealed interfaces with records
if (event instanceof TickEvent.ServerTickEvent.Post) {
    var server = event.server();       // Record accessor
    // player.server is private → use player.level().getServer()
}
```

---

## Multiversion Build

`build-multiversion.ps1`:
- Reads `scripts/local/loader-versions.json` for version-specific props
- Overwrites root `gradle.properties` per build, restores git state after
- `$ContinueOnError = $true` by default — builds continue on failure
- Output: `build-multiversion-output/<loader>/statusmod-<ver>-<loader>-<mc>.jar`

---

## Key Files

| File | Purpose |
|------|---------|
| `settings.gradle` | Architectury Loom plugin declaration |
| `gradle.properties` | Default MC version + all version props |
| `common/src/main/java/com/teufel/statusmod/` | Platform-agnostic code |
| `forge/src/main/java/com/teufel/statusmod/forge/StatusModForge.java` | Forge platform |
| `neoforge/src/main/java/com/teufel/statusmod/neoforge/StatusModNeoForge.java` | NeoForge platform |
| `fabric/src/main/java/com/teufel/statusmod/fabric/StatusModFabric.java` | Fabric platform |
| `scripts/local/loader-versions.json` | Version matrix for multiversion builds |
| `scripts/local/build-multiversion.ps1` | Multiversion build orchestrator |

---

## Common Pitfalls

- **`player.server` is private** in Forge/NeoForge 26.x. Use `player.level().getServer()` instead.
- **Forge 26.x uses EventBus 7** with Records — `event.phase == Phase.END` no longer exists. Use `event instanceof TickEvent.ServerTickEvent.Post`.
- **NeoForge needs `-Xmx6g`** — NeoForge processing extracts 1125+ Forge source classes.
- **`forge_gradle_version` is unused** — Architectury Loom handles Forge natively. The property in `loader-versions.json` is vestigial (set to `"unused"`).
- **Use `.\gradlew.bat`**, not `gradlew`.
