# Bedrock-Support (Geyser + Floodgate)

StatusMod erkennt Bedrock-Spieler automatisch, sobald **Floodgate** auf dem
Server installiert ist. Alle UUID-basierten Features (Status, Sync, Mutes,
Blocks, Avatare, Presets) funktionieren unveraendert, weil Floodgate
Bedrock-Spielern stabile Java-UUIDs gibt.

## Benoetigte Mods (Server-seitig, zusätzlich zu StatusMod + Fabric API)

| MC-Version | Geyser-Fabric | Floodgate-Fabric | Stand |
|------------|---------------|------------------|---------|
| 26.3 | 2.11.3-b1249 (`download.geysermc.org/.../builds/1249/downloads/fabric`) | 2.2.7-b69 (Modrinth) | ⏳ Geyser verlangt noch Java `~26.2` (Stand 06.10.2026) – sobald Geyser 26.3 als Java-Version traegt, testen |
| 1.21.11 | 2.9.6-b1133 (Modrinth) | 2.2.6-b60 (Modrinth) | ✅ verifiziert: Server-Done, StatusMod-Init, Floodgate-Erkennung, Geyser UDP 19132, kein Crash |

Einfach beide JARs in den `mods/`-Ordner legen und den Server starten.
Beim ersten Start erzeugen Geyser/Floodgate ihre Configs (u.a. Floodgate-Key).

## Netzwerk & Server-Config

- **UDP-Port freigeben:** Bedrock verbindet per UDP, Standard-Port **19132**
  (Geyser-Config `bedrock.port`). TCP reicht NICHT (das ist nur Java).
- **online-mode:** Der Server kann auf `online-mode=true` bleiben (empfohlen).
  Floodgate laesst Bedrock-Spieler ueber Xbox-Auth herein, Java-Spieler
  werden normal ueber Mojang verifiziert.
- **Namen:** Floodgate kann Bedrock-Namen ein Prefix geben
  (`username-prefix` in der Floodgate-Config). Der Sync uebernimmt den Namen
  so, wie der Server ihn meldet.

## Was StatusMod bei Bedrock-Spielern anders macht

- **Erkennung:** `BedrockUtil` prueft per Floodgate-API (Reflection, keine
  harte Abhaengigkeit), ob eine UUID zu einem Bedrock-Spieler gehoert.
  Ohne Floodgate ist das immer `false` – der Mod laeuft unveraendert.
- **Spielerkoepfe im Admin-GUI:** Bedrock-Skins sind keine Mojang-Texturen,
  daher zeigen Bedrock-Koepfe das Steve-Standardgesicht. Betroffene Heads
  tragen die Lore-Zeile **"Bedrock-Spieler"** (gold).
- **Tipp:** Mit `/status avatar <Name|URL|off>` kann jeder Spieler (auch
  Bedrock) seinen Dashboard-Kopf selbst setzen, z.B. auf einen
  Premium-Namen oder eine eigene Skin-PNG-URL.
- **Dashboard:** Avatare/Heads dort nutzen dieselbe Avatar-Logik wie bei
  Java-Spielern (Name -> Mojang-Heads, URL -> Face-Crop, sonst Fallback).
- **Commands:** `/status`, `/status gui`, `/status avatar` usw. funktionieren
  fuer Bedrock-Spieler ueber den Chat wie bei Java (Berechtigungen wie
  gehabt, z.B. OP-Level oder LuckPerms).
- **Server-Log:** Bei installiertem Floodgate meldet StatusMod beim Start
  `Floodgate detected - Bedrock support enabled.`

## Grenzen (ehrlich)

- **Kein Bedrock-Client zum Testen:** Server-Boot mit Geyser+Floodgate ist
  verifiziert (kein Crash, beide Mods initialisieren). Ein echter
  Bedrock-Join (Handy/Konsole/Windows-Edition) muss ingame getestet werden:
  `/status hallo`, Farbe setzen, `/status gui` oeffnen, Avatar setzen.
- **GUI auf Bedrock:** Geyser uebersetzt Container-Menues in Bedrock-Formen.
  Klick-Aktionen (Links = Status, Shift = Farbe) koennen sich auf Bedrock
  anders anfuehlen – bitte ingame gegenpruefen.
- **Tablist/Scoreboard:** Status-Texte laufen ueber Scoreboard-Teams; wie
  viel davon Bedrock anzeigt, haengt von Geyser ab.
- **Geyser-Fabric folgt nur der neuesten MC-Version.** Fuer aeltere
  MC-Versionen (hier: 1.21.11) den passenden alten Geyser-Build aus der
  Tabelle oben nehmen.
