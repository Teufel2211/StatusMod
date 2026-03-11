# Local Release System

GitHub Actions workflows were removed from this repository.
Build and publish are now fully local via scripts in `scripts/local/`.

## Commands

```powershell
./scripts/local/run-workflow-local.ps1 -Workflow build
./scripts/local/run-workflow-local.ps1 -Workflow publish -DryRun
./scripts/local/run-workflow-local.ps1 -Workflow build-multi-version -DryRun
./scripts/local/run-workflow-local.ps1 -Workflow publish-multi-version -ReleaseType release
```

## Multi-Version Output Naming

Every built jar uses:

`statusmod-<modversion>-<modloader>-<minecraftversion>.jar`

Example:

`statusmod-1.2.9-fabric-1.21.11.jar`

## Upload Behavior

`publish-multi-version`:

1. Builds all configured versions first.
2. Uploads every built jar to Modrinth and CurseForge.
3. Uses the minecraft version from the filename for platform metadata.
4. Warns if `fabric.mod.json` has a different minecraft value than the filename.

Required environment variables:

- `MODRINTH_PROJECT_ID`
- `MODRINTH_TOKEN`
- `CURSEFORGE_PROJECT_ID`
- `CURSEFORGE_API_KEY` (or `CURSEFORGE_TOKEN`)

Optional local file:

- `scripts/local/.release.env`
  - copy from `scripts/local/.release.env.example`
  - loaded automatically by `run-workflow-local.ps1`
