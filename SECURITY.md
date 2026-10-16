# Security Policy

## Wer darf rein (Owner-Wunsch 2026-10-15: keine PR-Pflicht für eigene Änderungen)

* Einziger Maintainer: **@Teufel2211**. Er pusht direkt auf
  `development` und mergt nach `main` — **ohne Pull Request**.
* Externe Beiträge nur als **Pull Request vom Fork** (kein direkter Push,
  keine Schreibrechte): Merge erst nach **Owner-Review + grünem CI**.
* Branch-Schutz auf `main` + `development` (kein Force-Push, kein Löschen,
  Admins eingeschlossen) — aber **keine PR-Pflicht**, damit der Owner
  arbeiten kann. Einrichten/prüfen:
  `.\scripts\ci\protect-branches.ps1` (Dry-Run) bzw. `-Apply`.

## Branch Protection einrichten (einmalig, eigene Shell)

```powershell
# Nur anzeigen, was gesetzt würde:
.\scripts\ci\protect-branches.ps1
# Wirklich setzen (braucht Repo-Admin = Owner):
.\scripts\ci\protect-branches.ps1 -Apply
```

GitHub-UI-Alternative: Repo Settings → Branches → Add rule für `main`
und `development`: **ohne** `Require a pull request before merging`,
dafür `Do not allow bypassing the above settings`, `Restrict deletions`,
`Block force pushes`.

## Melden von Lücken

* Bitte **kein öffentliches Issue** für Sicherheitslücken.
* Melden via **GitHub → Security → Report a vulnerability**
  (Private Vulnerability Reporting). Antwortziel: 7 Tage.

## Automatische Schutzmechanismen (laufen im CI)

| Mechanismus | Wo | Was |
|---|---|---|
| Secret-Scan | `supply-chain.yml` | Gitleaks über jeden Push/PR; Build bricht bei Treffer ab |
| Dependency-Review | `supply-chain.yml` | Blockt PRs mit bekannten CVEs in Abhängigkeiten (nur bei PRs aktiv) |
| Least-Privilege-Token | `build.yml` | `GITHUB_TOKEN` nur `contents:read` (Publish-Job: `contents:write`), Checkouts ohne Credential-Persistenz |
| Release-Prüfsummen | `build.yml` Publish-Job | `SHA256SUMS.txt` wird erzeugt und ans GitHub Release gehängt |
| JAR-Verifikation | `verify-jars.ps1` | Dateinamen-, Versions- und Metadaten-Check vor jedem Publish |

## Automatik-Regel (Owner-Wunsch 2026-10-15)

Es werden **keine automatischen Update-Pull-Requests** geöffnet
(kein Dependabot, keine anderen Bots). Updates von Actions/Gradle macht
der Owner manuell. Versehentlich geöffnete Bot-PRs werden kommentarlos
geschlossen + Branch gelöscht.

## Release-Regeln

* Publish nur ab `main`, nur bei `version.txt`-Änderung (oder manuellem
  `force_publish` durch den Owner). CurseForge ist Pflicht: Fehler dort
  brechen den Publish ab statt still zu skippen.
* Secrets (`MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, …) nur als
  GitHub-Actions-Secrets. Niemals in Repo, Logs oder Chat (nur Masken
  wie `abcd...wxyz`, s. `scripts/ci/check-release-tokens.ps1`).
* Der tote Workflow `fabric-release.yml` ist gelöscht (2026-10-16):
  er trug falsche Secret-Namen, einen toten Publish-Pfad und hat bei
  Pushes als Geister-Run gefailt. Releases laufen nur über `build.yml`.

## Bekannter Historien-Treffer (kein Handlungsbedarf)

Blob `c2fd862…` (`ModConfig.java`, Aug 2026) enthält einen alten,
**rotierten** `setupSecret`-Default. Schaden 0, kein Rewrite der Historie.
Bei Secret-Scans als bekannt überspringen.
