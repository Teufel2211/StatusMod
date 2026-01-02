# StatusMod - Build-Anleitung

## Neue Struktur (vereinfacht)

Statt komplexer Multi-Version-Skripte verwenden wir jetzt **per-Version `gradle.properties` Dateien**:

```
gradle.properties          (aktuelle Konfiguration)
gradle.properties.1.21.10  (Minecraft 1.21.10)
gradle.properties.1.20.4   (Minecraft 1.20.4)
gradle.properties.1.20.1   (Minecraft 1.20.1)
gradle.properties.1.19.4   (Minecraft 1.19.4)
build.ps1                  (Baue eine spezifische Version)
build_all.ps1              (Baue alle Versionen)
```

## Verwendung

### Einzelne Version bauen

```powershell
.\build.ps1 1.21.10
.\build.ps1 1.20.4
.\build.ps1 1.20.1
.\build.ps1 1.19.4
```

**Ausgabe:** `build/libs/statusmod-<version>.jar`

### Alle Versionen bauen

```powershell
.\build_all.ps1
```

Das erstellt:
- `build/libs/statusmod-1.21.10.jar`
- `build/libs/statusmod-1.20.4.jar`
- `build/libs/statusmod-1.20.1.jar`
- `build/libs/statusmod-1.19.4.jar`

## Wie es funktioniert

1. Das Skript `build.ps1` nimmt eine Version als Parameter entgegen
2. Es kopiert `gradle.properties.<version>` zu `gradle.properties`
3. Es führt `./gradlew.bat build` aus
4. Es benennt die erzeugte JAR-Datei in `statusmod-<version>.jar` um

## Neue Versionen hinzufügen

1. Erstelle eine neue Datei `gradle.properties.<version>`:
   ```properties
   minecraft_version=1.20.6
   yarn_mappings=1.20.6+build.1
   loader_version=0.16.9
   fabric_api_version=0.93.0+1.20.6
   org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-21.0.9.10-hotspot
   ```

2. Baue die Version:
   ```powershell
   .\build.ps1 1.20.6
   ```

Das war's!
