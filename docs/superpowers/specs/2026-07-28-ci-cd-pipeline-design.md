# CI/CD Pipeline Design

## Overview

Automated build, verification, and publishing pipeline for StatusMod using GitHub Actions.
Triggered on every push to `main`. Publishing only occurs when `version.txt` changes.

## Trigger

```yaml
on:
  push:
    branches: [main]
```

Always builds + verifies. Conditionally publishes based on `version.txt` diff.

## Jobs

### 1. `build-and-verify`

**Runner:** `windows-latest` (multiversion script is PowerShell + needs Java 21 + 25)

**Steps:**

1. **Checkout** — `actions/checkout@v4` with `fetch-depth: 2` (needs previous commit for version diff)
2. **Setup Java 21** — `actions/setup-java@v4`, Java 21 (for MC < 26 builds)
3. **Setup Java 25** — `actions/setup-java@v4`, Java 25 (for MC >= 26 builds)
4. **Cache Gradle** — `actions/cache@v4` for `~/.gradle/caches` and `~/.gradle/wrapper` (keyed on hash of build files)
5. **Detect version change** — compare `version.txt` between HEAD and HEAD~1
6. **Run multiversion build** — `scripts/local/build-multiversion.ps1 -Loaders fabric,forge,neoforge -ContinueOnError:$false`
7. **Verify JARs** — `scripts/ci/verify-jars.ps1` checks every JAR's metadata matches `version.txt`
8. **Upload artifacts** — `actions/upload-artifact@v4` with all 15 JARs

### 2. `publish` (conditional)

**Needs:** `build-and-verify`
**If:** `version.txt` changed (detected in step 5 of build job)

**Steps:**

1. **Download artifacts** — `actions/download-artifact@v4`
2. **Publish to Modrinth** — Use `cloudnepal/modrinth-publish@v1` (or direct API call)
3. **Publish to CurseForge** — Use `curl` multipart POST to `https://minecraft.curseforge.com/api/projects/{id}/upload-file` with metadata JSON + file attachment

## Version Change Detection

```yaml
- name: Check version change
  id: version_check
  run: |
    $old = git show HEAD~1:version.txt 2>$null
    $new = Get-Content version.txt -Raw
    if ($old -ne $new) { echo "changed=true" >> $env:GITHUB_OUTPUT }
    else { echo "changed=false" >> $env:GITHUB_OUTPUT }
  shell: pwsh
```

## JAR Verification (`scripts/ci/verify-jars.ps1`)

For each output JAR in `dist/multiversion/`:

1. Extract metadata file (`fabric.mod.json`, `mods.toml`, or `neoforge.mods.toml`)
2. Parse `version` field
3. Compare against `version.txt`
4. Exit non-zero on any mismatch

```powershell
# Pseudo:
$expected = Get-Content version.txt -Raw | ForEach-Object { $_.Trim() }
Get-ChildItem dist/multiversion -Recurse -Filter *.jar | ForEach-Object {
    $version = [regex]::Match((jar xf ...), '"version":\s*"(\d[\w.]*)"').Groups[1].Value
    if ($version -ne $expected) { exit 1 }
}
```

## GitHub Secrets Required

| Secret | Purpose |
|--------|---------|
| `MODRINTH_TOKEN` | Modrinth API token for uploading files |
| `CURSEFORGE_TOKEN` | CurseForge API token (non‑expiring API key) |
| `MODRINTH_PROJECT_ID` | Modrinth project slug (e.g. `statusmod`) |
| `CURSEFORGE_PROJECT_ID` | CurseForge project numeric ID |

## Build Matrix

All 15 combinations built sequentially (same as local multiversion script):

| Loader | Minecraft Versions |
|--------|-------------------|
| Fabric | 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 |
| Forge | 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 |
| NeoForge | 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 |

## Caching Strategy

- **Gradle caches** (`~/.gradle/caches`, `~/.gradle/wrapper`): key = `gradle-{hash of **/*.gradle, **/gradle.properties, **/gradle-wrapper.properties}` + restore-keys with prefix fallback
- **Fabric API version list**: fetched fresh each run (small)

## Error Handling

- Build failure → workflow fails immediately (`ContinueOnError:$false`)
- Verification failure → workflow fails (broken JARs never published)
- Publish failure → workflow still succeeds for build+verify; publish error is visible in logs. User retries manually.
- No Gradle daemon (`--no-daemon`) to avoid daemon leaks on runners
