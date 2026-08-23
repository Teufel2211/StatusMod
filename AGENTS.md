# AGENTS.md — StatusMod

## Overview

StatusMod is a Minecraft mod that adds lightweight status tags for players.
Supports Fabric, Forge, NeoForge, and Quilt across MC 1.19 - 26.2.

## Build Commands

```powershell
# All loaders, all versions (20 combinations)
.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge,quilt -ContinueOnError:$false

# Single loader
.\scripts\local\build-multiversion.ps1 -Loaders fabric -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders forge -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders neoforge -ContinueOnError:$false
.\scripts\local\build-multiversion.ps1 -Loaders quilt -ContinueOnError:$false

# Standalone 26.x branches
.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon
.\gradlew.bat -p Loader\forge26.1 clean build --no-daemon

# Quick sanity build (root project only)
.\gradlew.bat clean build writeVersion --no-daemon

# Hook tests
python -m pytest tests/test_hooks.py -v
```

## Key Files

| File | Purpose |
|------|---------|
| `build.gradle` | Root build: Architectury Loom + forge/neo Loom plugins |
| `settings.gradle` | Plugin management: Loom + ForgeGradle |
| `gradle.properties` | All version props (MC, loaders, mappings, Java home) |
| `common/build.gradle` | Shared code compilation (maps MC dep → common config) |
| `Loader/fabric/build.gradle` | Fabric-specific: remaps + includes common at build time |
| `Loader/fabric26.1/` | Standalone Fabric 26.x project (Architectury Loom, no mappings) |
| `Loader/forge26.1/` | Standalone Forge 26.x project (ForgeGradle 7, no mappings) |
| `forge/build.gradle` | Forge <26 via Loom: `${minecraft_version}` in dependency |
| `forge/gradle.properties` | `loom.platform=forge` |
| `neoforge/gradle.properties` | `loom.platform=neoforge` |
| `quilt/build.gradle` | Quilt Loom (root subproject), reuses common + fabric source |
| `quilt/src/main/resources/quilt.mod.json` | Quilt metadata: entrypoint `StatusModFabric`, depends quilt_loader/fabric-api |
| `scripts/local/build-multiversion.ps1` | Orchestrates all loader/version builds |
| `scripts/local/loader-versions.json` | Fabric/Forge/NeoForge versions + Fabric API URLs |
| `scripts/local/fetch-loader-versions.py` | Fetches latest versions from Maven/MDK |
| `version.txt` | Current mod version |
| `AGENTS.md` | This file (git-ignored, local only) |

## Pitfalls

1. **`gradle.properties` has NO `forge_minecraft_version`** — Forge dependency uses `${minecraft_version}` directly. Setting a separate Forge version breaks Fabric builds.

2. **`org.gradle.jvmargs` affects all Gradle invocations** — the multiversion script saves/restores this property. If the script crashes mid-run, `gradle.properties` may be left with temporary values. Always run from clean git state.

3. **Forge <26 uses Loom, not ForgeGradle** — `loom.platform=forge` in `forge/gradle.properties`. Only Forge 26.x uses standalone ForgeGradle 7 (`Loader/forge26.1/`).

4. **MC 26.x ships unobfuscated** — no mappings needed for 26.x builds. `fabric26.1` and `forge26.1` have no mappings in their `gradle.properties`.

5. **Gradle 9.x doesn't resolve `${var}` in `properties` files** — cannot reference other properties within properties files. Use `${variable}` only in `build.gradle` files.

6. **`Loader/*/build/` and `Loader/*/.gradle/` are git-ignored** — don't commit build artifacts from standalone subprojects.

7. **Java version matters** — MC <26 needs Java 21 (`JAVA_21_HOME`), MC ≥26 Forge/NeoForge needs Java 25 (`FABRIC_JAVA_25_HOME`). Fabric 26.x always uses Java 25.

8. **Quilt Loom must be Gradle-9-compatible** — `quilt_loom_version` in `loader-versions.json` is `1.15.1` (NOT the older `1.8.5`, which only works with Gradle 8). The script writes this into root `gradle.properties`; `settings.gradle` reads it via `${quilt_loom_version}`. `gradle.properties` has a `quilt_loom_version=1.15.1` default so sanity builds work without the script.

9. **Quilt JARs use Mojang mappings + `intermediate_mappings: net.fabricmc:intermediary`** — quilt `build.gradle` uses `loom.officialMojangMappings()` (common code is Mojang-mapped); the `quilt_mappings` prop from the script is unused but must stay non-empty (else the script skips Quilt).

10. **Quilt JVM crashes possible under low RAM** — `remapJar` (fabric-api remap) can native-OOM the Gradle daemon (`hs_err_pid*.log`, "G1 virtual space"). Retry usually succeeds; it's transient system memory pressure, not a build error.

8. **Root `build.gradle` uses Groovy `=` syntax** for Loom config — must stay Groovy (not Kotlin DSL) because Loom's `indentation` and `remapperArchives` don't exist in Kotlin DSL.

## Loader Matrix

| Loader | Versions | Build system | Notes |
|--------|----------|--------------|-------|
| Fabric | 1.19 - 1.21.11 | Loom (root project) | Shared source |
| Fabric | 26.1 - 26.2 | Standalone Loom | `Loader/fabric26.1/` |
| Forge | 1.19 - 25.x | Loom + `loom.platform=forge` | Shared source, `${minecraft_version}` |
| Forge | 26.1 - 26.2 | ForgeGradle 7 standalone | `Loader/forge26.1/`, no mappings |
| NeoForge | 1.21+ | Loom + `loom.platform=neoforge` | Shared source |
| Quilt | 26.1 - 26.2 + 1.21.11 (target MC 1.21.11) | Quilt Loom 1.15.1 (root subproject) | Reuses fabric source; `quilt.mod.json` entrypoint `StatusModFabric` |

### Icon/Logo locations

| Loader | Icon path in resources | Metadata field |
|--------|------------------------|----------------|
| Fabric | `fabric/src/main/resources/assets/statusmod/icon.png` | `"icon": "assets/statusmod/icon.png"` in `fabric.mod.json` |
| Forge (<26) | `forge/src/main/resources/logo.png` | `logo="logo.png"` in `mods.toml` |
| Forge (26.x) | `Loader/forge26.1/src/main/resources/logo.png` | `logo="logo.png"` in `mods.toml` |
| NeoForge | `neoforge/src/main/resources/logo.png` | `logo="logo.png"` in `neoforge.mods.toml` |
| Quilt | `fabric/src/main/resources/assets/statusmod/icon.png` | `"icon": "assets/statusmod/icon.png"` in `quilt.mod.json` (via fabric resources) |

Regenerate icon: `python scripts/local/gen-icon.py` → `docs/statusmod-icon.png` (512×512 RGB). In-JAR logos: `python scripts/local/gen-icon.py --out <path> --size 256 --transparent` (RGBA, transparent corners). Design: obsidian rounded square, "STATUS PLAYER" title, 4 rows status (colored) + name (white). Layout auto-scales by size/512.

## Session State (last updated: 2026-08-19)

### CI/CD Pipeline — Complete

| Component | Status |
|-----------|--------|
| Build all 15 JARs | ✅ Working |
| JAR verification (metadata) | ✅ Working |
| Modrinth publish | ✅ Working (15/15 uploaded) |
| CurseForge publish | ❌ Needs valid API token |
| GitHub Release | ✅ Working (created on push with `force_publish`) |

### Key Files (CI)

| File | Purpose |
|------|---------|
| `.github/workflows/build.yml` | CI/CD: build, verify, publish to Modrinth/CF/GitHub Releases |
| `scripts/ci/publish-all.ps1` | Publish to Modrinth (v2 API) + CurseForge (legacy API) |
| `scripts/ci/verify-jars.ps1` | Verify JAR metadata + expected combos |
| `docs/statusmod-icon.png` | Project icon (512×512 PNG, deterministic code-generated) |

### Secrets Needed (GitHub repo Settings → Secrets → Actions)

| Secret | Status |
|--------|--------|
| `MODRINTH_TOKEN` | ✅ Set (working) |
| `MODRINTH_PROJECT_ID` | ✅ Set (working) |
| `CURSEFORGE_TOKEN` | ❌ Needs fresh key from https://legacy.curseforge.com/account/api-tokens |
| `CURSEFORGE_PROJECT_ID` | ❓ May need verification |

### Publish trigger

- **Automatic**: on `main` push when `version.txt` content changes
- **Manual**: Go to Actions → "Build & Verify" → Run workflow → check `force_publish`
  - Optional: `loaders` (comma-separated: `fabric,forge,neoforge`) — empty = all
  - Optional: `mc_versions` (comma-separated: `1.21.11,26.2`) — empty = all

### Modrinth API details

- Endpoint: `POST https://api.modrinth.com/v2/version` (singular)
- Auth: `Authorization: mrp_...` header
- Body (JSON in `data` form field): `project_id`, `file_parts: ["file"]`, `name`, `version_number`, `changelog`, `game_versions: [...]`, `version_type`, `loaders: [...]`, `featured`, `dependencies: []`, `primary_file: "file"`
- File attached as form field `file`

### CurseForge API details

- Legacy endpoint: `https://minecraft.curseforge.com/api/game/versions` (versions list), `https://minecraft.curseforge.com/api/projects/{id}/upload-file`
- Auth: `X-API-Key` header
- Need valid token from https://legacy.curseforge.com/account/api-tokens

### Known Issues

1. **CurseForge 401** — token in secrets is invalid; needs fresh key from legacy.curseforge.com
2. **JAR naming** — all 15 use `Statusmod-{version}-{loader}-{mc}.jar` (case-insensitive regex for NTFS)

### 2026-07-29 — MC version range fixes ✅ Committed (d790b39)

- **Fabric**: `fabric.mod.json` → `"minecraft": ">=1.21.11"` (was `>=1.21.0`)
- **Forge (shared, MC <26)**: `mods.toml` → `[1.21.11,1.21.12)` (was `[1.21,1.21.12)`)
- **Forge 26.x standalone**: new `Loader/forge26.1/src/main/resources/META-INF/mods.toml` with `[26.1,26.2)` (+ `duplicatesStrategy=EXCLUDE` in build.gradle to override shared TOML)
- **NeoForge**: `neoforge.mods.toml` → `[1.21.11,26.2]` (covers both pre-26 and 26.x)

All 15 builds verified passing.

### 2026-07-29 — NeoForge 26.x rebuild ✅ Committed (d790b39)

- **Issue**: NeoForge 26.1 failed at runtime: `versionRange="[1.21,1.21.12)"` didn't include MC 26.1
- **Fix**: Already applied locally — `versionRange="[1.21.11,26.2]"` in `neoforge.mods.toml`
- **Rebuilt**: All 5 NeoForge JARs (26.2, 26.1.2, 26.1.1, 26.1, 1.21.11) built successfully
- **JAR location**: `dist/multiversion/neoforge/`

### 2026-07-29 — Icon/description fix + selective publish ✅ Committed (d790b39)

- **Issue**: In-game mod shown no logo, no description, no button text — all 4 metadata files lacked `icon`/`logo` refs and `description` field
- **Fix**: Added `description`, `icon`/`logo` paths, `authors`, `license`, `environment` to all metadata files
- **Icon**: Generated 256×256 PNG (Minecraft-style: obsidian border, shield badge, inventory slot status dots, diamond gem, Steve head icon)
- **Workflow**: Added `loaders` and `mc_versions` manual inputs to `workflow_dispatch` for selective publish. `publish-all.ps1` accepts `-Loaders` and `-McVersions` params with JAR filtering logic

### 2026-07-30 — Committed + pushed all pending changes

- **Commit**: `d790b39` — 13 files, 70 insertions, 11 deletions
- **Pushed**: `main` → `origin/main`

### 2026-07-30 — Dashboard Security Hardening + Missing Features ✅

**Dashboard Build:** `npm run build` ✅ — 39 API routes, 28 static pages, 0 type errors

**Critical Fixes (8/8):**
- C1+C8: Argon2id for all code/key hashing (`lib/hash.ts` + `lib/auth.ts`)
- C2: TOTP verification now validates token via `otplib.verify()` (was no-op)
- C3: TOTP secret encrypted with AES-256-GCM (SERVICE_ROLE_KEY derived)
- C4: Recovery codes hashed with Argon2id, persisted to `recovery_code_hashes`
- C5: Mod polling endpoints added: `GET .../updates?since=` and `.../admin-actions?since=`
- C6: `DELETE /api/players/[server_id]/[uuid]` — HMAC-anonymizes PII
- C7: Stripe success page at `/dashboard/shop/success` + verify-checkout API
- Refresh token reads role from DB (was hardcoded `"owner"`)

