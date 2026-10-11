# Release-Checkliste 1.4.0

Abhaken der Reihe nach. Alles auf `development` vorbereiten, erst ganz am Ende nach `main` mergen (CI feuert nur dort).

## 1. Code fertig

- [ ] Alle Features/Fixes auf `development` committet, Baum sauber (`git status` leer)
- [ ] `version.txt` = `1.4.0`
- [ ] `CHANGELOG.md` → `## Unreleased` in `## 1.4.0` umbenennen, Datum prüfen

## 2. Builds

- [ ] `.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge,quilt -ContinueOnError:$false`
- [ ] `.\scripts\ci\verify-jars.ps1` → 24/24 OK
- [ ] `dist/multiversion` enthält 24 × `Statusmod-1.4.0-<loader>-<mc>.jar`

## 3. Tests

- [ ] `.\scripts\ci\test-mod-matrix.ps1` (Server-Boot grün, mind. fabric × 1.21.11 + 26.2)
- [ ] Ingame-Foltertest Singleplayer (Klick/Shift/Q/Ziffern/F/Drag + ESC, Inventar prüfen)
- [ ] Dashboard-Build grün: `npm run build --prefix dashboard`

## 4. Tokens & Secrets

- [ ] `.\scripts\ci\check-release-tokens.ps1` → Exit 0 (keine FAILs)
- [ ] Falls FAILs: `MODRINTH_PROJECT_ID` setzen; CurseForge-Token neu von https://legacy.curseforge.com/account/api-tokens
- [ ] CI-Secrets prüfen (GitHub → Settings → Secrets → Actions): `MODRINTH_TOKEN`, `MODRINTH_PROJECT_ID`, `CURSEFORGE_TOKEN`, `CURSEFORGE_PROJECT_ID`
- [ ] Keine Secrets im Diff: `git log main..development -p | Select-String "sm_|AHYt|mrp_|sbp_"` → leer

## 5. Dashboard live

- [ ] `vercel deploy --prod` (im `dashboard/`-Ordner, oder Preview promoten)
- [ ] `/api/health` → `healthy`, `previous_secret_active: false`
- [ ] Login + Owner-Login (`/fleet owner-code` → `/admin-dashboard`) einmal durchspielen
- [ ] Fleet-Pull auf Testserver prüfen (`/fleet status` → Pull ok)

## 6. Offenlegung (Modrinth, vor Upload!)

- [ ] Projektseite: Icon aktuell, Beschreibung aktuell
- [ ] Disclosure **"Contains AI-generated content"** (Code + Text) gesetzt
- [ ] Disclosure **"Contains telemetry"** gesetzt (Sync ist drin seit 1.4.0!) — Text: UUID, Username, Status/Farbe/Settings, Mute/Block über HTTPS an `dashboardUrl`, opt-in
- [ ] README/Modrinth: kein "No telemetry"-Versprechen mehr

## 7. Merge + Publish

- [ ] `development` → `main` mergen (kein Push ohne Go!)
- [ ] CI-Workflow grün (Build + Verify + Publish)
- [ ] Modrinth: 20 Uploads sichtbar (24 JARs, Duplikate gruppiert), Version `1.4.0`
- [ ] CurseForge: Dateien da, Freigabe abwarten
- [ ] GitHub Release angelegt (Changelog aus Git-Log seit letztem Tag)

## 8. Danach

- [ ] Testserver + Nitrado auf 1.4.0-JARs aktualisieren + Neustart
- [ ] Modrinth-Profile syncen (`scripts/local/sync-modrinth-profiles.ps1`)
- [ ] Diese Datei fürs nächste Release zurücksetzen (Haken raus)
