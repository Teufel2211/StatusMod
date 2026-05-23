# Assistant Handoff

Last Update: 2026-03-20

## Current Goals
- Keep multiloader builds green (fabric, quilt, forge, neoforge, datapack).
- Maintain shared code across loaders; mirror fixes to all loader folders.
- Keep admin commands usable from server console.

## Recent Changes
- Console sources now count as admin for PermissionUtil (all loaders + src_legacy).
- Quilt switched to Quilted Fabric API; QSL removed from build deps.
- Quilt build verified OK (log: dist/local/quilt-build-7.log).
- Added quilted_fabric_api_version and updated fabric_api_version.

## Build Status
- Multiversion build still long; last attempt timed out (log: dist/local/run-workflow-9.log).
- Previous multiversion run had Fabric Loom cache errors; caches cleaned.

## Open Issues
- Multiversion full run not completed due to timeouts.
- NeoForge may still have issues (ATS parsing seen earlier).

## Next Steps
- Re-run multiversion build with enough time or split by loaders.
- Check dist/multiversion/build-report.json after successful run.