**Missing Features Added:**
- Player detail page (`/dashboard/players/[uuid]`) with status/color/joined display
- Stripe checkout verification (`GET /api/shop/verify-checkout?session_id=`)
- Audit logging on all admin actions (mute, unmute, status update, config change)
- Security headers: HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy
- Players page now reads server_id from JWT (was calling non-existent `/api/players`)

**Key Changed Files:**

| File | Change |
|------|--------|
| `lib/hash.ts` | Argon2id + AES-256-GCM encrypt/decrypt + HMAC |
| `lib/auth.ts` | Argon2id API key verification (iterate all keys) |
| `lib/audit.ts` | New audit log helper |
| `next.config.mjs` | Security headers (HSTS, CSP, etc.) |
| `types/otplib.d.ts` | Type declarations for otplib v13 |
| `app/api/auth/*/route.ts` | 7 files: Argon2id + TOTP + encrypted secrets |
| `app/api/keys/route.ts` | Argon2id key hashing, dedicated rate limit |
| `app/api/keys/leak/route.ts` | Uses `dashboard_codes` table (was `verify_codes`) |
| `app/api/players/[server_id]/[uuid]/route.ts` | Added DELETE (DSGVO anonymization) |
| `app/api/players/[server_id]/[uuid]/status/route.ts` | Audit logging |
| `app/api/players/[server_id]/[uuid]/mute/route.ts` | Audit logging |
| `app/api/players/[server_id]/[uuid]/unmute/route.ts` | Audit logging |
| `app/api/players/[server_id]/[uuid]/updates/route.ts` | NEW — mod polling |
| `app/api/players/[server_id]/[uuid]/admin-actions/route.ts` | NEW — mod polling |
| `app/api/server/[server_id]/config/route.ts` | Audit logging |
| `app/api/shop/verify-checkout/route.ts` | NEW — Stripe checkout verification |
| `app/dashboard/players/page.tsx` | Fixed server_id from JWT, clickable rows |
| `app/dashboard/players/[uuid]/page.tsx` | NEW — player detail |
| `app/dashboard/shop/success/page.tsx` | NEW — Stripe success page |

**Remaining Medium-Low Items (not blocking):**
- JWT secret rotation cron (90-day warning — health endpoint warns)
- Incident response runbook / PagerDuty integration
- Console log sanitization validation

**Completed in this sub-session:**
- Health endpoint: schema version, DB status, JWT age warning (`/api/health`)
- Redis rate limiter support (`lib/rate-limit-redis.ts`) — falls back to in-memory
- SSE endpoint for realtime player push (`/api/events`)
- Sentry crash reporting (`@sentry/nextjs` + `sentry.{server,client,edge}.config.ts` + `instrumentation.ts` + `next.config.mjs` wrapper)
- Rate limit headers on all auth routes (code, setup, 2fa/verify, refresh)
- Backup automation scripts (`scripts/backup.ps1`, `scripts/restore-test.ps1`)
- Schema versioning migration system (`lib/version.ts`, `migrations/001_schema_version.sql`)
- `.env.local` for local dev

### 2026-07-30 — Discord Bot + Vercel Deploy + Stripe entfernt

