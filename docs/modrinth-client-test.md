# Client-Tests in der Modrinth App (manuell)

Die Matrix (`test-mod-matrix.ps1`) testet per Default nur Server.
Clients laufen manuell hier – pro Loader/MC-Kombi einmal.

## Setup pro Kombi

1. **Modrinth App:** Instanz anlegen mit passendem Loader + MC-Version
   (Fabric/Forge/NeoForge/Quilt x 1.21.11 / 26.x).
2. **Mods-Ordner der Instanz:**
   - Für **Singleplayer-Tests**: Mod-JAR aus `dist/multiversion/...` dazu
     (der Mod ist server-side und läuft auf dem integrierten Server mit).
   - Für **Server-Tests**: Vanilla-Client reicht – nichts installieren
     (außer Fabric API nur wenn die Instanz sie verlangt).
   - Fabric/Quilt: passende **Fabric API** (MC-Version beachten) dazu.
3. **Server:** Nitrado oder lokaler Testserver mit der passenden JAR,
   `online-mode=false` zum schnellen Joinen (nur Testserver!).

## Checkliste pro Sitzung

- [ ] Join ohne Fehler, Log ohne Exceptions
- [ ] `/status` → Menü öffnet, Preset klickbar, Weiter/Löschen ok
- [ ] `/status list` → alle online mit Farbe
- [ ] Tab-Liste (Java) + Nametags über Köpfen (2. Account nötig)
- [ ] Falls an: Bossbar/Actionbar/Sidebar sichtbar
- [ ] Bedrock (falls Geyser läuft): `/status list`, Bossbar, Köpfe

## Hinweise

- Der Mod braucht clientseitig **nichts** – alles außer Singleplayer
  geht mit Vanilla-Client.
- Fehler zuerst im Server-Log suchen (`logs/latest.log`), dann Client-Log.
- G-Keybind gibt es nicht mehr (Mod ist server-only) – `/status` nutzen.
