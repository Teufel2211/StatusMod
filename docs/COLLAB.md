# Collaboration Workflow (Two Machines)

This repo is designed for a split workflow:
- **Build machine (yours):** runs builds/tests, handles packaging and logs.
- **Dev machine (friend):** writes code, updates handoff notes, pushes changes.

## Sync Options
1. **Git (recommended)**
   - Dev machine: `git pull`, make changes, update `docs/ASSISTANT_HANDOFF.md`, `docs/ASSISTANT_STATE.json`, then `git push`.
   - Build machine: `git pull`, run builds, update handoff + build logs, then `git push`.

2. **Shared folder / ZIP**
   - Dev machine sends only source changes + `docs/ASSISTANT_*` files.
   - Build machine applies changes and runs builds.

## Shared Memory Files (must be kept updated)
- `docs/ASSISTANT_HANDOFF.md` — human‑readable summary.
- `docs/ASSISTANT_STATE.json` — structured state for quick parsing.

## Communication Rules
- Any decision that affects builds or dependencies must be written to the handoff.
- Always append new build logs to `dist/local/` and reference them in the handoff.
- Keep changes mirrored across loader folders if they affect shared behavior.

## Suggested Checklist
- Dev machine: update code + handoff
- Build machine: run builds + update handoff
- Both: keep `docs/ASSISTANT_STATE.json` consistent with the handoff
