Publish workflow README

This repository includes a build+publish GitHub Actions workflow at `.github/workflows/publish.yml`.

Quick summary

- The workflow does two jobs:
  1. `build` — checks out the repo, sets up Java 21, runs `./gradlew clean build`, and uploads the produced JAR as an artifact.
  2. `publish` — downloads the artifact, creates a Git tag `v<version>` (from `version.txt`) if missing, creates a GitHub Release, and uploads the JAR to Modrinth & CurseForge using `mc-publish`.
- The workflow prefers to use the built-in `GITHUB_TOKEN` to push tags and create releases. If repository or organization policies prevent that, it supports a fallback personal access token (`PUBLISH_PAT`).

Required repository secrets

- `MODRINTH_TOKEN` — Modrinth API token for the project (used by `mc-publish`).
- `MODRINTH_PROJECT_ID` — Modrinth project id (slug or id as required by the action).
- `CURSEFORGE_TOKEN` — CurseForge API key (or `CURSEFORGE_API_KEY` depending on the action variant).
- `CURSEFORGE_PROJECT_ID` — CurseForge project id.
- `PUBLISH_PAT` (optional) — Personal Access Token with repo write scope. Use this only if the default `GITHUB_TOKEN` is blocked by repository/org policy and the workflow cannot push tags. See below for creating a PAT.

Repository settings to check

1. Actions permissions (Repository → Settings → Actions → General):
   - Ensure workflow permissions allow workflows to write repository contents. The recommended setting is "Read and write permissions" for workflows. If this is disabled by an org policy, provide a PAT via `PUBLISH_PAT`.

How the tag & release flow works

- The workflow reads the version from the file `version.txt` in the repository root. It expects a simple string like `1.0.6`.
- The `publish` job computes the tag name `v<version>` (for example, `v1.0.6`). If the tag already exists on the remote, the workflow skips creating it.
- The workflow will attempt to push the tag using `GITHUB_TOKEN`. If that token lacks write permission, provide a `PUBLISH_PAT` secret (see next section).

Create a Personal Access Token (PAT)

If you need to create a PAT to allow the workflow to push tags and create releases:

1. Go to https://github.com/settings/tokens (or Settings → Developer settings → Personal access tokens).
2. Click "Generate new token" (classic) or "Generate new token (classic)" if your organization requires classic tokens.
3. For a classic token: select the `repo` scope (this includes `repo:status`, `repo_deployment`, `public_repo`, `repo:invite`, and `security_events`).
   - For fine-grained tokens, grant access to the repository and enable the ability to `Contents: Read & write` and `Repository metadata` as needed.
4. Generate the token and copy it.
5. In your repository: Settings → Secrets and variables → Actions → New repository secret. Name it `PUBLISH_PAT` and paste the token.

Triggering the workflow

- Push to `main` or use the Actions UI "Run workflow" (workflow_dispatch). To test quickly, you can push an empty commit:

```powershell
git commit --allow-empty -m "ci: trigger publish workflow test"
git push origin main
```

Notes and troubleshooting

- If the publish job fails with a 403 when pushing tags, first check repository Actions permissions. If the repo is locked by org policy, `GITHUB_TOKEN` will not be able to push and you must use `PUBLISH_PAT`.
- If the release action reports "GitHub Releases requires a tag", the tag creation step is out of order; the workflow already creates the tag before the release step, so this normally shouldn't happen. If you hit it, double-check the run logs and send the failing step output here for diagnosis.
- The workflow will skip publishing if it detects the run actor is the GitHub Actions bot (to avoid publish loops triggered by the bump workflow pushing tags/commits).

Questions or changes

If you want me to:
- Add a short `CONTRIBUTING.md` snippet describing how to add the secrets in the repository settings, or
- Add an alternate publish path (e.g., only create a release when a tag is pushed manually),

tell me which and I will add that file and wire it into the repo.