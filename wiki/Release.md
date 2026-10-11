# Release (maintainers)

Publishing targets: **Modrinth + CurseForge + GitHub Releases**. CurseForge is required since 1.4.0 — a missing token aborts the whole publish.

## 1. Token check (read-only, no publishing)

```powershell
.\scripts\ci\check-release-tokens.ps1
```

Checks Modrinth token + project, CurseForge token + project, `gh` login + repo access, Supabase reachability. Secrets are only shown masked (`mrp_…PlcP`). Exit 0 = ready, 1 = don't release.

Needed values:

| Variable | Where from |
|----------|-----------|
| `MODRINTH_TOKEN` | Modrinth account → API tokens |
| `MODRINTH_PROJECT_ID` | Modrinth project settings (also in CI secrets) |
| `CURSEFORGE_TOKEN` | https://legacy.curseforge.com/account/api-tokens (renew if 401) |
| `CURSEFORGE_PROJECT_ID` | CurseForge project settings (also in CI secrets) |

Locally export them as env vars; CI reads them from repo secrets.

## 2. Build + verify

```powershell
.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge,quilt -ContinueOnError:$false
.\scripts\ci\verify-jars.ps1   # 24/24 metadata check
```

## 3. Publish

```powershell
.\scripts\ci\publish-all.ps1
```

Behavior: byte-identical JARs upload once with merged game versions (CurseForge rejects duplicates); already-published versions are skipped; any CurseForge failure aborts with exit 1. The changelog is generated from git log since the last tag.

## 4. CI

`.github/workflows/build.yml`: builds, verifies and publishes on `main` push when `version.txt` changes, or manually via `workflow_dispatch` (`force_publish` + optional `loaders`/`mc_versions` filters).
