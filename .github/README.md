# CI/CD Release Guide

This repository uses hardened GitHub Actions workflows for build and publishing.

## Workflows

- `.github/workflows/build.yml`
  - Runs on push/PR
  - Validates Gradle wrapper
  - Retries Gradle build up to 3 times
  - Uploads build artifacts

- `.github/workflows/publish.yml`
  - Runs on push to `main` or manual dispatch
  - Builds with retry, verifies JAR metadata and checksum
  - Enforces semver progression (or allows safe republish when version equals latest tag)
  - Generates release notes from commits since latest tag
  - Creates/pushes `v<version>` tag, creates GitHub Release, uploads JAR + checksum
  - Publishes to Modrinth + CurseForge via `mc-publish`
  - Retries `mc-publish` once
  - Falls back to Python API uploader if both attempts fail

- `.github/workflows/publish-multi-version.yml`
  - Manual only (`workflow_dispatch`)
  - Builds multiple Minecraft targets with per-version retry
  - Restores `gradle.properties` safely even on failure
  - Publishes all generated jars

## Manual Publish Inputs (`publish.yml`)

- `release_type`: `release`, `beta`, or `alpha`
- `game_versions`: optional list (comma/newline separated)
- `dry_run`: if `true`, performs full validation/build but skips tagging and uploads

## Required Secrets

- `MODRINTH_TOKEN`
- `MODRINTH_PROJECT_ID`
- `CURSEFORGE_TOKEN`
- `CURSEFORGE_PROJECT_ID`
- `PUBLISH_PAT` (optional, used when `GITHUB_TOKEN` cannot push tags)

## Fallback Uploader

Fallback script: `.github/scripts/upload_release.py`

Used only if `mc-publish` fails twice. It supports:

- `GAME_VERSIONS`
- `RELEASE_TYPE`
- `VERSION`
- `RELEASE_CHANGELOG` (optional)

## Operational Notes

- Keep `version.txt` in semver format (`x.y.z` with optional suffix).
- Bump `version.txt` before normal releases.
- Use manual `dry_run=true` before high-impact releases to validate pipeline health.
