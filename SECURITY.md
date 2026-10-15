# Security Policy

## Wer darf rein

* Einziger Maintainer: **@Teufel2211**. Nur er kann mergen und releasen.
* Externe Beitraege nur als **Pull Request vom Fork** (kein direkter Push):
  Merge erst nach **Owner-Review + gruenem CI** (`Build & Verify`).
* `main` und `development` sind per Branch Protection geschuetzt
  (einmalig einrichten, s. unten): kein Force-Push, kein Loeschen,
  kein direkter Push ohne PR, Admins eingeschlossen (`enforce_admins`).

## Branch Protection einrichten (einmalig, eigene Shell)

```powershell
# Nur anzeigen, was gesetzt wuerde:
.\scripts\ci\protect-branches.ps1
# Wirklich setzen (braucht Repo-Admin = Owner):
.\scripts\ci\protect-branches.ps1 -Apply
```

GitHub-UI-Alternative: Repo Settings → Branches → Add rule fuer `main`
und `development`: `Require a pull request before merging` (1 Approval,
`Require review from Code Owners`), `Require status checks to pass`,
`Do not allow bypassing the above settings`, `Restrict deletions`,
`Block force pushes`.

## Melden von Luecken

* Bitte **kein oeffentliches Issue** fuer Sicherheitsluecken.
* Melden via **GitHub → Security → Report a vulnerability**
  (Private Vulnerability Reporting). Antwortziel: 7 Tage.

## Automatische Schutzmechanismen (laufen im CI)

| Mechanismus | Wo | Was |
|---|---|---|
| Secret-Scan | `supply-chain.yml` | Gitleaks ueber jeden Push/PR; Build bricht bei Treffer ab |
| Dependency-Review | `supply-chain.yml` | Blockt PRs mit bekannten CVEs in Abhaengigkeiten |
| Dependency-Updates | `dependabot.yml` | Woechentliche PRs fuer GitHub Actions + Gradle |
| Least-Privilege-Token | `build.yml` | `GITHUB_TOKEN` nur `contents:read` (Publish-Job: `contents:write`), Checkouts ohne Credential-Persistenz |
| Release-Pruefsummen | `build.yml` Publish-Job | `SHA256SUMS.txt` wird erzeugt und ans GitHub Release gehaengt |
| JAR-Verifikation | `verify-jars.ps1` | Dateinamen-, Versions- und Metadaten-Check vor jedem Publish |

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