**Discord Bot** (`C:\Users\Steven\Desktop\Status mod bot\`):
- Python/py-cord, 16 Slash-Commands
- `api/client.py` — REST-Client gegen Dashboard-API
- `sessions.py` — DM-Session-Management (15 Min TTL)
- Cogs: code, status, info, admin, shop, keys
- Start: `python main.py` (mit .env)
- **Nicht auf Vercel deploybar** (Python, benötigt Langzeit-Prozess)

**Dashboard deployed:** https://statusmod-dashboard.vercel.app
- `vercel deploy --prod` → aliased
- Env vars gesetzt: DATABASE_URL, SERVICE_ROLE_KEY, JWT_SECRET, CORS_ORIGIN, etc.
- ⚠️ SERVICE_ROLE_KEY + JWT_SECRET im Chat sichtbar — **rotieren!**

**API-Änderungen:**
- `GET /api/me` — User-Info aus JWT (uuid, role, server_id, totp_enabled)
- `/api/auth/code` + `/api/auth/refresh` — Response enthält jetzt `uuid` + `server_id`
- Stripe komplett entfernt (webhook, verify-checkout, success-page, env-vars, dependency)

**Dashboard Build:** 40 API routes, 26 static pages, 0 errors

### 2026-07-31 — Security-Härtung Phase 2 ✅ (deployed)

**Secrets rotiert + deployed:**
- **SERVICE_ROLE_KEY** rotiert (User via Supabase Dashboard). Neuer Key in Vercel Production + `.env.local`. Supabase: `ujksgdbfczbgtifiovuw`. Key funktioniert (REST-Verify + `/api/health` = `healthy`, `database=connected`).
- **JWT_SECRET** rotiert: neu `F+d+s6p4BkAuQnc/w2jFX0TqhKe1/oWXVQ8hXJzC27J2hOAgBC3wQLIjbndCstp0`; alt als `JWT_SECRET_PREVIOUS` auf Vercel (grace period).
- **DISCORD_WEBHOOK_SECRET** neu: `gOtc/aQ9VnkLug6dpCfZgdHP7PO2vBbbVyCRYOyABooEMMaOBjqcoz8PK9PGCFBo`. Webhook verifiziert Bearer-Token.

**⚠️ Argon2-Migration (wichtig!):** `argon2` (native, kein Build für Node 24/abi=137) → **`@node-rs/argon2`** (N-API). `lib/hash.ts` nutzt `hash/verify` aus `@node-rs/argon2` (gleiches PHC-Format `$argon2id$v=19$m=19456,t=2,p=1`, bestehende Hashes bleiben verifizierbar). `next.config.mjs` markiert `@node-rs/argon2` als **webpack external** (server-only) — sonst: `Module parse failed ... .node` beim Build. Vercel-Lambda lädt dann das Platform-Binding (`@node-rs/argon2-linux-x64-gnu`) zur Laufzeit. Ohne diesen Fix: alle Auth-Routen 500 (`No native build was found`).

**Security-Fixes (Routen):**
- **IDOR Player-Export:** alle 4 Queries auf `session.server_id` gefiltert
- **Bot-Session-Token:** `sessions.py require_session(api_client=...)` setzt `set_session(access_token)`; alle 5 Cogs angepasst
- **API-Key Server-Scoping:** `requireApiKey(request, scope, serverId?)` filtert `.eq("server_id", serverId)`; alle 14 Call-Sites
- **Role-Checks:** Server-Config PUT + Mute/Unmute + Shop Products/Statuses → nur owner/admin + server_id match; Keys DELETE → nur owner
- **Key-Leak:** `KeyLeakSchema` statt loose parsing
- **Health-Route:** `force-dynamic` (war statisch gecached)

**Verifiziert (live):** `/api/health` healthy; `/api/me` ohne Auth = 401 (vorher 500); `/api/players/<uuid>` ohne Key = 401; Webhook ohne/mit falschem Token = 401; `/api/auth/code` leeres Body = 400 → Argon2id lädt auf Vercel-Node-24 korrekt.

**Vercel Env Vars (Production, encrypted):** CORS_ORIGIN, JWT_SECRET (neu), JWT_SECRET_PREVIOUS (alt), NEXT_PUBLIC_SUPABASE_ANON_KEY, NEXT_PUBLIC_SUPABASE_URL, SERVICE_ROLE_KEY (neu), DATABASE_URL, DISCORD_WEBHOOK_SECRET.

**Offen:** CurseForge 401 (altes Token) — braucht frischen Key von https://legacy.curseforge.com/account/api-tokens. Kein git-commit durchgeführt.

### 2026-07-31 — Setup-Flow (Server-Code-Claim + API-Key) ✅

**Feature:** Dedicated Server registriert sich automatisch über einen 16-Zeichen Setup-Code, Server-Code wird auf der Dashboard-Seite geclaimt, API-Key wird automatisch generiert.

**Setup-API-Vertrag:**
- `POST /api/auth/setup/init` — Mod→Dashboard Bootstrap. Bearer `MOD_SETUP_SECRET`, Body `{server_id: string (UUID), code: string (16, A-Za-z0-9)}` → 200 `{server_id, expires_in: 86400}`. Falsches Secret → 401.
- `POST /api/auth/setup` — Claim. Body `{code}` → `{access_token, refresh_token, expires_in, server_id, api_key, key_prefix}`. Löscht Code, setzt `owner_uuid`, generiert API-Key (`sm_`-Prefix, hashed).

**Mod-Logik (`common/src/main/java/com/teufel/statusmod/setup/SetupManager.java`):**
- `runIfNeeded()` läuft NUR wenn `platform.isDedicatedServer()` UND `serverId` leer in `ModConfig`.
- Abbrüche: kein Config, `serverId` gesetzt (Idempotenz), `dashboardUrl` leer, `setupSecret` leer.
- Registriert Code in Daemon-Thread, bei Erfolg `config.serverId = serverId; config.save()`, druckt Code-UI in Konsole.
- `ModConfig` neue Felder: `dashboardUrl`, `setupSecret`, `serverId`, `apiKey` (+ Trim-Normalisierung).
- `Platform.isDedicatedServer()` für Fabric/Forge/NeoForge implementiert.

**Dashboard-Secrets/URLs:**
- Dashboard: `https://statusmod-dashboard.vercel.app` (Vercel project `statusmod-dashboard`, `prj_9bRByVzzoVCLdKwwoJcRHXjZeUJl`)
- **MOD_SETUP_SECRET (Dashboard→Mod geteilt):** `AHYt4c7+4YixNHpCAT4jAQhgw8Q5Gfv9PzWxtWOr+80=` — gesetzt als Vercel Env (Production + Development; Preview schlug fehl), in `dashboard/.env.local` und Testserver-`config.json`.
- Supabase Ref: `ujksgdbfczbgtifiovuw`
- Testserver (fester E2E-Test): `C:\Users\Steven\Downloads\Status Test`, start via `start.bat` (`java -Xms4096M -Xmx4096M -jar fabric-server-mc.26.2-loader.0.19.3-launcher.1.1.2.jar nogui`).
- E2E verifiziert: `serverId fa074ec9-7d3f-4815-aac0-b6660a61c797`, Code `W85HT8QXVP8RFAA7`, Claim 200 `{api_key: sm_01eea3...}`, DB: dashboard_codes 0, users 1, api_keys 1, servers.owner_uuid gesetzt. Test-Init-Datensatz `11111111-2222-4333-8444-555555555555` aufgeräumt.

**Multiversion-Build 15/15 ✅:**
- `.\scripts\local\build-multiversion.ps1 -Loaders fabric,forge,neoforge -ContinueOnError:$false` → alle ok, Version 1.3.0.
- Output: `dist\multiversion\{fabric,forge,neoforge,quilt}\Statusmod-1.3.0-{loader}-{mc}.jar`. Report: `dist\multiversion\build-report.md`.
- Datapack: NICHT im Repo konfiguriert (kein Modul) — Skript skippt es. Quilt: seit 2026-07-31 eingebaut (s. Session-Sektion unten).

**Aktueller Testserver-Config-Stand (config.json zurückgesetzt, setupSecret neu eingetragen):**
`{"adminOpLevel":2,...,"dashboardUrl":"https://statusmod-dashboard.vercel.app","setupSecret":"AHYt4c7+4YixNHpCAT4jAQhgw8Q5Gfv9PzWxtWOr+80=","serverId":"","apiKey":""}` — `serverId` leer → nächster Start generiert frischen Setup-Code.

**LSP-Hinweise (bekannt, ignorieren):** Dashboard-`next/server`-Errors (nicht auflösbar), SetupManager.java-Superzeichen `(` (Red Herring). JDK-Pfade für Builds: `C:\Program Files\Java\jdk-21` (MC<26), `C:\Program Files\Java\jdk-25.0.3` (26.x). Aktueller Shell-JAVA_HOME: `C:\JDK\oracleJdk-26`.

### 2026-07-31 — Persistent Login (Cookies) + Legal-Seiten ✅ (nicht committed/deployed)

**Persistent Login (Ansatz A — HttpOnly-Cookie + Auto-Refresh):**
- `lib/cookies.ts` (neu): `refreshCookieHeader()` (Path=`/`, 7 Tage), `clearRefreshCookieHeader()`, `getRefreshTokenFromCookie()`; `Secure` nur wenn `ALLOW_HTTP != true`.
- Alle Auth-Routen nutzen den Helper: `code`, `setup`, `refresh` setzen Cookie mit `Path=/` (vorher `/api/auth`), `logout` liest Cookie + cleart.
- `refresh`-Route liest Refresh-Token jetzt zuerst aus dem Cookie, Body als Fallback (Bot-Kompatibilität). `logout` analog.
- `lib/client/auth.ts` (neu): `apiFetch()` (Bearer-Attach + 401→`refreshSession()`→Retry, In-Flight-Promise-Dedup gegen Rotations-Races), `ensureSession()` (Gate), `getServerId()` (JWT-Decode), `logout()`.
- `app/dashboard/layout.tsx`: Gate nutzt `ensureSession()` — bei leerem/abgelaufenem Access-Token wird über Cookie refreshed → **Login übersteht Browser-Neustart**.
- Alle Dashboard-Seiten + Sidebar auf `apiFetch`/`logout` umgestellt; `refresh_token` wird NICHT mehr in localStorage gelegt (nur Access-Token).
- **Bugfix:** Dashboard-Overview rief `/api/players` (existiert nicht → stiller 404, PlayerCount immer 0). Jetzt `/api/players/${serverId}` via `getServerId()`.

**Legal-Seiten (Deutsch, statisch):**
- `app/agb/`, `app/datenschutz/`, `app/nutzungsbedingungen/`, `app/impressum/` (Impressum mit Platzhaltern `[Name]`, `[Straße]`, etc. — muss User ausfüllen).
- `components/footer.tsx` (neu): Links auf alle 4 Seiten; eingebunden in Landing, Login, Setup, Dashboard-Layout.
- `components/legal-layout.tsx` (neu): geteiltes Layout mit `prose`-Styling.
- `tailwind.config.ts`: `@tailwindcss/typography`-Plugin registriert (war installiert, aber ungenutzt).

**Build:** `npm run build` ✅ — 44 API routes, 30 Seiten, 0 Type-Errors. **Nicht committet/gepusht/deployed** (User-Anweisung).

### 2026-07-31 — Logout-Bug nach Registrieren + kaputte Dashboard-Seiten ✅ (deployed)

**Ursache (live reproduziert):** Daten-Routen (`/api/players/[server_id]`, config-GET, audit-GET, shop-GET) verlangten ausschließlich `x-api-key` (Mod-Key). Das Dashboard sendet nur das JWT → **jeder Seitenaufruf 401 → `apiFetch` löst Refresh aus (Rotation) → Refresh-Rate-Limit (5/min) → 6. Refresh = 429 → `clearSession()` + Redirect /login**. Beim Durchklicken nach dem Registrieren (Overview→Players→Detail→…) wurde man so innerhalb einer Minute ausgeloggt.

**Fixes:**
- `lib/auth.ts`: neuer Helper `authorizeData(request, scope, serverId)` = **JWT-Session (server_id aus JWT muss zum Pfad passen) ODER Mod-API-Key** → `{kind:"session"} | {kind:"apiKey"} | null`.
- 6 GET-Routen auf `authorizeData` umgestellt: `players/[server_id]`, `players/[server_id]/[uuid]`, `audit/[server_id]`, `server/[server_id]/config`, `shop/products/[server_id]`, `shop/statuses/[server_id]`. PUT/POST/DELETE blieben auf `requireSession`.
- `lib/client/auth.ts`: `refreshSession()` gibt jetzt HTTP-Status zurück (nicht Boolean). `apiFetch` cleart Session + redirectet NUR bei Refresh-Status 401/400; **429/5xx = transient, kein Logout**.
- Refresh-Rate-Limit: `max 5` → `max 10` pro 60s.
- Kaputte Dashboard-Seiten gefixt (hatten Pfade ohne server_id → 404, Daten luden nie):
  - `config/page.tsx`: `/api/server/config` → `/api/server/${serverId}/config` (GET+PUT)
  - `audit/page.tsx`: `/api/audit` → `/api/audit/${serverId}`
  - `shop/page.tsx`: `/api/shop/{products,statuses}` → `/api/shop/{products,statuses}/${serverId}`

**Live verifiziert (neuer Wegwerf-Server `55555555-…`):** PLAYERS/CONFIG/AUDIT/SHOPPROD/SHOPSTAT mit JWT → alle 200; PLAYERS/CONFIG mit x-api-key → 200 (Mod unverändert); JWT mit fremdem server_id → 401 (Security intakt); Refresh → 200. Audit/Shop mit x-api-key → 401 ist korrekt (Claim-Key hat nur `check`-Scope).

**Aufgeräumt:** Wegwerf-Server `11111111-…`, `44444444-…`, `55555555-…` inkl. refresh_tokens/api_keys/dashboard_codes/dashboard_users gelöscht. Echte Server intakt: `69dea5f0` (aktuell, Session gültig), `29c81267`, `fa074ec9`.

### 2026-07-31 — Plan-Gap: Brute-Force-Lockout Login ✅ (deployed)

**Plan-Abgleich (`docs/dashboard-plan.md`):** REST-API vollständig bis auf bewusst entferntes Stripe (Webhook, Checkout, Success-Page). `attempt_count`-Schutz war wirkungslos (nur bei Erfolg inkrementiert, dann sofort resettet → `.lt("attempt_count", 3)`-Filter griff nie).

**Gebaut:**
- `lib/rate-limit.ts`: `LOGIN_LOCKOUT {maxFailures:3, windowMs:60_000, banMs:300_000}` + `isLockedOut()`, `lockoutRemainingMs()`, `recordLoginFailure()`, `clearLoginFailures()` (in-memory, pro Instanz — wie bestehender Rate-Limiter).
- `app/api/auth/code/route.ts`: Lockout-Check VOR Rate-Limit (429 + `Retry-After`-Header); `recordLoginFailure` bei falschem Code; `clearLoginFailures` bei Erfolg; toten `attempt_count`-Increment entfernt (nur noch `used=true`). `.lt("attempt_count",3)`-Filter bleibt als harmlose Defense-in-Depth.

**Hinweis (Limitation):** In-memory = pro Serverless-Instance. Live-Test: 401→401→401 (3 Fehlversuche registriert) → 429; `Retry-After` vs `X-RateLimit-Reset` je nach Instance nicht deterministisch verteilbar. `lib/rate-limit-redis.ts` existiert, aber **nirgends verdrahtet** (toter Code) — bei Bedarf Lockout + Rate-Limits auf Redis umstellen.

**Build:** `npm run build` ✅ (mit `NODE_OPTIONS=--max-old-space-size=4096` — ohne Heap-Erhöhung OOM). **Deployed:** Production alias `https://statusmod-dashboard.vercel.app`.

**Nicht committet** (wie gehabt): `dashboard/` komplett untracked, `CodeCommand.java`, `PermissionUtil.java`, Loader-Registrierungen, `setup/`, `docs/dashboard-plan.md`.

### 2026-07-31 — Quilt-Support eingebaut ✅ (5/5 JARs, CI/Publish erweitert, Runtime getestet; nicht committet)

**Frage des Users:** „hast du auch schon die neuen loader mit versionen eingebaut?" → Nein (nur Fabric/Forge/NeoForge). User wählte **Quilt** (nicht Bukkit/Paper, Folia, Datapack).

**Neues Modul `quilt/` (Root-Subprojekt, wie fabric/forge/neoforge):**
- `quilt/build.gradle`: Plugin `org.quiltmc.loom` (Version via `${quilt_loom_version}`), sourceSets = common + fabric (Java + Resources, fabric.mod.json wird ausgeschlossen), `loom.officialMojangMappings()`, Java-25-Toolchain (wie Root), deps `org.quiltmc:quilt-loader:${quilt_loader_version}` + fabric-api + architectury-fabric + gson.
- `quilt/src/main/resources/quilt.mod.json`: entrypoint `com.teufel.statusmod.fabric.StatusModFabric` (Quilt unterstützt fabric-Entrypoints nativ), `intermediate_mappings: net.fabricmc:intermediary`, depends `quilt_loader >=0.30.0`, `minecraft >=1.21.11`, `fabric-api *`. Description OHNE Em-Dash (Gradle-`expand` verhunzt UTF-8-Sonderzeichen auf Windows).
- `settings.gradle`: quilt-Maven-Repo + `id 'org.quiltmc.loom' version "${quilt_loom_version}"` + `include 'quilt'`.
- `gradle.properties`: Defaults `quilt_loader_version=0.30.0`, `quilt_loom_version=1.15.1`.
- `scripts/local/loader-versions.json`: `quilt_loom_version` `1.8.5` → `1.15.1` (Gradle-8-Ära) UND `quilt_loader_version` `0.29.2` → `0.30.0`.

**⚠️ Loader 0.30.0 ist zwingend (PITFALL):** Quilt Loader providet `fabricloader` in eigener `quilt.mod.json` (`provides`). 0.29.2 providet nur `0.17.2`, aber alle fabric-api 1.21.11-Versionen verlangen `fabricloader >=0.17.3` → Crash „Fabric API requires version [0.17.3, ∞) of fabricloader". 0.30.0 providet `0.19.2` → passt. Inkl. 0.30.1-beta.2 existiert als neueste; 0.30.0 ist letzte stabile.

**CI/Publish (erledigt):**
- `scripts\ci\verify-jars.ps1`: Regex um `quilt`, `$expected` um 5 Quilt-Einträge, neuer `quilt.mod.json`-Check. **Zusätzlich gefixt:** `$jars`-Filter schließt `.gradle-user-home\` und `logs\` aus — sonst matcht der isolierte GRADLE_USER_HOME (unter `dist\multiversion\`) tausende Gradle-Lib-JARs → 1000+ False-Errors. Ergebnis: `All 20 JARs verified OK`.
- `scripts\ci\publish-all.ps1`: Regex `(fabric|forge|neoforge|quilt)`, `$cfAliases` um `"quilt" = "Quilt"`.
- `.github\workflows\build.yml` line 90: `-Loaders fabric,forge,neoforge,quilt` + Description ergänzt.

**Runtime-Test Quilt-Server ✅ (`C:\Users\Steven\Downloads\QuiltServerTest`):**
- Setup: `quilt-installer-0.15.0.jar install server 1.21.11 0.30.0 --install-dir=... --download-server --create-scripts` (kein Spaces im Pfad wegen Installer-Arg-Parsing; nutzt `quilt-server-launch.jar`). Mods: `Statusmod-1.3.0-quilt-1.21.11.jar` + **fabric-api-0.141.6+1.21.11** (neuere Version ok, dep `fabric-api *`). QSL/QFAPI existiert NICHT für 1.21.11 (Modrinth/Maven veraltet) — normales fabric-api + Loader 0.30.0 ist der richtige Weg.
- **Java 25 nötig:** Alle Mod-JARs (auch fabric/forge 1.21.11!) sind mit Toolchain 25 kompiliert (class file 69.0) → Server mit jdk-25 starten, NICHT jdk-21 (sonst `UnsupportedClassVersionError`).
- Log: `[StatusMod] Initializing on fabric` + `Done (0.393s)!` + `Server empty for 60 seconds, pausing` (normal, wartet auf Spieler). Einmal falsch-negativ: nach `Done` kam `Server empty... pausing` → NICHT stuck.
- `server.properties`: `online-mode=false`, `server-port=25566`, `level-type=minecraft\:flat`. Start: `java -Xms1024M -Xmx2048M -jar quilt-server-launch.jar nogui`.

**Bekanntes Problem:** JVM-native-OOM beim `remapJar` (hs_err_pid*.log, „G1 virtual space") — transient bei niedrigem System-RAM; Retry erfolgreich. Kein Logikfehler.

**Nicht committet** (wie gehabt): alles — u.a. neue Dateien `quilt/build.gradle`, `quilt/src/main/resources/quilt.mod.json`, Edits an `settings.gradle`, `gradle.properties`, `scripts/local/loader-versions.json`, `scripts/ci/*`, `.github/workflows/build.yml`.

### 2026-07-31 — CI-Runtime-Test-Harness ✅ FABRIC 1.21.11 KOMPLETT GRÜN

**Ziel:** GitHub-Action startet echte Minecraft-Server (MP-Test) und -Clients (SP-Test) pro Loader/MC-Version, verifiziert `[StatusMod] Initializing` + `Done` + `/modinfo`-Response; `publish`-Job hängt an grünen Runtime-Tests.

**Stand:** `scripts/ci/test-runtime.ps1` → **`RESULT: PASSED (fabric 1.21.11)`** — MP-Test UND SP-Test grün.

**Status-MP (grün):** Server bootet `DONE OK`, `[StatusMod] Initializing`, modinfo-Response ok. Fabric-Server-Setup: `fabric-installer-1.1.2.jar server -mcversion 1.21.11 -loader 0.19.3 -dir <dir> -downloadMinecraft` (single-dash Flags!).

**Status-SP (grün):** Client bootet komplett durch in den QuickPlay-Singleplayer (`CI_Test joined the game`), `[StatusMod] Initializing on fabric` im Log. Zwei Assertion-Fixes nötig (s. unten). Assets: **4591 Objekte / ~450 MB** in `client\assets\objects`.

**SP-Assertion-Fixes (neu, nicht mehr ändern):**
- **`Done (` existiert im Singleplayer-Log NIE** → SP-Erfolgsmarker ist `(?:Done \(|joined the game)` (`joined the game` = Player kam in die integrierte Welt). Sonst schlug der Test nach 240s fehl, obwohl der Client voll gebootet war.
- **`Caused by:` ist im Client-Log harmlos** — Offline-Client mit Fake-Token wirft `Failed to fetch user properties` → `InvalidCredentialsException`/`MinecraftClientHttpException: Status: 401` (Download-Thread). Error-Check filtert Benign-Lines (`MinecraftClientHttpException|InvalidCredentialsException|authlib|Unsupported JNI`) und meckert nur noch über echte Crashes.

**Asset-Downloader (GEFIXT + verifiziert ✅):** Konsole wird nicht mehr verschmutzt. Alte Version (`-O --output-dir` + `2>&1 | Out-String | Write-Host`) schrieb Binär auf stdout. Neue Version: Config mit **expliziten `output = "C:/…/asset-dl/<hash>"`-Zeilen** (Forward-Slashes!), UTF-8 **ohne BOM** via `[System.IO.File]::WriteAllLines`, curl `-s -S -f --parallel --parallel-max 32 --config $cfg *> curl.log` (alle Streams in Logfile), dann Move in `objects\<ab>\`.

**Gefixt (nicht mehr ändern):**
- Param heißt **`StartArgs`** (nicht `Args` — kollidiert mit `$args`). Beide Call-Sites (MP ~657, SP ~539) umgestellt.
- `ProcessStartInfo.ArgumentList` zuverlässig → `ConvertTo-ArgumentString` + `$psi.Arguments`.
- `add_OutputDataReceived` mit Delegate crasht → `Register-ObjectEvent -Action` + `Add-Content`.
- Fabric-Client-Install: **`-noprofile`** (nicht `-launcher win32` — verlangt launcher_profiles.json).
- StrictMode-Fallen: `$lib.downloads.classifiers` braucht lokale `$downloads` + `PSObject.Properties`-Guard; `(Test-Path $x)` in `-and`-Kette klammern; Copy-Item-Wildcard braucht vorher angelegtes Zielverzeichnis (PS 5.1 „Container cannot be copied onto existing leaf item"); `$Process.WaitForExit(5000)` liefert `True` in Pipeline → `[void]$...` (hatte SP-Result `Object[]` statt `[pscustomobject]`).

**Workdir (gecacht):** `C:\Users\Steven\AppData\Local\Temp\opencode\rt-fabric-1.21.11\` (Installer, Libs, Client+Assets, Server intakt). Debug-Replikat `test-real-server.ps1` (StartArgs, funktioniert). Workflow `.github\workflows\build.yml` hat build/verify/matrix-prep + runtime-test-Matrix. Verwendetes Test-JAR: `dist\multiversion\fabric\1.21.11\Statusmod-1.3.0-fabric-1.21.11.jar`.

**Weiter:**
1. **Andere Loader/Versionen testen** (forge, neoforge, quilt; 26.x) — `test-runtime.ps1` ist für fabric optimiert, SP-Teil für Forge/NeoForge nutzt Installer-Profil-JSON (anderer Pfad). Quilt-MC-Test läuft auf 1.21.11.
2. CI-Kosten-Entscheidung: ~450 MB Asset-Download pro Version → SP-Test evtl. nur auf neueste Version pro Loader beschränken (MP-Test auf alle Combos).
3. Parse-Check (`parse-test-runtime.ps1`) + sauberer Lauf ohne Cache-Verzeichnis.
4. Nicht committet (wie gehabt).

### 2026-08-01 — CI-Harness Lock-Race + Offsets gefixt → fabric 1.21.11 GRÜN (nicht committet)

**Neuer Root cause (Lock-Fehler `mp-server.out.log` "von einem anderen Prozess verwendet", ~10× im Boot-Burst):** Die `Register-ObjectEvent -Action`-Runspaces liefen bei Output-Bursts PARALLEL → gleichzeitige `Add-Content` auf dieselbe Datei → transiente Lock-Fehler + verlorene Zeilen. Isoliert NICHT reproduzierbar (Start-Job/FileStream-Tests sauber) — nur im echten Harness.

**Fix: Serialisierter Drain via async Runspaces (statt Event-Actions):**
- `Start-MinecraftProcess`: `Register-ObjectEvent` + `Add-Content` ENTFERNT. Jetzt ein eigener `[powershell]::Create()`-Runspace pro Stream, der `$p.StandardOutput.ReadLine()` in einer Schleife liest und mit `[System.IO.File]::AppendAllText` schreibt (1 Writer pro Datei → keine Races). Process + Log-Pfad via `.AddArgument()` übergeben (gleicher AppDomain → Objektreferenzen gültig), `catch {}` isoliert Lesefehler.
- Runspaces hängen als `SmRunspaces`-NoteProperty am Process; `Stop-MinecraftProcess` disposed sie nach Prozess-Ende (500 ms Settle vor Truncation des nächsten Laufs).

**⚠️ PITFALL (wichtig):** Rohe .NET-Background-Threads (`New-Object System.Threading.Thread([ThreadStart]{...})`) CRASHEN powershell.exe mit Exit-Code 5, sobald im Thread-Scriptblock eine Exception passiert (z. B. unter `Set-StrictMode`/`$ErrorActionPreference=Stop`). ASYNC-RUNSPACES sind der sichere Weg (Exceptions isoliert, keine Prozess-Crashes). Verifiziert: runspace-test.ps1 exit 0 + 30/30 Zeilen.

**Offset-Ordering-Fix:** Init-Zeile steht im Log VOR `Done` → `Wait-LogContains` muss init zuerst prüfen, sonst verschiebt der Done-Check den Offset an der Init-Zeile vorbei → False-Fail. MP-Sektion: `[StatusMod] Initializing` (60s) vor `Done (` (180s).

**modinfo-Pattern-Fix:** Em-Dash-Mangling variiert pro Lauf (5–7 Zeichen zwischen `StatusMod` und `Informationen`). Pattern `StatusMod.{0,5}Informationen` → `StatusMod.{0,20}Informationen`. `/status`-Literale verifiziert in `StatusCommand.java`: `status config show` → `"StatusMod configuration:"`, `status preset list` → `"Verfügbare Presets:"` (Harness-Pattern `Presets:` matcht auf ASCII).

**Verifiziert (fabric 1.21.11, Java 25):** MP komplett grün (Init, Done, modinfo, status preset list, status config show) UND SP grün (`CI_Test joined the game`). `result.json`: `passed=true`, mp+sp `passed=true`, keine Errors, Exit 0. Keine Lock-Fehler mehr.

### 2026-08-01 — Shop-Features komplett entfernt (Dashboard + Bot + Supabase) ✅ (nicht committet)

**Entscheidung:** Alle Shop-Features raus. **API-Key-System BLEIBT** — der Mod braucht ihn zwingend für `/code` (`CodeCommand.java` ruft `POST /api/auth/verify-code` mit `x-api-key`, Scope `check`). Nur die toten Shop-Scopes (`products`, `purchases`, `check-status`) wurden entfernt.

**Dashboard gelöscht (8 Dateien):**
- `app/api/shop/{products,statuses,purchases,check,check-status}/**` — alle 7 Routen
- `app/dashboard/shop/page.tsx`

**Dashboard editiert:**
- `components/sidebar.tsx`: Shop-Nav-Item entfernt
- `app/dashboard/page.tsx`: "Shop Settings"-Quicklink entfernt
- `app/layout.tsx`: Description ohne ", and shop"
- `lib/validators.ts`: `CreateProductSchema`, `CreateStatusSchema`, `productKeyRegex`, `statusTextRegex`, `validatePathProductKey`, `validatePathStatusText` entfernt; `ApiKeyCreateSchema`-Scopes → `["check","audit","*"]`
- `app/api/players/export/[uuid]/route.ts`: `purchases`/`player_status_purchases`-Queries entfernt
- `app/api/players/[server_id]/[uuid]/route.ts`: Anonymize-Updates für Shop-Tabellen entfernt

**Discord-Bot (`C:\Users\Steven\Desktop\Status mod bot`):**
- `cogs/shop.py` gelöscht; `main.py`: `"cogs.shop"` aus COGS; `api/client.py`: `get_shop_products`, `check_purchase`, `get_purchases`; `cogs/info.py`: `/shop`- und `/shop-check`-Zeilen aus Help; `cogs/keys.py`: `VALID_SCOPES` → `["check","audit","*"]`. `py_compile` aller Dateien OK.

**Supabase (Ref `ujksgdbfczbgtifiovuw`, Migration `drop_shop_tables`):** `DROP TABLE` für `player_status_purchases`, `purchases`, `shop_statuses`, `shop_products` (waren alle leer). Verifiziert via list_tables — `api_keys` (3 Keys) unangetastet.

**Doku:** `docs/dashboard-plan.md` — Shop-Tabellen/Endpunkte/Stripe-Sektionen gestrichen bzw. mit "ENTFERNT 2026-08-01" markiert; `api_keys`-Scope-Liste + Bot-Command-Tabelle + Env-Vars + Rate-Limits + Monitoring + Secret-Rotation bereinigt.

**Verifiziert:** `npm run build` (mit `NODE_OPTIONS=--max-old-space-size=4096`) → EXIT 0, keine Shop-Routen/-Seiten mehr in der Build-Ausgabe. Keine Stripe-/Shop-Referenzen mehr in deps, `.env.local` oder Code. **Nicht deployed/committet.**

### 2026-08-01 — Setup: API-Key Auto-Delivery (kein manueller Eintrag mehr) ✅ (nicht committet)

**Problem:** Der Claim (`POST /api/auth/setup`) erzeugte den `sm_`-API-Key und schickte ihn nur an den Browser — der Mod speicherte nur `serverId`, `config.apiKey` blieb leer. Manueller Copy-Paste in `config.json` nötig.

**Lösung — Mod pollt den Key nach dem Claim automatisch:**

- **Neue Tabelle `setup_pending_keys`** (Migration `create_setup_pending_keys`): `server_id uuid PK REFERENCES servers(id) ON DELETE CASCADE`, `key_encrypted text`, `created_at timestamptz`. RLS enabled.
- **Claim-Route** (`app/api/auth/setup/route.ts`): legt nach dem API-Key-Insert den rohen Key mit `encryptSecret()` (AES-256-GCM, SERVICE_ROLE_KEY-deriviert) in `setup_pending_keys` ab.
- **Neue Route `GET /api/auth/setup/pending-key?server_id=<uuid>`** (`app/api/auth/setup/pending-key/route.ts`): Bearer `MOD_SETUP_SECRET` (wie init), Rate-Limit `setup_pending:CODE_GENERATION`, validiert UUID. Findet Key → `{pending:true, api_key}` und löscht die Zeile (one-time Fetch). Kein Key → `{pending:false}`.
- **SetupManager.java**: startet nach `register()`-Erfolg einen Poll-Thread (`statusmod-key-poll`): alle 30s `GET pending-key`, bei `api_key` → `config.apiKey = key; config.save()` und fertig. Timeout 24h (= Code-Ablauf). **Wichtig:** `runIfNeeded()` pollt auch weiter, wenn `serverId` schon gesetzt ist, aber `apiKey` leer — d.h. Server-Neustart nach Claim holt den Key ebenfalls nach.
- **Setup-Seite** (`app/setup/page.tsx`): Text auf "auto-delivered (≤30s), Copy nur Fallback" umgestellt.

**Verifiziert:** `npm run build` EXIT 0, Route `/api/auth/setup/pending-key` in Build-Ausgabe; `:common:compileJava` EXIT 0. SetupManager nutzt `JsonParser` (Gson, vorhanden). **Nicht deployed** — live muss `vercel deploy --prod` laufen, damit Mod+Route funktionieren. Migration ist schon auf der DB.

### 2026-08-01 — Feature-Toggles + Supabase-Sync mit JSON-Fallback ✅ (nicht committet)

**Anforderung:** Jedes Feature pro Server ein-/ausschaltbar (config.json + In-Game-Command) und Spielerdaten-Sync (Status, Badge, Mute, Block) mit Dashboard-Supabase; bei Supabase-Ausfall funktioniert alles über die lokalen JSON-Dateien weiter (JSON bleibt Quelle der Wahrheit, Sync ist best-effort).

**Feature-Toggles (`ModConfig.java`):**
- `features`-Map, `FEATURE_KEYS` = `["status","badge","afk","block","mute","presets","code","sync"]`.
- `isEnabled(key)` defaultet auf `true`, wenn der Map-Eintrag fehlt (kein Migration-Bruch); `setFeature(key, enabled)`; `normalize()` füllt fehlende Keys mit TRUE.
- **Guards in allen Commands:** `StatusCommand.setStatus/applyPreset/saveCustomPreset/mutePlayer/unmutePlayer/setPlayerBadge/clearPlayerBadge/showPlayerBadge` → `status`/`badge`/`mute`/`presets`; `SettingsCommand.update` + `ColorCommand.setColor` → `status`; `CodeCommand.handleCode` → `code`; `BlockCommand.blockPlayer/unblockPlayer` → `block`.
- `/status feature list` und `/status feature <key> <on|off|true|false|an|aus>` mit Suggestions; `/status config show` listet alle Features.
- `StatusLifecycle`: AFK prüft zusätzlich `config.isEnabled("afk")`. `StatusTeamUtil`: render nur Status-Text wenn `statusEnabled`, Badge-Pfad prüft `isEnabled("badge")`.

**Sync-Architektur (Mod→Dashboard push, Dashboard→Mod pull):**
- **Mod (`common/.../sync/SyncManager.java`, neu):** Daemon-Thread, alle 30s. Push → `POST /api/players/<serverId>/sync` (x-api-key, Scope `check`): alle Spieler (uuid, username, status, color, settings inkl. brackets/beforeName/fontStyle/statusWords/statusByWorld/colorByWorld/badge), Mutes, Blocks. Danach Pull → `GET /api/players/<serverId>/sync?since=<watermark>`: wendet Dashboard-Mutes (`muteUntil` nur wenn länger), Blocks und Status-Änderungen lokal an. Watermark `config.lastSyncAtMs` (persistiert) wird nach jedem Pull auf `server_time` gesetzt.
- `updateOnlineNames(MinecraftServer)` wird aus `StatusLifecycle.onServerTick` gepflegt (Username-Map wird im Server-Thread gefüllt, Sync-Thread liest nur → Thread-Safety).
- `start()` läuft NUR wenn `isEnabled("sync")` UND `dashboardUrl`/`serverId`/`apiKey` gesetzt; bei HTTP-Fehler nur Log + nächster Zyklus → nie Game-Thread blockieren, nie Daten löschen.
- `MutedPlayers.muteUntil(uuid, untilMs)` neu (absolute Zeit; mergt nur wenn länger als bestehend).

**Dashboard:**
- **Neue Route `app/api/players/[server_id]/sync/route.ts`:** POST = Bulk-Upsert `players` (`onConflict: "server_id,uuid"`, setzt `updated_at`), max 200 pro Aufruf; `muted_until=0` = Löschmarker (Unmute), `blocked[].active:false` = Unblock; muted/blocked upsert mit `onConflict: "uuid,server_id"`. GET = Pull: `players` (optional `since`), komplette `muted`+`blocked`, plus `admin_actions` und `server_time`.
- Scope: `requireApiKey(request, "check", serverId)` — bewusst `check` statt neuem `sync`-Scope, weil bestehende Auto-Keys (Claim) nur `check` haben. `lib/validators.ts` hat zusätzlich `"sync"` in `ApiKeyCreateSchema.scopes` (optional, unbenutzt).
- **Migration `players_add_updated_at` (angewendet):** `ALTER TABLE public.players ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now()`. Unique `players_server_id_uuid_key` existiert bereits (verifiziert). Dashboard-Mute/Status-Routen setzen `updated_at` NICHT explizit (nur Mod-Push) — bekanntes Detail.

**Verifiziert:** `:common:compileJava` EXIT 0; `npm run build` EXIT 0 (Route `/api/players/[server_id]/sync` in Build-Ausgabe). **Nicht deployed/committet** — live: `vercel deploy --prod` + Mod-Neubuild.

**Offene Punkte (bewusst so):**
- Dashboard `updated_at` wird nur vom Mod-Push aktualisiert (Admin-PATCH-status/mute berühren `players.updated_at` nicht) → Pull-Watermark deckt Status-Änderungen vom Dashboard evtl. nicht ab; Dashboard→Mod fließt hauptsächlich Mutes/Blocks.
- Kein manueller Sync-Trigger (nur alle 30s); kein `/status feature`-Reload der Sync-Konfiguration ohne Server-Restart.

### 2026-08-01 — Tot-Code-Aufräum ✅ (nicht committet)

**Auftrag:** „lösche jeden toten code" — Java + Dashboard, verifiziert via `:common:compileJava` + `npm run build` (beide EXIT 0).

**Java gelöscht (4 Klassen):** `PlatformLoader`, `Compat`, `common/api/IPlatform`, `common/api/IPlayer` (alter `common.api`-Stack; echtes Bootstrapping läuft über `PlatformServices`). Leeres `common`-Paket + `common/api` entfernt.

**Java gelöschte Methoden:**
- `Platform.isDevelopmentEnvironment` + Implementierungen in `FabricPlatform`, `ForgePlatform`, `NeoForgePlatform`
- `PermissionUtil.isLuckyPermsAvailable` (LuckyPerms-Integration selbst bleibt — Felder + `checkLuckyPermsPermission` werden benutzt)
- `SettingsStorage.getAllUuids` (nur `getAllSnapshot` bleibt)
- `StatusLifecycle.onPlayerMove/onPlayerChat/onPlayerCommand` + `getPlayerServer` + `markActive` (nie verdrahtet; AFK-Wiederaufnahme läuft über `onServerTick`-Positions-Tracking)
- `StatusTextUtil.renderStatusText` 1- und 2-Arg-Overloads (nur 3-Arg wird von `StatusTeamUtil.applyStatus` genutzt)
- `ColorMapper.keys()` (nie aufgerufen)
- `ModConfig.StaffBadge` 3-Arg-Konstruktor (nur No-Arg wird von `StatusCommand` genutzt)

**Nicht entfernt (falsch als tot gemeldet):** `AuditLogger`-Import (StatusMod/BlockCommand/ColorCommand/StatusCommand benutzen ihn), `CommandSuggestions` (ColorCommand/SettingsCommand/StatusCommand), `StatusCommand` (ColorCommand ruft `checkCooldown`).

**Dashboard:**
- `lib/rate-limit-redis.ts` gelöscht (keine Referenzen; Lockout/Rate-Limits bleiben in-memory)
- `lib/validators.ts`: `ApiKeyRevokeSchema` entfernt (nie importiert), `"sync"` aus `ApiKeyCreateSchema.scopes` entfernt (kein `requireApiKey("sync",…)`-Aufrufer; Discord-Webhook-`case "sync"` ist Event-Typ und bleibt), `PlayerStatusUpdateSchema` entfernt (identisches Duplikat von `PlayerStatusFieldSchema`)
- `app/api/players/[server_id]/[uuid]/route.ts`: Import + Aufruf auf `PlayerStatusFieldSchema` umgestellt

**Verifiziert:** `:common:compileJava` EXIT 0; `npm run build` EXIT 0 (mit `NODE_OPTIONS=--max-old-space-size=4096`), Route `/api/players/[server_id]/sync` weiter in Build-Ausgabe. **Nicht deployed/committet.**

### 2026-08-02 — Tot-Code-Runde 2 (Java + Dashboard) ✅ (nicht committet)

**Auftrag:** „suche nach ungenutzen code oder nach code der durcheinander ist und fixe das" — zweite Analyse-Runde mit 3 Sub-Agents (Java tot, Dashboard tot, Duplikate/Chaos). Verifiziert via `:common:compileJava` + `npm run build` (beide EXIT 0).

**Java — ungenutzte Imports gelöscht (5):**
- `StatusCommand`: `net.minecraft.server.MinecraftServer`
- `CommandSuggestions`: `ColorMapper` + `CommandDispatcher`
- `PermissionUtil`: `java.nio.file.Paths`
- `AuditLogger`: `java.io.BufferedWriter`

**Java — No-op-Kette gelöscht:** `network/ModNetworking.java`, `registry/ModItems.java`, `registry/ModBlocks.java`, `registry/ModRegistries.java` (+ leere Ordner `network/`, `registry/`); Aufrufe aus `StatusMod.init()` entfernt. Kein Registrierungs-Stack nötig (alles über `PlatformServices`).

**Java — `Platform.registerItem/registerBlock` entfernt:** aus `Platform`-Interface + `FabricPlatform`/`ForgePlatform`/`NeoForgePlatform` + Aufrufen in `StatusMod.init()` (nie benutzt; Items/Blocks werden nicht registriert).

**Java — `FontMapper.apply` entfernt:** war No-op (gleicher In/Out); `StatusTextUtil.renderStatusText` entsprechend vereinfacht.

**Java — `safeBackupCorrupted` konsolidiert:** 4× identische private Kopie in `SettingsStorage`/`MutedPlayers`/`BlockedPlayers`/`CustomPresets` → eine Methode `StorageFiles.backupCorrupted` (neu). Ordner mit existierendem `statusmod_backup/` wird inkrementell durchnummeriert.

**Java — `CodeGenerator` (neu) `util/CodeGenerator.java`:** `ALPHABET` (A-Za-z0-9 ohne mehrdeutige Zeichen), `generate(int)` (secure random), `trimTrailingSlash(String)`. `CodeCommand` (CODE_LENGTH=8) + `SetupManager` (CODE_LENGTH=16) nutzen ihn.

**Java — `SettingsStorage`:** Inline-FQN `com.teufel.statusmod.StatusMod` → `import`.

**Dashboard — tote Exports gelöscht:**
- `lib/response.ts`: `notFound()` (kein Aufrufer)
- `lib/hash.ts`: `generateRandomCode()` (CodeCommand macht das serverseitig nicht mehr; Mod nutzt eigenes Schema)
- `lib/supabase.ts`: `getAnonClient()` (nie importiert)
- `lib/version.ts`: `getAllMigrations()` (nur interne Schema-Version-Query benutzt), `Migration`-Interface → nicht mehr exportiert
- `lib/rate-limit.ts`: `LOGIN_LOCKOUT` → nicht mehr exportiert (nur intern in der Datei)
- `lib/validators.ts`: `uuidRegex` → nicht mehr exportiert
- `lib/env.ts`: `Env`-Type → nicht mehr exportiert
- `lib/cookies.ts`: `REFRESH_TTL_SECONDS` → nicht mehr exportiert
- `lib/auth.ts`: `getSession()`, `DataAccess` → nicht mehr exportiert

**Dashboard — `RATE_LIMITS`-Entries entfernt:** `API`, `CODE_VERIFY`, `KEY_REVOKE`, `SHOP` (keine Aufrufer). Bleiben: `LOGIN`, `CODE_GENERATION`, `TOTP_VERIFY`.

**Dashboard — doppelte Zod-Schemas vereinheitlicht (`lib/validators.ts`):**
- `KeyLeakSchema` gelöscht → `app/api/keys/leak/route.ts` nutzt `LoginCodeSchema` (identischer Body)
- `LogoutSchema` gelöscht → `app/api/auth/logout/route.ts` nutzt `RefreshTokenSchema` (identischer Body)

**Dashboard — `lib/hash.ts` Dedupe:** private `sha256Hex()`; `hashIp`/`hashUserAgent`/`hashAuditUuid` rufen sie auf.

**Dashboard — dynamische Imports → statisch:** `app/api/keys/route.ts` + `app/api/auth/2fa/disable/route.ts` importierten `tooMany()` via `await import("@/lib/response")` → jetzt statischer Import.

**Dashboard — Audit-Bugfix (echter Bug, richtig herum):** `lib/audit.ts` schrieb `details:` (Plural) in `audit_log`, aber die DB-Spalte heißt `detail` (Singular, `text`, max 1000) → Insert schlug fehl / Audit-Einträge kamen nie an. Fix: `lib/audit.ts` schreibt jetzt `detail: JSON.stringify(params.details)`; die Audit-Page (`entry.detail`, String) war korrekt und bleibt unverändert. **Achtung:** `detail`-Werte werden JSON-Stringified, Check-Constraint ≤1000 Zeichen — lange Records könnten fehlschlagen (bewusst, nur kurze Infos: `{keys:[...]}`, `{duration_minutes,...}`).

**Dashboard — `DATABASE_URL` aus env-Schema entfernt (`lib/env.ts`):** wird nirgends gelesen (Supabase-Zugriff über `NEXT_PUBLIC_SUPABASE_URL` + `SERVICE_ROLE_KEY`). Env-Schema verlangt jetzt nur noch die tatsächlich benutzten Vars.

**Dashboard — tote Tailwind/CSS entfernt:**
- `tailwind.config.ts`: komplette `colors`-Paletten `bedrock`/`deepslate` gelöscht (nie als Utility-Klassen benutzt; Styles nutzen arbitrary values `bg-[#…]`)
- `app/globals.css`: tote CSS-Vars `--bg-tertiary`, `--text-secondary`, `--text-muted`, `--accent`, `--accent-dim`, `--border`, `--danger`, `--success`, `--warning` und `--bg-secondary` entfernt (nur `--bg-primary` + `--text-primary` werden via `var()` benutzt); `.btn-danger`-Komponente entfernt (kein Aufrufer — Danger-Buttons nutzen Inline-Klassen)

**Verifiziert:** `:common:compileJava` EXIT 0 (schon vor den Dashboard-Edits); `npm run build` EXIT 0 (mit `NODE_OPTIONS=--max-old-space-size=4096`), `Compiled successfully`, `/api/players/[server_id]/sync` weiter in Build-Ausgabe. **Nicht deployed/committet.**

**Nicht angefasst (bewusst):** `isDedicatedServer`-Reflection near-copy in Forge/NeoForgePlatform (unterschiedliche Internals), 3-fach duplizierte Command-Registrierung in Loader-Entrypoints (Risiko/Nutzen), generischer `JsonStore`-Helper für die 4 Storage-Klassen, `lib/rate-limit-redis.ts`-Wiederverdrahtung.

### 2026-08-02 — MC 26.x Permission-Fix + CI-Harness StrictMode-Fix ✅ (nicht committet)

**User-Report:** Im Fabric-26.2-Testserver meldete `/status afk` „keine Berechtigung", obwohl Delle2211 Level-4-OP ist.

**Root-Cause-Analyse (per gz-Logs `C:\Users\Steven\Downloads\Status Test\logs\`):**
- MC 26.x hat `hasPermission(int)` auf `CommandSourceStack`/`CommandSource` ENTFERNT → neues Modell: `permissions()` → `PermissionSet`/`LevelBasedPermissionSet` (`net.minecraft.server.permissions.*`). OP-Level aus `ops.json` → `PermissionLevel.byId(int)` → `LevelBasedPermissionSet.forLevel(...)`. Statisch verifiziert per javap: OWNER-Set → `hasPermission(COMMANDS_OWNER)` = true via `isEqualOrHigherThan`.
- Log `2026-07-31-6.log.gz` (Debug-Build, perm-debug-Lines) beweist: Level-Pfad warf `IllegalAccessException: ... cannot access a member of class net.minecraft.server.permissions.LevelBasedPermissionSet$1` (anonyme Impl-Klasse ist package-private), aber der COMMANDS-Fallback lieferte `hasPermission(COMMANDS_OWNER)=true` → Permission funktionierte schon, nur der Level-Pfad schlug still fehl.
- Sessions 1–5 (älteres JAR ohne 26.x-API) hatten KEINE perm-debug-Lines → dort kam die „keine Berechtigung"-Meldung. Deploytes JAR `Statusmod-1.3.0-fabric-26.2.jar` war bereits der saubere Build (kein `perm-debug`, aber `LevelBasedPermissionSet`-Refs).

**Fix 1 — `common/src/main/java/com/teufel/statusmod/util/PermissionUtil.java`:**
- `cachedLevelMethod.setAccessible(true)` in `resolveNewPermissionApi()` (in eigenem try/catch). Behebt die `IllegalAccessException` beim `level()`-Invoke auf der package-privaten anonymen Klasse `LevelBasedPermissionSet$1`. COMMANDS-Fallback bleibt als Absicherung.
- **Wichtig (Java-Reflection-Gotcha):** Öffentliche Interface-Methoden auf nicht-öffentlicher Impl-Klasse werfen beim `invoke()` IllegalAccessException → `setAccessible(true)` auf dem gecachten Method-Objekt nötig. Default-Methoden (`hasPermission`) sind davon nicht betroffen, abstrakte Overrides (`level()`) schon.

**Fix 2 — `scripts/ci/test-runtime.ps1` (forge/neoforge StrictMode-Crash):**
- Fehler war `Die Eigenschaft "fabric_api_url" wurde nicht gefunden` — unter `Set-StrictMode -Version 2` crasht `$resolved.fabric_api_url` auf der `[ordered]`-Dict, wenn der Key fehlt (forge/neoforge setzen ihn nie).
- L548/L721: `if ($resolved.fabric_api_url)` → `if ($resolved.PSObject.Properties["fabric_api_url"])` (robust für OrderedDictionary UND PSCustomObject).
- `-FabricApiUrl`-Pfad (fabric/quilt): setzt zusätzlich `fabric_api_version = [System.IO.Path]::GetFileNameWithoutExtension($url) -replace '^fabric-api-', ''` — sonst hätten L549/L722 bei gesetztem `fabric_api_url` gecrasht (Property war da nicht gesetzt).

**Rebuild + Deploy (fabric 26.2):**
- `.\gradlew.bat -p Loader\fabric26.1 clean build --no-daemon` mit `JAVA_HOME=C:\Program Files\Java\jdk-25.0.3` → BUILD SUCCESSFUL. Classfile verifiziert: `setAccessible` + `LevelBasedPermissionSet`-Refs enthalten.
- Deployed nach `C:\Users\Steven\Downloads\Status Test\mods\Statusmod-1.3.0-fabric-26.2.jar` (112342 Bytes), altes als `Statusmod-1.3.0-fabric-26.2.jar.bak` gesichert.

**PITFALLS (neu dokumentiert):**
- `loader-versions.json` hat KEINEN top-level `fabric`-Key (nur forge/neoforge/quilt). Fabric-Versionen liegen im `build-multiversion.ps1` (`$FabricExtraVersions = 26.2, 26.1, 26.1.1, 26.1.2`).
- `Loader\fabric26.1` baut intern **MC 1.21.11** (build.gradle hardcoded `minecraft "com.mojang:minecraft:1.21.11"`), nicht 26.x! Der Name „26.1" ist Legacy. Das JAR läuft auf 26.x, weil `fabric.mod.json` `minecraft: >=1.21.11` hat und 26.x unobfuscated ist.
- Fabric 26.x-JARs werden vom Multiversion-Skript einfach je Zielversion umbenannt (`Statusmod-1.3.0-fabric-{26.2|26.1|...}.jar`).

**Verifiziert:** `:common:compileJava` EXIT 0; fabric26.1 Build EXIT 0; PS-Syntax-Parse von `test-runtime.ps1` OK. **Nicht committet.**

### 2026-08-14 — Modrinth Compliance (AI-Policy + Disclosures) + Development-Branch ✅ (gepusht)

**Modrinth Content Rules Update (2026-08-13):** Neue Sektion 6 „Usage of Generative AI" + neue Content Disclosures (Details-Tab auf Projektseite). 45-Tage-Grace-Periode, Reports erst ab **27.09.2026**.

**StatusMod-Status:**
- **„Contains AI-generated content" → MUSS gesetzt werden** (6.1.1: substanzieller Anteil AI-Code; 6.1.4: Beschreibung mit AI). Erlaubt, weil AI-assistiert (menschliche Architektur + Review). **Entschiedener Disclosure-Text:** „Development assisted by an AI coding tool; human-authored architecture and review. Description written with AI assistance." → Optionen **Code** + **Text**.
- **6.2.1 (AI-Bilder verboten):** Icon `docs/statusmod-icon.png` ist Script-generiert (`gen-icon.py`, kein AI-Bild) → OK.
- **6.2.2 (primär/vollständig AI):** nicht betroffen.
- **„Contains telemetry" → NICHT jetzt fällig** (veröffentlichte 1.3.0 hat keinen Sync). Pflicht erst beim Release einer Version mit `SyncManager`/Dashboard → dann **Opt-in** + Datenliste: Player UUID, Username, Status-Text/-Farbe, Settings (brackets, position, fontStyle, statusWords, statusByWorld, colorByWorld, badge), Mute-State, Block-Liste. HTTPS an `dashboardUrl` (config.json), off by default, enthält PII.
- README/Modrinth-„No external telemetry"-Angabe stimmt für den veröffentlichten Stand, muss aber **VOR dem Sync-Release** korrigiert werden (Regel 1.11/2).
- Nicht nötig: Advertising, Paid features (Stripe entfernt), Derivative, Photosensitivity, External system interactions.

**Entwicklungs-Workflow (dev):**
- Neuer Branch **`development`** (von `main`), gepusht: `origin/development`, Commit `0f88360` (133 Dateien: Dashboard, Sync/Setup-Flow, Quilt-Support, Runtime-Test-Harness, Tot-Code-Aufräum Runde 1+2).
- **Kein Build/Upload auf `development`:** `build.yml` (`push: branches: [main]`) + `fabric-release.yml` (`main`/`master`/`v*`-Tags) feuern dort NICHT. Publishen läuft nur über `main`-Push oder manuelles `workflow_dispatch`.
- `dashboard/.env.local` ist gitignored → Secrets nie committen.

### 2026-08-14 — AI-Icon-Flag + deterministisches Icon ✅ (gepusht: f29b7cf, c12ace5)

**Problem:** Modrinth lehnte das alte Icon (`docs/statusmod-icon.png`, 1254×1254) als **AI-generiert** ab (Rule 6.2.1, "Using AI-generated images to represent your project is not allowed … workaround may lead to suspension"). Altes Bild war extern erstellt (kein Script im Repo) → Backup unter `%TEMP%\opencode\statusmod-icon-old-ai.png`.

**Lösung — neues deterministisches Icon via Pillow (`scripts/local/gen-icon.py`):**
- Keine AI-Anteile → kein Verstoß. Design: obsidian abgerundetes Quadrat, Titel **"STATUS PLAYER"**, türkise Trennlinie, 4 Tablist-Zeilen `Status (farbe) + Name (weiß)`:
  - Bauen (grün) · Max | Farmen (gelb) · Tom | AFK (rot) · Noah | PvP (türkis) · Finn
- Verlauf: Nutzer iterierte über Design (Shield→Ping-Balken→Status-Wörter; Namen Steve/Alex/Herobrine wegen Mojang-IP raus, dann Delle2211→generisch). Titel musste verkleinert werden (46px), sonst wurden S/R abgeschnitten.
- `gen-icon.py` Argumente: `--out <pfad>`, `--size <px>` (Layout skaliert per size/512), `--transparent` (RGBA-Ecken). Font: `C:\Windows\Fonts\arialbd.ttf`.
- **In-JAR-Icons alle regeneriert (256×256 RGBA, transparent):** `fabric/src/main/resources/assets/statusmod/icon.png`, `forge/src/main/resources/logo.png`, `Loader/forge26.1/src/main/resources/logo.png`, `neoforge/src/main/resources/logo.png`. Quilt nutzt die fabric-Icon-Ressource.

**SVG-Experiment (verworfen):** PNG→SVG-Vektor via potrace 1.16 (portable, `%TEMP%\opencode\trace\potrace-1.16.win64\`), Farb-Tracing + Verifikation (svglib/cairo + Chrome). `docs/statusmod-icon.svg` (3,9 MB) wurde erstellt, verifiziert und auf Nutzer-Wunsch wieder gelöscht. Modrinth nimmt ohnehin nur PNG.

**Commits auf `development`:** `f29b7cf` (Icon + gen-icon.py + SVG-Deletion), `c12ace5` (Loader-Icons + Size/Alpha-Refactor). **Nicht auf `main`.**

**Offen (User-Aktion):**
1. ~~Modrinth-Projektseite: neues Icon hochladen.~~ ✅ 2026-08-14 erledigt
2. ~~Disclosure "Contains AI-generated content" (Code + Text) setzen.~~ ✅ 2026-08-14 erledigt (gesetzt vor 2026-09-27-Grace-Frist)
3. README-/Modrinth-"No external telemetry"-Text vor erstem Sync-Release korrigieren (noch offen).

### 2026-08-19 — Dashboard Features: Dark/Light Theme + Real-time Updates ✅ (gepusht: 57967fa)

**Feature 1 — Dark/Light Theme Toggle:**
- `tailwind.config.ts`: `darkMode: "class"` added
- `app/globals.css`: Full CSS variable system — light (`:root`) and dark (`.dark`) themes with 200ms transition. Variables: `--bg-primary`, `--bg-card`, `--bg-hover`, `--bg-input`, `--border`, `--text-primary`, `--text-secondary`, `--text-muted`, `--accent`, `--accent-dim`, `--accent-hover`, `--accent-border`, `--grid-opacity`
- `components/theme-provider.tsx`: `ThemeProvider` + `useTheme()` hook with localStorage persistence (`theme` key), system preference detection (`prefers-color-scheme`), SSR-safe (`suppressHydrationWarning`)
- `app/layout.tsx`: `<html className="dark" suppressHydrationWarning>`, wraps children in `<ThemeProvider>`
- `components/sidebar.tsx`: Theme toggle button (☀ Light Mode / ☾ Dark Mode) at bottom; all colors via CSS vars
- All 10 pages updated to use CSS variables instead of hardcoded colors:
  - `app/page.tsx`, `app/login/page.tsx`, `app/setup/page.tsx`
  - `app/dashboard/layout.tsx`, `app/dashboard/page.tsx`
  - `app/dashboard/players/page.tsx`, `app/dashboard/players/[uuid]/page.tsx`
  - `app/dashboard/config/page.tsx`, `app/dashboard/audit/page.tsx`, `app/dashboard/keys/page.tsx`
- `components/footer.tsx`: CSS var colors

**Feature 2 — Real-time Player Updates via SSE:**
- `lib/client/use-realtime.ts` (new): `useRealtimePlayers()` hook — connects to `/api/events` SSE endpoint, auto-reconnects on disconnect (5s backoff), returns `{players, connection}`. Passes access token as `?token=` query param (EventSource can't send headers).
- `app/api/events/route.ts` (updated): Now accepts `?token=<jwt>` query param for EventSource auth (fallback to `Authorization` header). Player limit raised from 10 → 100.
- `app/dashboard/page.tsx` (Overview): Shows "Realtime" stat card (Live/Connecting/Offline), "Live Players" section with clickable player chips (avatar + name + status) when players are online
- `app/dashboard/players/page.tsx` (Players list): "live" badge next to player count when SSE connected; merges SSE data with initial fetch (live data overwrites status/color/username)

**Dashboard Build:** `npm run build` ✅ — 39 API routes, 28 static pages, 0 errors

**Commits auf `development`:** `57967fa` (18 files, 494 insertions, 148 deletions). Nicht auf `main`.

**Offen (nächste Schritte):**
1. Deploy to Vercel (`vercel deploy --prod`)
2. Test theme persistence across page reloads
3. Test SSE reconnection after network interruption
4. Consider: SSE endpoint auth token refresh (JWT expires → EventSource won't reconnect with stale token)

### 2026-08-19 — Admin GUI (Player Heads) Bug-Iteration + MC 26.x Runtime-Analysis ✅ (nicht committet)

**Feature:** Admin-GUI (`/status gui [page] [search:query]`): virtuelle 6-Reihen-Chest, zeigt Player-Heads (skinned) als klickbare Slots. Admin-Klick = Action.

**UI-Build (5 Iterationen):** HeadBuilder (SkullOwner NBT → skinned heads) → HeadContainer (6-Reihen virtueller Chest) → PlayerHeadMenu (Base-Chest-Menu) → ClickablePlayerHeadMenu (ItemClick-Listener) → StatusGuiCommand (Command + Reflection für cross-version safety).

**Iteration 1 → 2 (Skinned Heads + Search):**
- `HeadBuilder.createHead(GameProfile, displayName)` baut `ItemStack` mit `SkullOwner`-Tag (Profile-WithSignature-NBT) — skinned heads statt grauer Steine.
- `StatusGuiCommand` arg `page:int` + `search:String` (greedy), paginiert über `PlayerHeadMenu.getHeadPage(...)`.
- `CommandSuggestions.COLOR_SUGGESTIONS`: 16 Farben + `#RRGGBB` + `rainbow`.

**Iteration 3 → 5 (Click-Actions + Bug-Fixes):**
- `ClickablePlayerHeadMenu`: Listener erkennt Klick (Slot wird leer → Item ging auf Cursor → Action → restore + Cursor clear).
- **Buggy Behavior (aktuell):** `mayPickup()` returned `true` → Items landeten im Cursor/Inventory des Admins. Restore via `setCarried(ItemStack.EMPTY)` funktionierte auf MC 26.x NICHT zuverlässig.
- **Fix-Versuche:** `menu.setCarried(ItemStack.EMPTY)` in try-catch; `savedHeads[]` + `wasPopulated[]` Tracking; `initialized[]`-Flag für Init-Phase.
- **User-Report (deployed auf Nitrado):** "rechtsklick legt items ins inventory, links-klickführt 'ungültige Farbe' aus".
- **Root Cause (analyisiert):** Links-Klickauf Head mit Name `Delle2211` → `/status <text>` matchte `status set Delle2211` → `parseStatusInput` nahm "set" als Status, "Delle2211" als Farbe → `isValidColorInput("Delle2211")` = false → "Ungültige Farbe". Der User wollte ANDERES Verhalten als implementiert.
- **User-Gewünschtes Verhalten:** Links-Klick = Ziel-Spieler-Status ändern (push), Rechts-Klick = Ziel-Spieler-Farbe ändern (push).
- **MC 26.x ClickType-Problem:** `AbstractContainerMenu.clicked()` Signatur: 1.21.11 = `(int, int, ClickType, Player)`, 26.x = `(int, int, ContainerInput, Player)`. Override mit `ClickType`-Param feuert auf 26.x NICHT (Loom kann nicht remappen, da `ClickType`≠`ContainerInput` im Intermediate).
- **Lösungsansatz (offen):** `quickMoveStack()` (Shift-Klick) für eine Action, normaler Klick für die andere — oder einfacher: beide Klicks machen dasselbe (Status+Farbe kopieren). Left/Right-Unterscheidung ist auf 26.x nicht zuverlässig möglich.

**MC 26.x Runtime-Pitfalls (aus Nitrado-Deploy + Multiversion-Build-Analyse):**

| Problem | Ursache | Fix |
|---------|---------|-----|
| `class_7157` → `NoClassDefFoundError` | Loom-Cache stale: Standalone `fabric26.1`-Build produzierte Bytecode mit Intermediary-Namen statt Mojang-Namen | Multiversion-Build (`build-multiversion.ps1`) räumt Loom-Cache auf → sauberer Bytecode |
| `Commands.performCommand` nicht gefunden | MC 26.x benennt um zu `Commands.performPrefixedCommand` | Reflection oder direkte Referenz (Loom remappt) |
| `ContainerListener` Signatur-Änderung | 1.21.11: `(menu, slot, newStack)`, 26.x: `(menu, slot, newStack)` — Parameteranzahl gleich, aber alte Version hatte `oldStack` als 4. Parameter | Common-Code nutzt 3-Param-Signatur → kompatibel |
| `ResolvableProfile` nicht instanziierbar | Sealed class, keine public-Konstruktoren | `ResolvableProfile.createResolved(GameProfile)` statische Factory |
| `ChatFormatting.getColor()` nicht vorhanden | MC 26.x entfernt | `FALLBACK_COLORS`-Map (hardcoded) |
| `ItemStack.getOrCreateTag()` entfernt | MC 1.21+ nutzt `DataComponents` | DataComponents-API |
| `setCarried(EMPTY)` wirkt nicht zuverlässig | MC 26.x"sync-Cursor" via `setCarried()` möglicherweise erst nach Broadcast sichtbar | Items flackern kurz im Cursor (minor UX) |

**Deployments:** User deployt manuell auf Nitrado-Server (kein lokaler Test-Server). JARs: `dist/multiversion/{fabric,forge,neoforge,quilt}/`.

**Architektur-Entscheidungen (bleibend):**
- `StatusTeamUtil.applyStatus()` via Reflection aufrufen (scoreboard via `server.getScoreboard()` reflection) — vermeidet Klassenreferenz-Probleme in Common-Code.
- `hasStatusPermission()` gibt `true` für ALLE Spieler wenn LuckyPerms NICHT installiert ist.
- `brackets`-Feld: `int` — 0=none, 1=`[]`, 2=`<>`; backwards-compatible via Gson Coercion.

**Offen (nächste Schritte):**
1. **ClickablePlayerHeadMenu Refactor:** Links/Rechts-Unterscheidung zuverlässig machen (Shift-Klick als Workaround ODER beide Klicks = Status+Farbe) → ✅ erledigt 2026-08-23 (s. unten)
2. **Item-Pickup-Problem lösen:** `mayPickup()` = false + Alternative Click-Detection ODER accepts "flashing cursor" als Known Issue → ✅ erledigt 2026-08-23 (s. unten)
3. Alle 20/20 Multiversion-Builds nochmal frisch laufen lassen nach GC-Änderungen
4. Deploy + User-Test auf Nitrado
5. Modrinth-Compliance: Telemetry-Disclosure vor Sync-Release

### 2026-08-23 — Admin GUI: Klick → Chat-Befehl ✅ (nicht committet)

**User-Anforderung:** `/status gui` — Klick auf Head = Befehl zum Status-/Farb-Ändern vorbereiten (Chat-Befüllung via klickbare Chat-Zeile).

**Finale Interaktion (Iteration 2, nach User-Feedback „bei jedem Head ist eine 2 daneben"):**
- **Linksklick (oder Rechtsklick — identisch) = Status ändern**, **Shift-Klick = Farbe ändern**.
- Der Count=2-Trick (2er-Stack für Links/Rechts-Erkennung) wurde ENTFERNT — Stack-Anzahl „2" war clientseitig sichtbar und nicht versteckbar. Links vs. Rechts ist serverseitig ohne clicked()-Override nicht unterscheidbar (beide leeren einen 1er-Stack).
- Shift-Klick-Erkennung: `quickMoveStack(Player, int)` Override — Signatur über alle MC-Versionen stabil; Vanilla QUICK_MOVE bricht die While-Schleife bei EMPTY-Return ab (kein Infinite-Loop); setzt voraus `mayPickup=true` (vorhanden). Return immer `ItemStack.EMPTY` → nichts wandert.

**Architektur (`common/gui/ClickablePlayerHeadMenu.java`):**
- Heads mit natürlicher Anzahl (count=1) in Slots, `mayPickup=true`, `mayPlace=false`.
- ContainerListener (`slotChanged`): Slot geleert = Klick → Restore Original-Head + Cursor clear → `handleHeadAction(slot, false)` (Status). Fremdes Item im Slot ("polluted") → Rescue auf Cursor + kein Action.
- `handleHeadAction(int slotIndex, boolean colorMode)`: 400ms-Debounce (Instance-Field), Permission-Check, Target-Name-Resolution, GUI-Close (Reflection, da `closeContainer()` PROTECTED in 1.21.11 UND 26.x), klickbare SUGGEST_COMMAND-Zeile `/status admin set <Name> ` bzw. `/status admin color <Name> `.
- ClickEvent/HoverEvent 3-Stufen-Reflection (Records 1.21.5+ / statische Factories alt / Konstruktor-Fallback).
- Mid-Packet-Close sicher (Bytecode-Analyse handleContainerClick: re-read von player.containerMenu nach clicked(), kein NPE).

**Neuer Command: `/status admin color <player> <color>`** (`StatusCommand.adminSetColor`): Feature-Guard, Online-Check, `ColorMapper.isValidColorInput`, settings.color + Storage + applyStatus + Audit + Messages.

**Forge-Override gelöscht:** `Loader/forge26.1/src/.../ClickablePlayerHeadMenu.java` (alte ContainerInput-Logik) entfernt + exclude aus build.gradle → Common-Version läuft überall.

**Lore:** „Linksklick: Status ändern" / „Shift-Klick: Farbe ändern" (grau, modern + Legacy-Builder).

**Verifiziert & deployed:** `:common:compileJava` EXIT 0; Multiversion-Build 20/20 ok; JAR-Checks (handleHeadAction + Shift-Lore drin, keine Rechtsklick-Lore). Deployed: `C:\Users\Steven\Downloads\Status Test\mods\Statusmod-1.3.0-fabric-26.2.jar`. **In-Game-Test ausstehend.**

**Pitfalls (neu):**
- ForgeGradle-LSP im Editor meldet falsche Gradle-Version (8.9 vs 9.3+) — ignorieren, CLI nutzt Wrapper.
- LSP kann Classpath verlieren („Player cannot be resolved" in intakten Dateien) — Gradle-Compile ist Quelle der Wahrheit.
- Disk-Space: Multiversion-Build braucht >6 GB frei; `~\.gradle\caches\fabric-loom` (9 GB) ist gefahrlos löschbar (regeneriert).

### 2026-08-23 — Dashboard→Ingame-Sync-Fix + Steve-Heads ✅ (nicht committet)

**User-Reports:** (1) „beim dashboard sind steve heads und ein richtiger player head", (2) „wenn ich etwas im dashboard ändere wird es ingame nicht übertragen".

**Root Causes:**
1. **Steve Heads:** Dashboard nutzte `https://mc-heads.net/avatar/<uuid>` — mc-heads löst nur **Premium-UUIDs** auf; Offline-Mode-Server erzeugen v3-Offline-UUIDs → Mojang-DB-Miss → Steve-Fallback.
2. **Sync-Lücke (3 Teile):**
   - `PATCH .../[uuid]/status` schrieb **kein `updated_at`** → Pull-Watermark (`updated_at >= since`) sah Dashboard-Status-Edits nie (bekanntes offenes Item aus 2026-08-01).
   - Mod-Pull **ignorierte** Empty-Status (Löschen unmöglich) und Color-only-Changes (`!status.equals(ps.status)`-Guard).
   - Loop-Reihenfolge push→pull: der periodische Push überschrieb Dashboard-Edits mit lokalem Stand (+bumped updated_at), bevor der Pull sie je sehen konnte.

**Fixes:**

| Datei | Änderung |
|-------|----------|
| `dashboard/app/api/players/[server_id]/[uuid]/status/route.ts` | `updated_at = now()` in Update- UND Insert-Pfad |
| `dashboard/app/dashboard/page.tsx`, `players/page.tsx`, `players/[uuid]/page.tsx` | Avatar-URL → `mc-heads.net/avatar/${encodeURIComponent(p.username || p.uuid)}` (Name-basiert: Premium-Namen lösen korrekt auf, egal ob Server offline-mode ist; Non-Premium bleibt Steve) |
| `common/.../sync/SyncManager.java` | Loop jetzt **pull() VOR push()** (Pull wendet Dashboard-Stand lokal an, Push lädt dann konsistenten Merged-State hoch); Player-Apply null-aware pro Feld (JSON-null = skip, `""` = explizites Clearen, Color-only geht durch); neuer Helper `applyOnline(serverRef, uuid, ps)` |

- `applyOnline`: stashed `volatile MinecraftServer serverRef` (gesetzt in `updateOnlineNames`, läuft im Server-Thread), `server.execute(...)` → Scoreboard-Änderung im Server-Thread, nur wenn Feature `status` enabled + Spieler online → **Live-Anwendung ohne Relog**.
- Rest-Race (Edit exakt zwischen Pull und Push desselben Zyklus) akzeptiert — nächster Zyklus zieht nach; admin_actions bleiben ungenutzt (target_uuid_hash nicht auflösbar).

**Verifiziert & deployed:** `:common:compileJava` EXIT 0; `npm run build` EXIT 0; Multiversion 20/20 ok; `vercel deploy --prod` EXIT 0 (https://statusmod-dashboard.vercel.app); Testserver-JAR `Statusmod-1.3.0-fabric-26.2.jar` (160831 Bytes, 15:35) deployed, `applyOnline` im Bytecode verifiziert.

**Offen:** In-Game-Test (Dashboard-Status-Edit ≤30s ingame sichtbar? Heads für Premium-Namen real?). Nicht committet.

### 2026-08-23 Runde 2 — Presence/Count/Username/RGB-Fixes ✅ (nicht committet)

**User-Reports:** (1) Players-Anzahl falsch, (2) nur 2/5 haben echten Kopf, (3) eigener Head Steve trotz BastiGHG-Skin, (4) RGB wird nicht richtig angezeigt, (5) Username verschwindet wenn Spieler offline geht.

| Problem | Root Cause | Fix |
|---------|-----------|-----|
| Username verschwindet offline | Sync-Route schrieb `username: p.username ?? null` → jeder Push (30s) überschrieb Usernamen offlineer Spieler mit NULL. Mod omitet username schon korrekt, aber Route forcierte null | Rows jetzt konditional: username/status/color/settings nur schreiben wenn non-null (PostgREST-Upsert lässt fehlende Spalten unangetastet) |
| Players-Anzahl falsch | Overview „Players" zählte SSE-Rows = ALLE bekannten Spieler (Tabelle), nicht Online. Players-Seite „X player(s)" ebenso missverständlich | **Presence-Feature:** Migration `players_add_online_presence` (`is_online boolean default false`, `last_seen timestamptz`); Mod-Push sendet `online` pro Spieler (aus onlineNames); Sync-Route schreibt is_online+last_seen; SSE `/api/events` filtert `.eq("is_online", true)` → Live-Feed/Live-Zähler echt; Players-Header jetzt „X known · Y online" |
| Zeilen immer halb transparent | `opacity: 0.5` hardcoded auf allen Player-Rows (sollte Offline-Dim sein, nie verdrahtet) | opacity an `is_online` gebunden + grüner/grauer Presence-Dot neben Username |
| Farben falsch angezeigt | Players-Liste nutzte rohen `p.color` als CSS (`dark_gray`, `light_purple`, `rainbow`, `animated` sind ungültiges CSS → Punkt unsichtbar); Detail-Seite hatte eigene lokale Map ohne rainbow/hex-3-stellig | Neuer Helper `dashboard/lib/color.ts`: `cssColor()` mappt alle MC-Namen → HEX, #hex (3+6 stellig) durchgereicht, rainbow/animated → CSS-Gradient, reset/leer → neutral. Genutzt in players/page.tsx + [uuid]/page.tsx (lokale COLOR_HEX entfernt) |

**Kopf-Problematik (2/5 + BastiGHG-Skin):** mc-heads-by-name löst nur PREMIUM-Namen auf (Name muss in Mojang-DB existieren). Non-Premium-Spieler → Steve. Ein Client-seitig ausgewählter Skin (z. B. BastiGHG-Skin via Skin-Mod/Launcher) ist dem Server UNBEKANNT → prinzipiell nicht per Name/UUID auflösbar. Einzig saubere Lösung: Custom-Avatar-Feature (z. B. `/status avatar <url>` + DB-Spalte + Dashboard rendert Face-Crop aus Skin-PNG) — noch NICHT gebaut, User-Entscheidung offen.

**Verifiziert & deployed:** Migration applied (Supabase ujksgdbfczbgtifiovuw); `npm run build` EXIT 0; `vercel deploy --prod` EXIT 0; Multiversion 20/20 ok; Testserver-JAR neu (160868 Bytes, 16:03). **Nicht committet.**

**Wichtig:** Nach Deploy zeigen Live-Feed/Online-Zähler erst nach dem nächsten Mod-Push (~30s) korrekte Werte; alte Zeilen starten mit is_online=false.



### 2026-08-23 Runde 3 - Custom-Avatar-Feature (/status avatar) ✅ (nicht committet)

**Feature:** Spieler setzen ihren Dashboard-Kopf selbst: `/status avatar <name|url|off>` (selbst) oder `/status avatar <player> <value>` (2 Args = Admin). Loest das Steve-Head-Problem fuer Non-Premium-Spieler endgueltig.

**Semantik:**
- Wert startet mit `http://`/`https://` → direkte Skin-PNG-URL, Dashboard cropt das Face per CSS (backgroundSize 8x, pixelated).
- Sonst: SpielerNAME (Regex `[A-Za-z0-9_]{1,16}`) → Dashboard rendert `mc-heads.net/avatar/<name>` (loest Premium-Namen wie BastiGHG auf).
- `off`/`clear`/`reset` → zurueck zum Standard (Username-Fallback).
- Validierung: max. 160 Zeichen, URL ohne Leerzeichen; Fehlermeldungen auf Deutsch.

**Aenderungen:**

| Datei | Aenderung |
|-------|-----------|
| `common/.../storage/PlayerSettings.java` | Neues Feld `public String avatar = ""` (Gson persistiert automatisch) |
| `common/.../command/StatusCommand.java` | `avatar`-Literal mit greedyString-Args (1 Arg = selbst, 2 Args = Admin via getPlayerByName, Online-Check); Methoden `setAvatar` + `applyAvatar`; Suggestion `off`. PITFALL: greedyString frisst alles → Player-Branch-Variante ueber manuelles Splitting statt zweiter Brigadier-Node (EntityArgument wuerde `/status avatar <skinName>` als fehlenden Online-Player fehlschlagen lassen) |
| `common/.../sync/SyncManager.java` | Push: `settings.addProperty("avatar", ...)` immer vorhanden (Mod bleibt Source of Truth) |
| Supabase (ujksgdbfczbgtifiovuw) | Migration `players_add_avatar`: `ALTER TABLE players ADD COLUMN avatar text` |
| `dashboard/app/api/players/[server_id]/sync/route.ts` | Schema += `avatar: z.string().max(160)`; Rows: nur schreiben wenn Mod sendet (`"" → null`) |
| `dashboard/app/api/events/route.ts` | SSE-Select += `avatar` |
| `dashboard/components/player-avatar.tsx` (NEU) | Renderer: URL → CSS Face-Crop; Name → mc-heads; leer → Username/UUID-Fallback; onError versteckt img |
| `dashboard/lib/client/use-realtime.ts` | Player-Type += `avatar` |
| Overview / Players / Detail Pages | `<PlayerAvatar>` statt roher mc-heads-img (20px Chips, 24px Tabelle, 64px Detail); Players-Merge zieht `live.avatar ?? p.avatar` |

**Verifiziert & deployed:** `:common:compileJava` EXIT 0; `npm run build` EXIT 0; Vercel prod deploy EXIT 0; Multiversion 20/20 ok; Testserver-JAR `Statusmod-1.3.0-fabric-26.2.jar` (161927 Bytes, 16:57), Bytecode enthaelt setAvatar/applyAvatar/avatar verifiziert. **Nicht committet.**

**Test-Anleitung (In-Game):**
1. `/status avatar Delle2211` → eigener Head im Dashboard (auch non-premium, wenn Name premium ist)
2. `/status avatar https://dein.host/skin.png` → Face-Crop aus eigener PNG
3. `/status avatar off` → zurueck zum Standard
4. Admin: `/status avatar <OnlinePlayer> <value>`
5. Dashboard aktualisiert sich binnen ~30s (Sync-Zyklus) bzw. sofort bei offenem Live-Feed

**Offen:** In-Game-Test Runde 1+2+3 gemeinsam; nichts committet (development-Branch-Push steht an).