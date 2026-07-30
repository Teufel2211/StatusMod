# StatusMod — Dashboard & Cloud-Integration Plan

## Inhalt
- [1. Architektur](#1-architektur)
- [2. Auth-Flow](#2-auth-flow)
- [3. Supabase Tabellen](#3-supabase-tabellen)
- [4. Dashboard Backend](#4-dashboard-backend)
- [5. Discord Bot](#5-discord-bot)
- [6. Betrieb](#6-betrieb)
- [7. Plattform-Kompatibilität](#7-plattform-kompatibilität)
- [8. DSGVO / Privacy](#8-dsgvo--privacy)
- [9. JWT / Token](#9-jwt--token)

---

## 1. Architektur

```
┌─────────────┐     ┌──────────────────┐     ┌────────────────┐
│  Minecraft   │────▶│  Dashboard REST  │◀────│  Web Dashboard  │
│  (Mod/Plugin)│     │  API (Next.js)   │     │  (Next.js UI)   │
└─────────────┘     └────────┬─────────┘     └────────────────┘
        │                    │
        ▼                    ▼
┌─────────────┐     ┌──────────────────┐
│  Discord Bot │────▶│  Dashboard REST  │
│  (py-cord)   │     │  API (Next.js)   │
└─────────────┘     └────────┬─────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │   Supabase   │◀────────────────┐
                      │ (PostgreSQL) │                 │
                      └──────────────┘                 │
                                                       │
                ┌──────────────────────────────────────┘
                │ Dashboard REST API (gleicher Pfad wie Mod)
                │ eigener Read-Only Key oder Session
                └──────────────────────────────────────┘
```

**Leitprinzipien:**
- Minecraft-Server spricht **nie** direkt Supabase an. Nur über Dashboard-REST-API.
- Discord Bot spricht ebenfalls **nie** direkt Supabase an.
- Supabase bleibt hinter Dashboard-Backend (Service-Role-Key nur dort).
- Service-Role-Key existiert nur als Env-Var im Backend, nie im Code, nie im Repo.

**HTTPS/TLS zwingend:**
- **Jede** Kommunikation nur über HTTPS: Mod↔API, Dashboard↔API, Bot↔API, Stripe↔API.
- Backend erzwingt HTTP→HTTPS-Redirect auf Production-Ebene (nginx/Cloudflare).
- HSTS-Header: `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- In Entwicklung ohne HTTPS: `ALLOW_HTTP=true` (default: false).
- `CORS_ORIGIN` muss **mit `https://` beginnen** — Backend validiert Prefix, nicht nur `includes()`. `http://` wird in Production abgelehnt.

---

## 2. Auth-Flow

### Codes (sicher)

- `/code`-Codes: **8 Zeichen** (alphanumerisch, z.B. `A3kR9xZ2`).
- Setup-Codes: **16 Zeichen** (alphanumerisch, 24h gültig).
- **Hash:** `code_hash` verwendet argon2id (oder bcrypt Cost=12). **Nie SHA-256.**
- **Timing:** Argon2-`verify()` ist constant-time. Kein Fast-Path-Check.
- **Rate-Limit:** max 3 Fehlversuche pro Code → Code ungültig.
- **IP-Sperre:** 3 Fehlversuche/IP/Min → 5 Min Sperre.
- **Kein Log-Leak:** Code nie in Console-Logs. Nur "Code generated for UUID: <uuid>".

### Server-Setup (First Owner)

1. Mod generiert **einmaligen Setup-Code** (16 Zeichen, alphanumerisch, 24h gültig).
2. Code erscheint **ausschließlich im Console-Log** (nicht im Chat):

   ```
   [StatusMod] === SERVER SETUP ===
   [StatusMod] Dashboard: https://statusmod.example.com/setup
   [StatusMod] Setup-Code: Xk9mR2pL7vN3bW8z
   [StatusMod] Code expires in 24 hours.
   ```

3. Owner öffnet Dashboard, gibt Setup-Code ein → Server + Owner registriert.
4. Nach Registrierung: `/code` für Owner freigeschaltet.
5. Setup-Code wird nach Nutzung oder Ablauf gelöscht.
6. **API-Key für Mod** wird automatisch generiert (Scope `["check"]`). Key im Owner-Dashboard angezeigt. Owner trägt ihn in `config/statusmod/config.json` ein. Ohne API-Key: Local-Only-Mode.

**Sicherheit:** Nur Console-Zugriff → erster Owner. Kein Claiming durch Dritte.

### Owner-Zugriff

1. Owner `/code` → 10 Min gültig.
2. Code im Dashboard → Server-ID + Owner-Rolle.
3. Session: **JWT (15 Min) + Refresh-Token (7 Tage, rotiert, IP+User-Agent gebunden)**.
4. **JWT-Regeneration:** Neues JWT + Refresh bei jedem Login. Alte revoked.
5. **2FA/MFA:** TOTP optional. Recovery-Codes (10×, einmalig) bei Einrichtung.

---

## 3. Supabase Tabellen

| Tabelle | Schema | Security |
|---------|--------|----------|
| `servers` | `id UUID PK, config JSONB, owner_uuid UUID, created_at TIMESTAMPTZ` | JSONB via Zod validiert. Setup-Codes in `dashboard_codes`, nicht hier. |
| `players` | `id SERIAL PK, server_id UUID NOT NULL, uuid TEXT NOT NULL, username TEXT, status TEXT, color TEXT, settings JSONB, anonymized BOOL DEFAULT false, created_at TIMESTAMPTZ, UNIQUE(server_id, uuid)` | DSGVO: `anonymized=true`, PII auf NULL. Kein PK-Konflikt. |
| `blocked_players` | `uuid TEXT, server_id UUID, blocked_at TIMESTAMPTZ, PRIMARY KEY(uuid, server_id)` | RLS |
| `muted_players` | `uuid TEXT, server_id UUID, muted_until TIMESTAMPTZ, PRIMARY KEY(uuid, server_id)` | RLS |
| `custom_presets` | `name TEXT, server_id UUID, status TEXT, color TEXT, creator_uuid TEXT, PRIMARY KEY(name, server_id)` | RLS |
| `audit_log` | `id SERIAL PK, server_id UUID, who_uuid_hash TEXT, action TEXT, target_uuid_hash TEXT, detail TEXT, created_at TIMESTAMPTZ` | Auto-Purge 90 Tage. Retention: 7–365 Tage. |
| `dashboard_codes` | `code_hash TEXT (argon2id), server_id UUID, expires_at TIMESTAMPTZ, used BOOL, created_at TIMESTAMPTZ` | Nur Hash. Argon2id, kein SHA. |
| `dashboard_users` | `uuid TEXT PK, username TEXT, role ENUM(owner/admin/viewer), server_id UUID, totp_secret TEXT (pgcrypto encrypt), totp_enabled BOOL DEFAULT false, recovery_code_hashes TEXT[]` | TOTP-Secret mit pgcrypto `pgp_sym_encrypt()` verschlüsselt. Recovery-Code-Hashes (argon2id). |
| `verify_codes` | `code_hash TEXT (argon2id), uuid TEXT, server_id UUID, expires_at TIMESTAMPTZ, used BOOL, attempt_count INT DEFAULT 0, created_at TIMESTAMPTZ` | `attempt_count > 2` → Code ungültig. |
| `discord_links` | `discord_id TEXT PK, uuid TEXT, server_id UUID, role TEXT, linked_at TIMESTAMPTZ, recovery_code_hash TEXT (argon2id)` | Recovery-Code beim Linken, gehasht. |
| `refresh_tokens` | `id SERIAL PK, uuid TEXT, server_id UUID, token_hash TEXT (argon2id), expires_at TIMESTAMPTZ, ip_hash TEXT, user_agent_hash TEXT, revoked BOOL` | Revokable. Rotation: altes revoken. |
| `shop_products` | `id SERIAL PK, server_id UUID, product_key TEXT, name TEXT, description TEXT, price_cents INT, duration_days INT, active BOOL` | RLS |
| `shop_statuses` | `id SERIAL PK, server_id UUID, status_text TEXT, color TEXT, price_cents INT, duration_days INT, active BOOL` | RLS. `status_text`: `^[a-zA-Z0-9_ &]+$` |
| `purchases` | `id SERIAL PK, uuid TEXT, server_id UUID, product_key TEXT, purchased_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, stripe_event_id TEXT UNIQUE, stripe_event_timestamp INT` | Replay-Schutz via UNIQUE + Timestamp. |
| `player_status_purchases` | `id SERIAL PK, uuid TEXT, server_id UUID, status_text TEXT, color TEXT, purchased_at TIMESTAMPTZ, expires_at TIMESTAMPTZ` | RLS |
| `api_keys` | `id SERIAL PK, server_id UUID, key_hash TEXT (argon2id), key_prefix TEXT, scopes TEXT[], created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, revoked BOOL` | Max 2/Server. Scopes: check, check-status, products, purchases, audit, *. Key nur einmalig sichtbar. |
| `schema_version` | `id SERIAL PK, version TEXT, applied_at TIMESTAMPTZ, checksum TEXT` | Migrations-Tracking. |

**Zeitzonen:** Alle `TIMESTAMPTZ`-Spalten speichern UTC.

**JSONB-Validierung:**
- Zod-Schema mit `z.string().max(100000)`. DB-Fallback: `CHECK (pg_column_size(config) < 102400)`.
- Unbekannte Felder via Zod `.strict()` entfernt (nicht rejected).
- Gleiches Schema für Mod-Config-Editoren.

---

## 4. Dashboard Backend

### Env-Vars

```
DATABASE_URL=postgresql://user:pass@host:5432/postgres
JWT_SECRET=<32+ Zeichen, randomBytes()>
JWT_SECRET_PREVIOUS=<vorheriger Secret für Rotation>
SERVICE_ROLE_KEY=<Supabase Service Role Key>
STRIPE_SECRET_KEY=<Stripe Secret Key>
STRIPE_WEBHOOK_SECRET=<Stripe Webhook Secret>
CORS_ORIGIN=https://statusmod.example.com
ALLOW_HTTP=false
```

### Rate-Limits

| Limit | Wert | Scope |
|-------|------|-------|
| RATE_LIMIT_WINDOW_MS | 60_000 | Global |
| RATE_LIMIT_MAX_LOGIN | 3 | Pro IP / Window |
| RATE_LIMIT_MAX_API | 30 | Pro API-Key / Window |
| RATE_LIMIT_MAX_CODE_VERIFY | 5 | Pro UUID / Window |
| RATE_LIMIT_MAX_CODE_GENERATION | 3 | Pro IP / Window (schützt vor Argon2id-Spam) |
| RATE_LIMIT_WINDOW_SHOP_MS | 60_000 | Pro IP / Window |
| RATE_LIMIT_MAX_SHOP | 10 | Stripe Sessions / Window |

### API Endpunkte — Auth

| Endpunkt | Methode | Auth | Zweck |
|----------|---------|------|-------|
| `/api/auth/setup` | POST | – | Server + Owner registrieren (Setup-Code) |
| `/api/auth/code` | POST | – | Login-Code → JWT + Refresh |
| `/api/auth/refresh` | POST | Refresh | Refresh-Token rotieren |
| `/api/auth/logout` | POST | Session | Session beenden |
| `/api/auth/2fa/setup` | POST | Session | TOTP einrichten |
| `/api/auth/2fa/verify` | POST | Session | TOTP-Code prüfen + aktivieren |
| `/api/auth/2fa/disable` | POST | Session | 2FA deaktivieren |

### API Endpunkte — Player & Server

| Endpunkt | Methode | Auth | Scope | Zweck |
|----------|---------|------|-------|-------|
| `/api/health` | GET | Öffentlich | – | Healthcheck |
| `/api/keys` | GET | Session | – | API-Keys auflisten |
| `/api/keys` | POST | Session | – | API-Key generieren |
| `/api/keys/:id` | DELETE | Session | – | Key revoken |
| `/api/keys/leak` | POST | Session + Re-Auth | – | Alle Keys revoken (vorher Code-Eingabe zur Bestätigung) |
| `/api/players/:server_id` | GET | API-Key | check | Alle Spieler |
| `/api/players/:server_id/:uuid` | GET | API-Key | check | Spielerstatus |
| `/api/players/:server_id/:uuid` | PUT | Session | – | Status setzen (Admin) |
| `/api/players/:server_id/:uuid/status` | PATCH | Session | – | Status-Feld ändern |
| `/api/players/:server_id/:uuid/mute` | POST | Session | – | Muten |
| `/api/players/:server_id/:uuid/unmute` | POST | Session | – | Entmuten |
| `/api/players/:server_id/:uuid/blocked` | GET | API-Key | check | Ist geblockt/gemuted? |
| `/api/presets/:server_id` | GET | API-Key | check | Custom-Presets |
| `/api/presets/:server_id/:name` | DELETE | Session | – | Preset löschen |
| `/api/audit/:server_id` | GET | API-Key | audit | Audit-Log |
| `/api/server/:server_id/config` | GET | API-Key | check | Config lesen |
| `/api/server/:server_id/config` | PUT | Session | – | Config schreiben |

### API Endpunkte — Shop

| Endpunkt | Methode | Auth | Scope | Zweck |
|----------|---------|------|-------|-------|
| `/api/shop/products/:server_id` | GET | API-Key | products | Aktive Produkte |
| `/api/shop/statuses/:server_id` | GET | API-Key | products | Kaufbare Premium-Status |
| `/api/shop/purchases/:server_id/:uuid` | GET | API-Key | purchases | Käufe eines Spielers |
| `/api/shop/check/:server_id/:uuid/:product_key` | GET | API-Key | check | Hat Spieler Produkt? |
| `/api/shop/check-status/:server_id/:uuid/:status_text` | GET | API-Key | check-status | Hat Spieler Premium-Status? |
| `/api/shop/products/:server_id` | POST | Session (Owner) | – | Produkt anlegen |
| `/api/shop/products/:server_id/:key` | DELETE | Session (Owner) | – | Produkt deaktivieren |
| `/api/shop/statuses/:server_id` | POST | Session (Owner) | – | Premium-Status anlegen |
| `/api/shop/statuses/:server_id/:id` | DELETE | Session (Owner) | – | Premium-Status deaktivieren |
| `/api/webhook/stripe` | POST | Stripe-Signatur | – | Kauf bestätigen |

### Stripe-Webhook Sicherheit

- **Signatur:** `stripe.Webhook.constructEvent()` — zwingend. Ohne gültige Signatur: 401.
- **Timestamp:** `event.created` ±5 Minuten. Ältere Events ablehnen (Replay-Protection).
- **Idempotency:** `stripe_event_id` UNIQUE. `stripe_event_timestamp` für Debug.
- **Rate-Limit:** Webhook ausgenommen.
- **Retry:** Bereits verarbeitete Events → **200 OK** (Stripe stoppt Retry).
- **Checkout-Session (Server-Side):** Feste Metadaten — Client nicht beeinflussbar:

  ```json
  {
    "server_id": "{{server.id}}",
    "uuid": "{{user.uuid}}",
    "product_key": "premium_colors",
    "product_type": "product",
    "status_text": ""
  }
  ```

  Webhook validiert: `server_id` existiert, `uuid` zu Server, `product_key` in `shop_products` (bzw. `status_text` in `shop_statuses`). Bei Diskrepanz: ablehnen + Alert.

- **success_url Tampering:** Nach Zahlung ruft Success-Seite server-seitig `stripe.checkout.sessions.retrieve(session_id)` auf. Prüft `metadata.uuid` = eingeloggter User, `payment_status === "paid"`, `metadata.server_id` = User-Server.

### Mod-Kommunikation

| Transport | Details |
|-----------|---------|
| **Polling** | Mod fragt alle 60s `/api/players/:server/:uuid/updates?since=` ab. Nur Timestamp + UUID + Status + Color. |
| **Push (opt.)** | Supabase Realtime via WebSocket-Proxy. Fallback auf Polling. |
| **Auth** | API-Key (Mod-Config). Rate-Limited. |
| **Payload** | Max 1KB pro Update. |

Dashboard-API ändert nie direkt lokale Config. Änderungen = "pending" → nächster Poll.

**Pfad-Parameter-Validierung:** Alle `:server_id` und `:uuid` Parameter werden als UUID-v4 via Regex validiert (`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`). Bei ungültigem Format: 400 Bad Request. `:product_key` und `:status_text` werden auf `^[a-zA-Z0-9_ &-]{1,64}$` geprüft. Alle Query-Parameter werden auf Länge (max 256) + Zeichensatz validiert.

### Verify-Flow

1. Spieler `/code` → Mod zeigt Code im Chat.
2. Dashboard `/verify` → Code eingeben.
3. Backend prüft `verify_codes` → gültig → JWT + Refresh.
4. Code `used=true`. Neuer Code beim nächsten `/code`.

### Player-Management

- Mod liest/schreibt lokal (`config/statusmod/`).
- **GET** `/api/players/:server/:uuid` — API-Key Sync.
- **PUT** `/api/players/:server/:uuid` — Session (Admin) schreibt aus Dashboard.
- Mod pollt Admin-Aktionen: `/api/players/:server/:uuid/admin-actions?since=`

### Lifecycle

1. **Init:** Setup-Code → Server + Owner registriert.
2. **Normal:** Mod pollt Updates, schreibt lokal.
3. **Key-Rotation:** Backend revoked alte Keys (401 → Owner benachrichtigen).
4. **Server-Löschung:** Alle Daten per Cron gelöscht (7 Tage Grace).

---

## 5. Discord Bot

### Setup

1. Discord Developer Portal → Application → Bot → Token kopieren.
2. Bot zum Server einladen (Admin-Rechte).
3. Dashboard: `/links/discord` → Bot autorisieren → Tokens verknüpfen.
4. Bot registriert Slash-Commands via `bot.tree.sync()`.

### Commands

| Command | Auth | Scope | Aktion |
|---------|------|-------|--------|
| `/code` | DM | – | Login-Code für Dashboard |
| `/status <player>` | Channel | check | Status anzeigen |
| `/status set <text> <color>` | Session (DM) | – | Eigenen Status setzen |
| `/blocked <player>` | Channel | check | Ist geblockt/gemuted? |
| `/server` | Channel | check | Server-Status |
| `/server config` | Session (DM) | – | Config lesen (Admin) |
| `/server config set <key> <value>` | Session (DM) | – | Config schreiben (Owner) |
| `/mute <player> <duration>` | Session (DM) | – | Muten (Admin) |
| `/unmute <player>` | Session (DM) | – | Entmuten (Admin) |
| `/shop` | DM | – | Link zum Dashboard-Shop |
| `/shop check <player>` | Channel | check | Aktive Premium-Käufe? |
| `/keys` | DM | – | API-Keys anzeigen (Owner) |
| `/keys new <scope>` | DM | – | API-Key generieren (Owner) |
| `/keys revoke <id>` | DM | – | Key widerrufen (Owner) |
| `/ping` | Channel | – | Latenz-Test |
| `/help` | Channel | – | Command-Liste |

**Auth-Kategorien:**
- **DM** = Antwort nur via Direct Message. Nie öffentlich.
- **Channel** = Antwort für alle sichtbar.
- **Session (DM)** = User muss eingeloggt sein + Token via DM senden.
- **API-Key** = Bot-eigener Read-Only-Key.

Bot: nur Slash-Commands (kein Message Content Intent). Privacy Policy öffentlich.

---

## 6. Betrieb

### Datenbank-Backup

- Supabase Pro: tägliche Backups (7 Tage Retention).
- Point-in-Time Recovery: 7 Tage.
- **Vor Migration:** Manuelles Backup via `pg_dump`:

  ```bash
  pg_dump --no-owner --no-acl -Fc -f pre-migration-$(date +%F).dump $DATABASE_URL
  ```

- **Off-Site:** Wöchentlicher Export → GPG (AES-256) → B2/S3. Retention: 30 Tage.
- **Restore-Test:** Alle 30 Tage.
- Lokale JSONs als zusätzliche Backup-Schicht.

### Monitoring & Alerting

- **Healthcheck:** `GET /api/health` (öffentlich) — DB + Stripe + Latenz.
- **Rate-Limit:** >80% → Warn, >100% → Alert.
- **Webhook-Failures:** Stripe `webhook_endpoint.delivery_failed` → Alert.
- **DB-Connections:** Max 15 (Pro). PgBouncer (Transaction Mode). >12 → Warn.
- **Uptime:** Externer Ping (UptimeRobot, 5 Min).
- **Crash-Reporting:** Sentry (optional, DSGVO-konform).

### Incident Response

| Phase | Aktion | Zeit |
|-------|--------|------|
| Detection | Healthcheck fail → PagerDuty/Telegram | < 1 Min |
| Triage | Logs prüfen | < 5 Min |
| Mitigation | Rate-Limit / Key revoken / DB rollback | < 15 Min |
| Resolution | Fix deployen | < 1h |
| Post-Mortem | Root-Cause + 5 Whys | < 24h |

### Schema-Versionierung

- `schema_version` trackt Migrationen (Version, applied_at, checksum).
- Backend prüft vor Migration gegen erwartete Version.
- Bei Diskrepanz: Migration ablehnen + Alert.
- Rollback via `pg_dump`-Restore.
- Keine Auto-Migration in Production.

---

## 7. Plattform-Kompatibilität

### Aktuell

| Plattform | MC Versionen | Status |
|-----------|-------------|--------|
| Fabric | 1.19 – 26.2 | ✅ |
| Forge | 1.19 – 26.2 | ✅ |
| NeoForge | 1.21+ | ✅ |

### Geplant

| Plattform | Typ | Aufwand |
|-----------|-----|---------|
| Quilt | Mod-Loader | Gering |
| Bukkit/Spigot/Paper/Purpur | Plugin | Hoch |
| Folia | Plugin (regioniert) | Mittel |
| Datenpaket | Vanilla | Niedrig |

---

## 8. DSGVO / Privacy

- **Datenlöschung:** `DELETE /api/players/:uuid` — anonymisiert PII in DB. Mod löscht lokale Datei bei `anonymized=true` im Poll-Response. uuid bleibt als HMAC-SHA256(player_uuid, server_secret_key) erhalten (deterministisch, aber nicht reversibel ohne Secret). Dient als Referenz für Käufe/Verlauf. Server-Secret ist der `JWT_SECRET`.
- **Datenportabilität:** `GET /api/players/:uuid/export` — JSON-Export. Auth: Session + Code.
- **Aufbewahrung:** Audit-Logs 90 Tage. Spieler-Daten bis Löschung.
- **Betroffenenanfrage:** `POST /api/privacy/request` → automatische Löschung in 24h.
- **Drittlandtransfer:** Supabase Hosting nur EU (Frankfurt).

---

## 9. JWT / Token

| Token | TTL | Storage | Rotation |
|-------|-----|---------|----------|
| Access Token (JWT) | 15 Min | In-Memory | Neu bei Login + Refresh |
| Refresh Token | 7 Tage | DB + HttpOnly Cookie | Bei Refresh: alt revoken |
| API Key (Mod) | Unbegrenzt | DB + Mod-Config | Manuell via Dashboard |
| Recovery Codes | Einmalig | DB (argon2id) | Neue bei 2FA-Reset |

**JWT Claims:** `sub` (UUID), `server_id`, `role`, `iat`, `exp`.

**JWT Secret Rotation:**
- `JWT_SECRET` aktualisiert, `JWT_SECRET_PREVIOUS` für 1h Überlapp.
- Cron: wöchentliche Prüfung `if (JWT_SECRET age > 90 days) → notify`.
- Ablauf: Neues Secret → beide aktiv (alt nur Verify) → alt entfernen.
