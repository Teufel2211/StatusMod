This repository is a small Fabric Minecraft mod (StatusMod). These instructions help AI coding agents become productive fast by highlighting the project's structure, build/run steps, important patterns, and examples to reuse.

- Project type: Fabric mod for Minecraft (Gradle + fabric-loom). See `build.gradle` for plugin versions and Java toolchain (Java 25 configured).
- Entrypoints:
  - `src/main/java/com/teufel/statusmod/StatusMod.java` — server-side mod initializer (implements `ModInitializer`). This owns `SettingsStorage` and registers commands.
  - `src/client/java/status/mod/StatusModClient.java` — client initializer (implements `ClientModInitializer`). Minimal; use for rendering/client-only hooks.
- Commands: implemented using Brigadier in `src/main/java/com/teufel/statusmod/command/`:
  - `StatusCommand.java` — sets player status, updates scoreboard teams and prefix/suffix.
  - `SettingsCommand.java` — toggles bracket/position preferences and applies them to teams.
- Persistent storage: `SettingsStorage` (uses `config/statusmod/players.json` via Gson). Storage is synchronous and saved on each change; Server lifecycle hook on shutdown writes file.
- Color mapping: `com.teufel.statusmod.util.ColorMapper` maps string keys to `Formatting` values; use it when styling `Text` objects.

Important code patterns and conventions for edits:
- Use server lifecycle and scoreboard APIs safely: commands run on the server thread and access ServerPlayerEntity.getServer() to get the scoreboard. When changing teams, the code creates team names like `status_<first8ofUUID>` and calls `scoreboard.addPlayerToTeam(entityName, team)`.
- Storage is simple and synchronous. When adding fields to `PlayerSettings`, update `PlayerSettings` class, `SettingsStorage` (if any migration needed), and any places that read/write JSON.
- Commands use Brigadier argument patterns: `StringArgumentType.greedyString()` for multi-word statuses, `word()` for single tokens. Keep message responses localized in-place (currently hard-coded German text).
- Exception handling: many command methods catch Exception and print stack traces; follow the existing pattern for robust but simple error handling.

Build / run / debug quick commands (Windows, PowerShell):
- Build the mod JAR: use the Gradle wrapper
  - Windows PowerShell: `.\gradlew.bat build`
  - Unix / WSL / Git Bash: `./gradlew build`
- Note: `build.gradle` uses `fabric-loom`. Always run Gradle tasks via the included wrapper to ensure the correct toolchain and mappings.

Files to reference when making changes (examples to reuse):
- `build.gradle` — dependency versions, toolchain, and resource processing (expands `fabric.mod.json`)
- `src/main/java/com/teufel/statusmod/StatusMod.java` — mod init and command registration
- `src/main/java/com/teufel/statusmod/command/StatusCommand.java` — how scoreboard/team/prefix/suffix are applied
- `src/main/java/com/teufel/statusmod/storage/SettingsStorage.java` — JSON storage pattern and lifecycle hook
- `src/main/resources/fabric.mod.json` — mod metadata processed by Gradle; keep `id` and `version` aligned with `build.gradle` expansion

When adding features:
- Keep server vs client code separate. Put server-only logic under `src/main/java/...` and client-only code under `src/client/java/...`.
- Update `fabric.mod.json` and any mixin configs in `src/main/resources/*.mixins.json` if adding new mixins or entrypoints.
- If adding new commands, register them in `StatusMod.onInitialize()` via `CommandRegistrationCallback.EVENT` (follow existing example).

Quick examples to copy:
- Create a scoreboard team prefix with color:
  - See `StatusCommand.setStatus(...)` for creation of `Text` and `styled` call with `ColorMapper.get(key)`.
- Persisting a setting for a player:
  - Use `StatusMod.storage.forPlayer(uuid)`, modify the POJO, then `StatusMod.storage.put(uuid, settings)`.

Limitations and safety:
- Storage is not migrated automatically. If you change `PlayerSettings` shape, consider reading missing fields with defaults.
- The code assumes `getPlayer()` exists in command context; for non-player sources guard before casting.

