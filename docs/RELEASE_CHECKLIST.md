# Release-Checkliste 1.4.0

Abhaken der Reihe nach. Alles auf `development` vorbereiten, erst ganz am Ende nach `main` mergen (CI feuert nur dort).

## 1. Code fertig

- [x] Alle Features/Fixes auf `development` committet, Baum sauber (`git status` leer) — verifiziert 2026-10-11
- [x] `version.txt` = `1.4.0` — verifiziert 2026-10-11
- [ ] `CHANGELOG.md` → `## Unreleased` in `## 1.4.0` umbenennen, Datum prüfen (erst beim Release!)

## 2. Builds

- [x] `.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge,quilt -ContinueOnError:$false` — 2026-10-11 alle ok
- [x] `.\scripts\ci\verify-jars.ps1` → 24/24 OK — verifiziert 2026-10-11
- [x] `dist/multiversion` enthält 24 × `Statusmod-1.4.0-<loader>-<mc>.jar` — verifiziert 2026-10-11

## 3. Tests

- [x] `.\scripts\ci\test-mod-matrix.ps1` (Server-Boot grün, mind. fabric × 1.21.11 + 26.2) — 2026-10-11: 2/2 PASS
- [x] Ingame-Foltertest Singleplayer (Klick/Shift/Q/Ziffern/F/Drag + ESC, Inventar prüfen) — **braucht dich**
- [x] Dashboard-Build grün: `npm run build --prefix dashboard` — verifiziert 2026-10-11

## 4. Tokens & Secrets

- [ ] `.\scripts\ci\check-release-tokens.ps1` → Exit 0 (Stand 2026-10-11: 3 FAILs — **brauchst du**: `MODRINTH_PROJECT_ID`, `CURSEFORGE_TOKEN` neu von https://legacy.curseforge.com/account/api-tokens, `CURSEFORGE_PROJECT_ID`)
- [ ] CI-Secrets prüfen (GitHub → Settings → Secrets → Actions): `MODRINTH_TOKEN`, `MODRINTH_PROJECT_ID`, `CURSEFORGE_TOKEN`, `CURSEFORGE_PROJECT_ID` — **brauchst du**
- [x] Keine *neuen* Secrets im Diff (Scan 2026-10-11: nur alte rotierte Defaults aus Aug-Commits + Suchbeispiel in dieser Datei) — sauber

## 5. Dashboard live

- [ ] `vercel deploy --prod` — **brauchst du** (CLI nicht eingeloggt)
- [ ] `/api/health` → `healthy`, `previous_secret_active: false` — nach Deploy prüfen
- [ ] Login + Owner-Login (`/fleet owner-code` → `/admin-dashboard`) einmal durchspielen — **brauchst du**
- [ ] Fleet-Pull auf Testserver prüfen (`/fleet status` → Pull ok) — **brauchst du**

## 6. Offenlegung (Modrinth, vor Upload!)

- [ ] Projektseite: Icon aktuell, Beschreibung aktuell — **brauchst du**
- [ ] Disclosure **"Contains AI-generated content"** (Code + Text) gesetzt — **brauchst du**
- [ ] Disclosure **"Contains telemetry"** gesetzt (Sync ist drin seit 1.4.0!) — **brauchst du**
- [ ] README/Modrinth: kein "No telemetry"-Versprechen mehr — **brauchst du**

## 7. Merge + Publish

- [ ] `development` → `main` mergen (kein Push ohne Go!)
- [ ] CI-Workflow grün (Build + Verify + Publish)
- [ ] Modrinth: 20 Uploads sichtbar (24 JARs, Duplikate gruppiert), Version `1.4.0`
- [ ] CurseForge: Dateien da, Freigabe abwarten
- [ ] GitHub Release angelegt (Changelog aus Git-Log seit letztem Tag)

## 7b. Neue Fixes seit Checklisten-Start (alle verifiziert 2026-10-11)

- [x] /status version antwortet (greedy-Arg frass ersion/history/list/clear als Statustext; Literale stehen jetzt zuerst)
- [x] Private Antworten kommen auf Remap-Runtimes an (direkter sendSystemMessage-Fallback in CommandUtil)
- [x] 26.3-Warnung: Konsole immer + Admin-Join-Ping + /status version-Zeile (Texte gekuerzt, User-verifiziert ingame)
- [x] BUILD-Tag aktuell: edrock263 (/status version meldet ihn)
- [x] Modrinth-Profile: 24/24 gesynct + byte-identisch (inkl. 4x 26.3 mit Warnung)

## 8. Danach

- [ ] Testserver + Nitrado auf 1.4.0-JARs aktualisieren + Neustart
- [ ] Modrinth-Profile syncen (`scripts/local/sync-modrinth-profiles.ps1`)
- [ ] Diese Datei fürs nächste Release zurücksetzen (Haken raus)
