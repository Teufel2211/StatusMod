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
.\gradlew.bat build --no-daemon
```

  - Alternative: Downgrade `fabric-loom` in `build.gradle` to a version compatible with your installed Gradle (not recommended unless necessary). Look up compatible versions in the Fabric Loom release notes.

If you run into "Unsupported class file major version 69" while Gradle is using Java 25, that indicates a tooling (Groovy/ASM) mismatch — the practical workaround is to run Gradle under Java 21 (or use the Gradle version bundled with the project wrapper that matches toolchain expectations). If you want, I can add an automated wrapper update or a note in `gradlew` invocation scripts.

If any of these references are stale or you want extra conventions (code style, tests, CI), tell me what to include and I will update this file.

VS Code / Workspace settings to pin Gradle's Java (JDK 21)
- Quick summary: the most reliable way to force Gradle to use a specific JDK for this project is to set `org.gradle.java.home` in the project's `gradle.properties` or to configure your workspace's Java runtime in VS Code. Below are two options you can use together.

1) Temporary (per-terminal) - PowerShell

```powershell
$env:JAVA_HOME = 'C:\\Path\\To\\jdk-21'
.\gradlew.bat clean build --no-daemon
```

2) Persistent for this workspace
- Add to project `gradle.properties` (workspace project root):

```
org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.0
```

- Or use the included workspace settings file `.vscode/settings.json` (example added). The workspace file sets the Java runtime used by VS Code's Java extension and the Gradle extension. Update the paths to match your installed JDK 21.

If you want, I can also update `gradle.properties` in this project to point to your JDK21 path if you provide it; otherwise the `.vscode/settings.json` + temporary PowerShell approach is usually enough.
