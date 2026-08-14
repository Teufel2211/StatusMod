# Runtime-Test-Gate Design

Datum: 2026-07-31
Status: Approved
Betrifft: `.github/workflows/build.yml`, neues `scripts/ci/test-runtime.ps1`

## Problem

Die CI baut und verifiziert nur Metadaten (Regex in `verify-jars.ps1`). Eine JAR kann also gebaut sein, korrekte `version`-Felder haben, aber trotzdem zur Laufzeit crashen (z.B. Loader-`provides`-Konflikt wie Quilt 0.29.2/fabric-api, UnsupportedClassVersion, fehlende API). Aktuell wird direkt nach dem Build released — ein Runtime-Crash wäre erst auf den Servern der Nutzer sichtbar.

## Ziel

**Jede der 20 JARs wird einzeln in Multiplayer UND Singleplayer zur Laufzeit getestet, bevor irgendetwas released wird.**

- Schlägt auch nur EINE JAR fehl → **keine JAR wird released** (Release-Gate).
- Der Test ist Teil der CI-Pipeline (GitHub Actions), kein lokales Tool.
- Test-Tiefe MP: Server startet komplett, Mod-Init im Log, `/modinfo`-Kommando über Server-Konsole → Antwort im Log.
- Test-Tiefe SP: Echter Client startet, Welt wird erstellt/geladen, Mod-Init im Log, keine Exceptions. **Kein** `/status`-Kommando im Chat (flaky auf CI) — siehe Entscheidung.

## JAR-Matrix (20 Einträge, 1 Matrix-Job pro JAR)

| Loader | MC-Versionen | Server-Setup | Test-Deps |
|--------|-------------|--------------|-----------|
| fabric | 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | `fabric-installer.jar server <mc> <loader> --dir` → Launcher-JAR | fabric-api (MC-passend) |
| forge | 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | `forge-installer.jar --installServer` → `run.bat` | keine (bundled) |
| neoforge | 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 | `neoforge-installer.jar --installServer` → `run.bat` | keine |
| quilt | 1.21.11 (alle 5 JARs byte-identisch, target 1.21.11) | `quilt-installer.jar server 1.21.11 0.30.0 --install-dir=... --download-server` → `quilt-server-launch.jar` | fabric-api 0.141.6+1.21.11 |

**Wichtig:** JARs gleichen Loaders sind innerhalb 26.x byte-identisch (nur fabric 3, forge 2, neoforge/quilt je 1 eindeutiges Binary für 20 Dateien) — ABER fabric/forge/neoforge-26.x-JARs laufen auf verschiedenen MC-Versionen. Deshalb wird trotzdem **jede der 20 JARs einzeln getestet** (gleiche JAR, andere Server-MC → eigene Matrix-Zeile).