Known issues / Gradle + Java compatibility
- Fabric Loom plugin versions and Gradle have minimum compatibility requirements. If you see errors like "Plugin ... requires at least Gradle 8.12" or Gradle fails with "Unsupported class file major version 69", follow either of these approaches:
  - Preferred: Run Gradle with a Java runtime compatible with the plugin/Gradle version (Java 21 is a safe choice for many Fabric toolchains). Example PowerShell to run a single Gradle command with a specific Java home:

```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-21'
# then run gradle using the wrapper:
```instructions
StatusMod — concise guide for AI coding agents

- Project: Fabric mod (Gradle + `fabric-loom`). Primary source roots:
  - Server: `src/main/java/com/teufel/statusmod/`
  - Client: `src/client/java/com/teufel/statusmod/`
  - Resources & mixins: `src/main/resources/` and `src/client/resources/`

**Entrypoints**
- `src/main/java/com/teufel/statusmod/StatusMod.java`: server-side `ModInitializer`. Owns `SettingsStorage` and registers commands (see `onInitialize`).
- `src/client/java/com/teufel/statusmod/StatusModClient.java`: client-side `ClientModInitializer` for rendering/client hooks.

**Commands & patterns**
- Commands live in `src/main/java/com/teufel/statusmod/command/` and use Brigadier.
- Typical argument patterns: `StringArgumentType.greedyString()` for multi-word statuses, `word()` for single tokens.
- Team naming: teams are created as `status_<first8OfUuid>`; scoreboard updates use `scoreboard.addPlayerToTeam(entityName, team)`.
- Style/status text: `com.teufel.statusmod.util.ColorMapper` → returns `Formatting` used with `Text.styled(...)`.

**Storage**
- `SettingsStorage` persists to `config/statusmod/players.json` (Gson). Writes are synchronous and saved on each change; shutdown hook flushes file.
- When adding fields to `PlayerSettings`, update `PlayerSettings.java`, `SettingsStorage.java`, and any JSON readers — read missing fields with defaults to avoid migration breakage.

**Server vs Client**
- Keep server logic under `src/main/java/...` and client-only code under `src/client/java/...`.
- Register server commands in `StatusMod.onInitialize()` (use `CommandRegistrationCallback.EVENT`).

**Build / run (Windows PowerShell)**
- Build JAR: `.`\`gradlew.bat build`
- If tooling errors appear (Gradle / fabric-loom vs JDK version), prefer running the wrapper under JDK21 (set `org.gradle.java.home` in `gradle.properties` or set `$env:JAVA_HOME` for the terminal). `build.gradle` may declare a higher toolchain; Gradle itself is more stable when run under JDK21 for Fabric tooling.

**Files to reference when editing**
- `build.gradle` — plugin versions, toolchain, resource processing.
- `src/main/java/com/teufel/statusmod/StatusMod.java` — command registration and storage access.
- `src/main/java/com/teufel/statusmod/command/StatusCommand.java` — scoreboard/team prefix & suffix application.
- `src/main/java/com/teufel/statusmod/storage/SettingsStorage.java` & `PlayerSettings.java` — JSON schema and lifecycle.

**Quick examples**
- Persist settings: `StatusMod.storage.forPlayer(uuid)`, modify fields, then `StatusMod.storage.put(uuid, settings)`.
- Create team name: `String teamName = "status_" + uuid.toString().replaceAll("-","").substring(0,8);`
- Set colored prefix: see `StatusCommand.setStatus(...)` — `Text.of(...).styled(s -> s.withColor(ColorMapper.get(key)))`.

**Conventions & gotchas**
- Responses currently contain hard-coded German text — keep localization in-place unless intentionally changing language.
- Many command handlers catch `Exception` and `e.printStackTrace()`; follow existing error-handling conventions for consistency.
- Storage has no automatic migration — handle schema changes defensively.

If you want, I can expand this into a checklist for adding new commands or for a storage migration guide. Reply with any areas you want expanded or any local environment details (JDK path) and I'll iterate.
```
