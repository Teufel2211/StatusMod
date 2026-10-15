# Security Policy

## Wer darf rein (Owner-Wunsch 2026-10-15: keine PR-Pflicht fuer eigene Aenderungen)

* Einziger Maintainer: **@Teufel2211**. Er pusht direkt auf
  `development` und mergt nach `main` — **ohne Pull Request**.
* Externe Beitraege nur als **Pull Request vom Fork** (kein direkter Push,
  keine Schreibrechte): Merge erst nach **Owner-Review + gruenem CI**.
* Branch-Schutz auf `main` + `development` (kein Force-Push, kein Loeschen,
  Admins eingeschlossen) — aber **keine PR-Pflicht**, damit der Owner
  arbeiten kann. Einrichten/pruefen:
  `.\scripts\ci\protect-branches.ps1` (Dry-Run) bzw. `-Apply`.

## Branch Protection einrichten (einmalig, eigene Shell)

```powershell
# Nur anzeigen, was gesetzt wuerde:
.\scripts\ci\protect-branches.ps1
# Wirklich setzen (braucht Repo-Admin = Owner):
.\scripts\ci\protect-branches.ps1 -Apply
```

GitHub-UI-Alternative: Repo Settings → Branches → Add rule fuer `main`
und `development`: **ohne** `Require a pull request before merging`,
dafuer `Do not allow bypassing the above settings`, `Restrict deletions`,
`Block force pushes`.

## Melden von Luecken

* Bitte **kein oeffentliches Issue** fuer Sicherheitsluecken.
* Melden via **GitHub → Security → Report a vulnerability**
  (Private Vulnerability Reporting). Antwortziel: 7 Tage.

## Automatische Schutzmechanismen (laufen im CI)

| Mechanismus | Wo | Was |
|---|---|---|
| Secret-Scan | `supply-chain.yml` | Gitleaks ueber jeden Push/PR; Build bricht bei Treffer ab |
| Dependency-Review | `supply-chain.yml` | Blockt PRs mit bekannten CVEs in Abhaengigkeiten (nur bei PRs aktiv) |
| Least-Privilege-Token | `build.yml` | `GITHUB_TOKEN` nur `contents:read` (Publish-Job: `contents:write`), Checkouts ohne Credential-Persistenz |
| Release-Pruefsummen | `build.yml` Publish-Job | `SHA256SUMS.txt` wird erzeugt und ans GitHub Release gehaengt |
| JAR-Verifikation | `verify-jars.ps1` | Dateinamen-, Versions- und Metadaten-Check vor jedem Publish |

## Automatik-Regel (Owner-Wunsch 2026-10-15)

Es werden **keine automatischen Update-Pull-Requests** geoeffnet
(kein Dependabot, keine anderen Bots). Updates von Actions/Gradle macht
der Owner manuell. Versehentlich geoeffnete Bot-PRs werden kommentarlos
geschlossen + Branch geloescht.

## Release-Regeln

* Publish nur ab `main`, nur bei `version.txt`-Aenderung (oder manuellem
  `force_publish` durch den Owner). CurseForge ist Pflicht: Fehler dort
  brechen den Publish ab statt still zu skippen.
* Secrets (`MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`, …) nur als
  GitHub-Actions-Secrets. Niemals in Repo, Logs oder Chat (nur Masken
  wie `abcd...wxyz`, s. `scripts/ci/check-release-tokens.ps1`).
* Der alte Workflow `fabric-release.yml` feuert nur noch manuell
  (`workflow_dispatch`); Tag-Pushes loesen keinen Build mehr aus.

## Bekannter Historien-Treffer (kein Handlungsbedarf)

Blob `c2fd862…` (`ModConfig.java`, Aug 2026) enthaelt einen alten,
**rotierten** `setupSecret`-Default. Schaden 0, kein Rewrite der Historie.
Bei Secret-Scans als bekannt ueberspringen.