**Quilt-Sonderfall (Mehrdeutigkeit aufgelöst):** Die 5 Quilt-JARs (26.1 … 26.2) sind byte-identisch UND laufen alle auf MC 1.21.11. Trotzdem erhält jede der 5 ein eigener Matrix-Job (User-Anforderung „jede JAR einzeln"). Die 5 Quilt-Matrix-Zeilen unterscheiden sich nur durch die zu testende JAR-Datei; die Test-Umgebung (MC 1.21.11 + Loader 0.30.0) ist für alle 5 identisch. Kein Fehler, nur leichte Redundanz — bewusst akzeptiert.

## Architektur

```
┌─────────────────────┐
│ build-and-verify    │  bestehend: Build + verify-jars.ps1 + upload 20 JARs
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ runtime-test        │  NEU: strategy.matrix = 20 Einträge, parallel
│  pro Job:           │
│  ├─ download JAR    │
│  ├─ test-runtime.ps1│  MP (Server + /modinfo) + SP (Client + Welt)
│  └─ upload result   │
└──────────┬──────────┘
           ▼
┌─────────────────────┐
│ publish             │  needs: build-and-verify + runtime-test
└─────────────────────┘
```

GitHub Actions `needs`-Mechanik ist das Gate: Fehlschlägt irgendein Matrix-Job, wird `runtime-test` als Ganzes rot → `publish` startet nie. Kein zusätzliches Prüf-Script nötig.

## `scripts/ci/test-runtime.ps1`

Pro Matrix-Job ein Aufruf. Parameter:

```powershell
.\test-runtime.ps1 `
  -JarPath <pfad zur mod-jar> `
  -Loader fabric|forge|neoforge|quilt `
  -McVersion 1.21.11|26.1|26.1.1|26.1.2|26.2 `
  -WorkDir <job-lokales verzeichnis> `
  -JavaHome <pfad zu jdk-25> `
  -FabricApiUrl <url zu passender fabric-api jar> `  # nur fabric/quilt
  -SkipSinglePlayer $false
```

### MP-Teil (Dedicated Server)

1. Server-Setup je Loader (Installer-JAR herunterladen, Installation ausführen, s.o. Matrix).
2. `eula.txt` = `eula=true`, `server.properties`: `online-mode=false`, `server-port=25565` (eigener Runner je Job → kein Port-Konflikt), `level-type=minecraft:flat` (schnelle Weltgenerierung).
3. Mod-JAR + Deps nach `mods/` kopieren.
4. Start mit **jdk-25** (`java -Xms1024M -Xmx2048M -jar <launcher> nogui`), stdout/stderr nach Log-Datei.
5. Warte auf `Done (` in Log (Timeout 120s).
6. Sende `modinfo` über die Server-Konsole (stdin-Pipe des Prozesses).
7. Prüfe im Log: `StatusMod — Informationen` (Assertion 1). Optional zusätzlich `status config show` → `StatusMod configuration:`.
8. Server stoppen (`stop` über stdin), Prozess-Beendigung abwarten (Timeout 30s, sonst Kill).

### SP-Teil (Echter Client + Welt)

1. Client-Setup je Loader:
   - fabric/quilt: `fabric-installer.jar client <mc> <loader> --dir` bzw. quilt-Installer-Client.
   - forge/neoforge: Installer mit `--installClient`, Client-Profile in `.minecraft/versions/`.
2. Asset-Index + Assets herunterladen (Mojang Manifest für `<mc>`), nach `.minecraft/assets/`.
3. Mod-JAR + Deps nach `mods/` kopieren (im Loader-Client-Verzeichnis).
4. Client starten mit quickPlay-Argument, um ohne GUI-Interaktion eine Welt zu laden:
   `java -Xmx2048M -cp <client-classpath> <main-class> --gameDir <dir> --assetsDir <dir/assets> --assetIndex <index> --username CI_Test --quickPlaySingleplayer <welt>`
   - Fallback, falls quickPlay die Welt nicht erstellt: vorab eine minimale Welt via `level.dat` + `level-type=flat` anlegen.
5. Warte auf Mod-Init-Logzeile (Assertion 2): `[StatusMod] Initializing on <loader>`.
6. Warte auf Welt-Load (Assertion 3): integrierter Server `Done (` im Client-Log.
7. Prüfe `ERROR`/`Exception`/`Caused by` im Client-Log → Fail (Assertion 4).
8. Prozess nach Ablauf-Praxis beenden (Timeout 180s, dann Kill).

### Resultat

- Schreibt `result.json` in `$WorkDir`: `{ jar, loader, mcVersion, mp: {passed, logPath}, sp: {passed, logPath}, passed }`.
- Exit-Code 0 = beide Teile bestanden, sonst 1.
- Bei jedem Fail: relevante Log-Zeilen (letzte 40 des Laufes) ins stdout, damit die Fehlersicht im GitHub-UI ohne Artefakt-Download möglich ist.
- Alle Downloads in `$WorkDir` (job-lokal, nicht Cachen über Jobs hinweg).

## `build.yml`-Änderungen

Neuer Job zwischen `build-and-verify` und `publish`:

```yaml
  runtime-test:
    needs: build-and-verify
    runs-on: windows-latest
    strategy:
      fail-fast: false        # alle Jobs laufen durch, keiner storniert den Rest
      matrix:
        include:
          - { loader: fabric,    mc: 1.21.11 }
          - { loader: fabric,    mc: 26.1 }
          # ... 20 Einträge insgesamt
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4        # Temurin 25 — nötig: alle JARs class file 69.0
        with: { distribution: temurin, java-version: '25' }
      - uses: actions/download-artifact@v4 # die 20 JARs (build-and-verify)
      - name: Resolve fabric-api
        run: ...                           # nur fabric/quilt; URL aus build.yml env oder Script-Lookup
      - name: Run runtime test
        run: powershell -File scripts/ci/test-runtime.ps1 -JarPath <match> -Loader ${{ matrix.loader }} ...
      - uses: actions/upload-artifact@v4   # result.json + Logs
        with: { name: runtime-test-${{ matrix.loader }}-${{ matrix.mc }}, path: ... }

  publish:
    needs: [build-and-verify, runtime-test]   # geändert: runtime-test als Pflicht
    if: ... # unverändert
```

- `fail-fast: false` ist Pflicht, sonst storniert ein Fehler die übrigen 19 Tests.
- JAR-Zuordnung: der Download enthält alle 20; der Job matcht per `dist/multiversion/<loader>/Statusmod-*-<loader>-<mc>.jar`.

## Zu klärende Punkte vor Umsetzung (in Plan auflösen)

1. **fabric-api-Versionen** je MC: 26.2 → `0.156.0+26.2` (verifiziert), 1.21.11 → `0.141.6+1.21.11` (verifiziert via Quilt-Test). Für 26.1/26.1.1/26.1.2 muss die passende Version nachgeschlagen werden (Modrinth) und in eine Versionstabelle (env in build.yml oder kleine JSON-Datei im Script).
2. **Installer-URLs**: fabric-installer (maven.fabricmc.net), forge/neoforge-installer (maven.neoforged.net/forge), quilt-installer 0.15.0 (maven.quiltmc.org). Exakte URLs im Plan.
3. **Loader-Versionen je MC**: Quelle ist `scripts/local/loader-versions.json` (bereits alle Versionen für 1.21.11 + 26.x vorhanden). Der Test-Job liest sie aus dem Checkout.
4. **forge/neoforge 26.x Server-Start**: `run.bat`/`run.sh` nutzt user_jvm_args.txt und eigene JARs — prüfen ob `--nogui` unterstützt wird, ggf. direkt `java ... @user_jvm_args.txt @libraries/net/minecraftforge/forge/...`-Variante.
5. **SP-Client ohne GUI**: GitHub-Windows-Runner hat Desktop-Session (RDP), Client startet mit Software-Rendering (LWJGL `-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true`). Muss im Plan als Risiko eingestuft und beim ersten Dry-Run lokal verifiziert werden.
6. **quickPlaySingleplayer**: exakte Arg-Syntax MC 1.21.11 vs 26.x; Fallback minimale Welt vorab anlegen.
7. **Test-Dauer**: pro Job ~2-5 Min (Server-Setup dominiert). 20 parallele Jobs = akzeptabel.
8. **`/modinfo`-Assertion ist Loader-agnostisch** — der Befehl ist in allen Loadern registriert (gemeinsamer Code). `/status config show` ist Admin-only (Konsole = immer Admin, `isConsoleSource` → true).

## Fehlerbehandlung

- Server startet nicht (Timeout `Done`) → MP-Fail, restlicher Job bricht ab, Exit 1.
- Mod-Init fehlt → Fail (Assertion 2 schlägt fehl).
- `/modinfo`-Antwort fehlt → MP-Fail.
- SP-Welt lädt nicht innerhalb 180s → SP-Fail.
- Jede Exception/Crash im Log → Fail.
- Alle Prozesse werden mit Kill-Fallback beendet (keine Zombie-Java-Prozesse auf dem Runner).
- Setup-Fehler (Download/Installer schlägt fehl) → Fail (kein stilles Skip).

## Erfolgskriterien

- 20/20 Matrix-Jobs grün auf einem Push mit gültiger `version.txt`-Änderung.
- Bewusster Negativtest: eine JAR mit kaputtem Runtime-Verhalten → der zugehörige Matrix-Job rot → `publish` läuft nicht. (Praktisch: einmalig per Dry-Run gegen eine lokal verdorbene Kopie verifiziert.)
- `publish` startet NUR wenn alle 20 Tests grün.
