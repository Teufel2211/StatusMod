# StatusMod — Dashboard & Cloud-Integration Plan

## Architektur

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

**Wichtig:**
- Minecraft-Server spricht NIE direkt Supabase an. Nur über Dashboard-REST-API mit Read-Only-Key.
- **Discord Bot** spricht ebenfalls NIE direkt Supabase an. Bot geht über dieselbe Dashboard-REST-API (eigener Read-Only-Key, oder Session wenn Admin-Aktion).
- Supabase bleibt hinter Dashboard-Backend (Service-Role-Key nur dort).
- Service-Role-Key existiert nur als Env-Var im Backend, nie im Code, nie im Repo.

**HTTPS/TLS zwingend:**
- **Jede** Kommunikation (Mod↔API, Dashboard↔API, Bot↔API, Stripe↔API) nur über HTTPS.
- Backend erzwingt HTTP→HTTPS-Redirect auf Production-Ebene (nginx/Cloudflare).
- HSTS-Header: `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
- In Development-Umgebung ohne HTTPS muss Explizit-Flag `ALLOW_HTTP=true` gesetzt werden (default: false).
- `CORS_ORIGIN` muss immer `https://` enthalten — Backend lehnt HTTP-Origins in Production ab.

---

## Auth-Flow

### Codes (sicher)

- `/code`-Codes sind **8 Zeichen** (Zahlen + Buchstaben, z.B. `A3kR9xZ2`). Setup-Codes sind **16 Zeichen** (alphanumerisch).
- **Hash-Verfahren:** `code_hash` verwendet **argon2id** (oder bcrypt mit Cost=12). **Niemals SHA-256** — zu schnell für Bruteforce.
- **Timing-Angriffe:** Alle Code-Vergleiche via Argon2-`verify()` (constant-time). Kein Fast-Path-Check vor dem Hash-Vergleich.
- **Rate-Limit:** max 3 Fehlversuche pro Code, dann Code ungültig
- **IP-Sperre:** 3 Fehlversuche pro IP in 1 Min → 5 Min Sperre (RATE_LIMIT_MAX_AUTH_LOGIN=3, RATE_LIMIT_WINDOW_MS=60000)
- **Kein Log-Leak:** Code nie in Console-Logs. Nur "Code generated for UUID: <uuid>" ohne Code-Wert.
- Mod loggt nur anonymisiert. Code erscheint ausschließlich im Chat (Player).

### Server-Setup (First Owner)

1. Bei erster Installation generiert Mod einen **einmaligen Setup-Code** (16 Zeichen, alphanumerisch, 24h gültig)
2. Code erscheint **ausschließlich im Console-Log** (nicht im Chat):

   ```
   [StatusMod] === SERVER SETUP ===
   [StatusMod] Dashboard: https://statusmod.example.com/setup
   [StatusMod] Setup-Code: Xk9mR2pL7vN3bW8z
   [StatusMod] Code expires in 24 hours.
   ```

3. Owner öffnet Dashboard, gibt Setup-Code ein → Server + Owner-Rolle werden registriert
4. Nach erfolgreicher Registrierung ist `/code` für den Owner freigeschaltet
5. Setup-Code wird nach Nutzung oder Ablauf gelöscht
6. **API-Key für Mod wird automatisch generiert:** Nach erfolgreichem Setup erstellt das Backend einen Read-Only-API-Key mit Scope `["check"]`. Der Key wird **einmalig im Owner-Dashboard angezeigt** (`/dashboard/keys`). Owner muss diesen im Mod-Config (`config/statusmod/config.json`) eintragen (`apiKey`-Feld). Ohne API-Key läuft der Mod im Local-Only-Mode (lokale JSON-Dateien).

**Sicherheit:** Nur wer Console-Zugriff hat, kann erster Owner werden. Verhindert Claiming durch Dritte.

### Owner-Zugriff

1. Owner ingame `/code` → generiert Code, 10 Min gültig
2. Code im Dashboard eingeben → Server-ID + Owner-Rolle zugeordnet
3. Session: **JWT (15 Min) + Refresh-Token (7 Tage, rotiert, in DB gespeichert)**. Session an IP + User-Agent gebunden.
4. **JWT-Regeneration:** Bei jedem Login (Code-Eingabe) wird ein **neues JWT + neuer Refresh-Token** ausgestellt. Vorherige Tokens werden revoked. Token werden nie wiederverwendet.
5. **2FA/MFA für Owner:** Dashboard unterstützt TOTP (Time-based One-Time Password) als optionales zweites Authentifizierungs-Verfahren. Einrichtung im Dashboard-Profil. Bei aktiviertem 2FA: Code + TOTP nötig. Recovery-Codes (10×, einmalig nutzbar) werden bei Einrichtung ausgegeben.

---

## Supabase Tabellen (mit Sicherheits-Fixes)

| Tabelle | Schema | Security |
|---------|--------|----------|
| `servers` | `id UUID PK, config JSONB, owner_uuid UUID, created_at TIMESTAMPTZ` | JSONB-Validation via Zod. Nur bekannte Felder, max 100KB. **Setup-Codes in `dashboard_codes`, nicht hier** (single-source-of-truth). |
| `players` | `id SERIAL PK, server_id UUID NOT NULL, uuid TEXT NOT NULL, username TEXT, status TEXT, color TEXT, settings JSONB, anonymized BOOL DEFAULT false, created_at TIMESTAMPTZ, UNIQUE(server_id, uuid)` | Bei DSGVO-Löschung: `anonymized = true`, alle PII-Felder (`username`, `status`, `color`, `settings`) auf NULL. Die UUID-Zeile bleibt als Referenz erhalten — kein PK-Konflikt möglich. |
| `blocked_players` | `uuid TEXT NOT NULL, server_id UUID NOT NULL, blocked_at TIMESTAMPTZ, PRIMARY KEY(uuid, server_id)` | RLS |
| `muted_players` | `uuid TEXT NOT NULL, server_id UUID NOT NULL, muted_until TIMESTAMPTZ, PRIMARY KEY(uuid, server_id)` | RLS |
| `custom_presets` | `name TEXT NOT NULL, server_id UUID NOT NULL, status TEXT, color TEXT, creator_uuid TEXT, PRIMARY KEY(name, server_id)` | RLS |
| `audit_log` | `id SERIAL PK, server_id UUID, who_uuid_hash TEXT, action TEXT, target_uuid_hash TEXT, detail TEXT, created_at TIMESTAMPTZ` | Auto-Purge nach 90 Tagen. DSGVO: UUID wird bei Purge endgültig gelöscht (nicht gehasht — salted Hash wäre genauso identifizierbar). Retention configurierbar (7–365 Tage). `action` + `detail` sind TEXT (Default). |
| `dashboard_codes` | `code_hash TEXT (argon2id), server_id UUID, expires_at TIMESTAMPTZ, used BOOL, created_at TIMESTAMPTZ` | Nur Hash, nie Klartext. Argon2id, kein SHA. |
| `dashboard_users` | `uuid TEXT PK, username TEXT (Anzeige), role ENUM(owner/admin/viewer), server_id UUID, totp_secret TEXT, totp_enabled BOOL DEFAULT false, recovery_code_hashes TEXT[]` | UUID-basiert. TOTP-Secret + Recovery-Code-Hashes für 2FA (argon2id). |
| `verify_codes` | `code_hash TEXT (argon2id), uuid TEXT, server_id UUID, expires_at TIMESTAMPTZ, used BOOL, attempt_count INT DEFAULT 0, created_at TIMESTAMPTZ` | `attempt_count > 2` → Code ungültig. Reset auf 0 bei erfolgreicher Verifikation. Wenn Code locked: Owner generiert neuen via `/code`. |
| `discord_links` | `discord_id TEXT PK, uuid TEXT, server_id UUID, role TEXT, linked_at TIMESTAMPTZ, recovery_code_hash TEXT (argon2id)` | Recovery-Code beim Linken (einmalig), gehasht mit argon2id |
| `refresh_tokens` | `id SERIAL PK, uuid TEXT, server_id UUID, token_hash TEXT (argon2id), expires_at TIMESTAMPTZ, ip_hash TEXT, user_agent_hash TEXT, revoked BOOL` | Refresh-Token in DB = revokable. Bei Rotation: altes Token revoken. |
| `shop_products` | `id SERIAL PK, server_id UUID, product_key TEXT, name TEXT, description TEXT, price_cents INT, duration_days INT, active BOOL` | RLS |
| `shop_statuses` | `id SERIAL PK, server_id UUID, status_text TEXT, color TEXT, price_cents INT, duration_days INT, active BOOL` | RLS. `status_text`: Input-Validierung `^[a-zA-Z0-9_ &]+$` |
| `purchases` | `id SERIAL PK, uuid TEXT, server_id UUID, product_key TEXT, purchased_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, stripe_event_id TEXT UNIQUE, stripe_event_timestamp INT` | `stripe_event_id UNIQUE` + `stripe_event_timestamp` für Replay-Erkennung (±5 Min). |
| `player_status_purchases` | `id SERIAL PK, uuid TEXT, server_id UUID, status_text TEXT, color TEXT, purchased_at TIMESTAMPTZ, expires_at TIMESTAMPTZ` | RLS. |
| `api_keys` | `id SERIAL PK, server_id UUID, key_hash TEXT (argon2id), key_prefix TEXT (ersten 4 Zeichen), scopes TEXT[], created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ, revoked BOOL` | Max 2 gleichzeitig. Scopes: `["check"]`, `["check-status"]`, `["check","products"]`, `["purchases"]`, `["check","products","purchases"]`, `["audit"]`, `["*"]`. Default: `["check","check-status"]`. **Hash vor dem Speichern nie Klartext.** Key wird einmalig bei Erstellung gezeigt. **Entropie:** ≥32 Bytes via `crypto.randomBytes()`, base64url-encoded (≥43 Zeichen). Backend MUSS vor jeder API-Operation prüfen: `api_key.server_id === url_params.server_id` — verhindert Key-Leak-Propagation zwischen Servern. |
| `schema_version` | `id SERIAL PK, version TEXT, applied_at TIMESTAMPTZ, checksum TEXT` | Trackt Migrations-Stand. Vor jeder Migration: Prüfe gegen erwartete Version. |

**Zeitzonen:** Alle `TIMESTAMPTZ`-Spalten speichern explizit UTC. Backend normalisiert vor jedem Write.

**JSONB-Validierung (Config + Settings):**
- Zod-Schema definiert erlaubte Felder + Typen + Limits
- `z.string().max(100000)` für serialisiertes JSON (100KB Grenze)
- Zusätzlich `CHECK (pg_column_size(config) < 102400)` in PostgreSQL als Fallback
- Unbekannte Felder werden via Zod `.strict()` removed (nicht rejected — Migration-sicher)
- Gleiches Schema für Mod-seitige Config-Editoren (lokale Validierung vor API-Send)

---

## Dashboard Backend (Next.js)

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
| RATE_LIMIT_WINDOW_SHOP_MS | 60_000 | Pro IP / Window |
| RATE_LIMIT_MAX_SHOP | 10 | Stripe Sessions / Window |

### API Endpunkte

**Auth (Minecraft/Discord):** Session (JWT + Refresh-Token)

| Endpunkt | Methode | Auth | Scope | Zweck |
|----------|---------|------|-------|-------|
| `/api/auth/setup` | POST | – | – | Server + Owner registrieren (Setup-Code) |
| `/api/auth/code` | POST | – | – | Login-Code einlösen → JWT + Refresh |
| `/api/auth/refresh` | POST | Refresh | – | Refresh-Token rotieren |
| `/api/auth/logout` | POST | Session | – | Session beenden |
| `/api/auth/2fa/setup` | POST | Session | – | TOTP einrichten |
| `/api/auth/2fa/verify` | POST | Session | – | TOTP-Code prüfen + aktivieren |
| `/api/auth/2fa/disable` | POST | Session | – | 2FA deaktivieren (mit Passwort?) |

**Player-API (Mod/Dashboard):**

| Endpunkt | Methode | Auth | Scope | Zweck |
|----------|---------|------|-------|-------|
| `/api/health` | GET | Öffentlich | – | Healthcheck (DB + Stripe) |
| `/api/keys` | GET | Session | – | Eigene API-Keys auflisten |
| `/api/keys` | POST | Session | – | Neuen API-Key generieren (mit Scope) |
| `/api/keys/:id` | DELETE | Session | – | API-Key revoken |
| `/api/keys/leak` | POST | Session | – | Alle Keys revoken (nach Re-Auth) |
| `/api/players/:server_id` | GET | API-Key | `check` | Alle Spieler einer Server-ID |
| `/api/players/:server_id/:uuid` | GET | API-Key | `check` | Status eines Spielers |
| `/api/players/:server_id/:uuid` | PUT | Session | – | Status setzen (Admin) |
| `/api/players/:server_id/:uuid/status` | PATCH | Session | – | Nur Status-Feld ändern (Admin) |
| `/api/players/:server_id/:uuid/mute` | POST | Session | – | Spieler muten |
| `/api/players/:server_id/:uuid/unmute` | POST | Session | – | Spieler entmuten |
| `/api/players/:server_id/:uuid/blocked` | GET | API-Key | `check` | Ist Spieler geblockt/gemuted? |
| `/api/presets/:server_id` | GET | API-Key | `check` | Custom-Presets einer Server-ID |
| `/api/presets/:server_id/:name` | DELETE | Session | – | Preset löschen (Admin) |
| `/api/audit/:server_id` | GET | API-Key | `audit` | Audit-Log lesen (keine Klartext-UUIDs) |
| `/api/server/:server_id/config` | GET | API-Key | `check` | Server-Config lesen |
| `/api/server/:server_id/config` | PUT | Session | – | Server-Config schreiben (Owner) |

| Endpunkt | Methode | Auth | Scope | Zweck |
|----------|---------|------|-------|-------|
| `/api/shop/products/:server_id` | GET | API-Key | `products` | Alle aktiven Shop-Produkte |
| `/api/shop/statuses/:server_id` | GET | API-Key | `products` | Alle kaufbaren Premium-Status |
| `/api/shop/purchases/:server_id/:uuid` | GET | API-Key | `purchases` | Alle Käufe eines Spielers |
| `/api/shop/check/:server_id/:uuid/:product_key` | GET | API-Key | `check` | Hat Spieler X Produkt Y? |
| `/api/shop/check-status/:server_id/:uuid/:status_text` | GET | API-Key | `check-status` | Hat Spieler X Premium-Status Y? |
| `/api/shop/products/:server_id` | POST | Session (Owner) | – | Neues Produkt anlegen |
| `/api/shop/products/:server_id/:key` | DELETE | Session (Owner) | – | Produkt deaktivieren |
| `/api/shop/statuses/:server_id` | POST | Session (Owner) | – | Premium-Status anlegen (Input-validiert) |
| `/api/shop/statuses/:server_id/:id` | DELETE | Session (Owner) | – | Premium-Status deaktivieren |
| `/api/webhook/stripe` | POST | Stripe-Signatur | – | Kauf bestätigen → Eintrag in purchases |

**Stripe-Webhook Sicherheit:**
- **Signatur-Prüfung:** `stripe.Webhook.constructEvent()` — zwingend. Ohne gültige Signatur: 401.
- **Timestamp-Prüfung:** `event.created` muss innerhalb ±5 Minuten aktueller Serverzeit liegen. Ältere Events ablehnen (Replay-Protection).
- **Idempotency:** `stripe_event_id` mit UNIQUE-Constraint. Zusätzlich: `stripe_event_timestamp` speichern für Debug + Replay-Analyse.
- **Rate-Limit:** Webhook vom Rate-Limit ausgenommen.
- **Retry-Handling:** Stripe wiederholt fehlgeschlagene Webhooks mit exponentiellem Backoff (bis 3 Tage). Bereits verarbeitete Events (`stripe_event_id` existiert) antworten mit **200 OK** (nicht 4xx/5xx), damit Stripe das Retry einstellt.
- **Checkout-Session-Erstellung (Server-Side):** Beim Klick auf "Kaufen" erstellt das Dashboard-Backend die Stripe Checkout Session mit **festen Metadaten**:

  ```json
  {
    "server_id": "{{server.id}}",
    "uuid": "{{user.uuid}}",
    "product_key": "premium_colors",
    "product_type": "product",
    "status_text": "" // nur bei product_type: "status"
  }
  ```

  Diese Metadaten werden vom Client **nicht beeinflussbar** gemacht. Der Stripe-Webhook **MUSS** diese Metadaten validieren:
  - `server_id` existiert in DB
  - `uuid` existiert in `dashboard_users` für diesen Server
  - `product_key` existiert in `shop_products` (oder `status_text` in `shop_statuses`)
  - Bei Diskrepanz: Ablehnen + Alert.
- **success_url Tampering-Schutz:** Nach erfolgreicher Zahlung redirectet Stripe zu `success_url?session_id={CHECKOUT_SESSION_ID}`. Die Success-Seite:
  1. Ruft **server-seitig** `stripe.checkout.sessions.retrieve(session_id)` auf
  2. Prüft, dass `metadata.uuid` mit dem eingeloggten User übereinstimmt
  3. Prüft `payment_status === "paid"`
  4. Prüft `metadata.server_id` mit User-Server — kein Cross-Server-Kauf.

### Mod-API (Dashboard REST → Mod)

| Transport | Details |
|-----------|---------|
| **Polling** | Mod fragt alle 60s `/api/players/:server/:uuid/updates` ab (Liste aktualisierter Spieler seit `?since=`). Nur Timestamp + UUID + Status + Color — minimale Daten. |
| **Push (optional)** | Supabase Realtime (WebSocket) — Mod subscribed via Dashboard-Backend-Websocket-Proxy. Fallback auf Polling. |
| **Auth** | API-Key (im Mod-Config). Rate-Limited. |
| **Payload** | Max 1KB pro Update. |

**Dashboard-API ändert nie direkt die lokale Config des Mods.** Änderungen werden als "pending" markiert und beim nächsten Poll abgeholt.

### Mod-Code / Verify-Flow

1. Spieler ingame `/code` → Mod zeigt Code im Chat
2. Spieler öffnet Dashboard → `/verify` → gibt Code ein
3. Backend prüft `verify_codes` → wenn gültig → JWT + Refresh-Token
4. `used = true` für Code. Neuer Code beim nächsten `/code`

### Mod-Code / Player-Management

Der Mod liest/schreibt in lokale JSON-Dateien (`config/statusmod/`). Zusätzlich:
- **GET** `/api/players/:server/:uuid` — API-Key sync (optional: Push-Updates bei Status-Change)
- **PUT** `/api/players/:server/:uuid` — Session (Admin) schreibt Status aus Dashboard

**Admin-Operationen** (Dashboard → Mod):
- Mod pollt `/api/players/:server/:uuid/admin-actions?since=<lastPoll>` — Admin kann muten, entmuten, Status zurücksetzen
- Mod führt Aktion aus → `GET` next poll

### Lifecycle

1. **Init:** Server-Owner registriert via Setup-Code
2. **Normal:** Mod pollt Updates, schreibt lokale Änderungen
3. **Key-Rotation:** Backend widerruft alte Keys (revoked=true). Mod merkt beim nächsten Poll: 401 → Owner benachrichtigen. Neuen Key generieren + Config aktualisieren.
4. **Server-Löschung:** Alle zugehörigen Daten per Cron gelöscht (7 Tage Grace)

---

## Discord Bot (py-cord)

### Setup

1. Discord Developer Portal → Application → Bot → Token kopieren
2. Bot zum Dashboard-Server einladen (Admin-Rechte)
3. Im Dashboard: `/links/discord` → Bot autorisieren → Tokens verknüpfen
4. Bot registriert Slash-Commands via `bot.tree.sync()`

### Commands

| Command | Auth | Scope | Aktion |
|---------|------|-------|--------|
| `/code` | DM | – | Generiert Login-Code für Dashboard |
| `/status <player>` | Channel | `check` | Status eines Spielers anzeigen |
| `/status set <text> <color>` | Channel | Session (DM) | Eigenen Status setzen |
| `/blocked <player>` | Channel | `check` | Ist Spieler geblockt/gemuted? |
| `/server` | Channel | `check` | Server-Status (online, Spielerzahl) |
| `/server config` | Channel | Session (DM) | Config lesen (Admin) |
| `/server config set <key> <value>` | Channel | Session (DM) | Config schreiben (Owner) |
| `/mute <player> <duration>` | Channel | Session (DM) | Spieler muten (Admin) |
| `/unmute <player>` | Channel | Session (DM) | Spieler entmuten (Admin) |
| `/shop` | Channel | DM | Link zum Dashboard-Shop |
| `/shop check <player>` | Channel | `check` | Hat Spieler aktive Premium-Käufe? |
| `/keys` | Channel | Session (DM) | API-Keys anzeigen (Owner) |
| `/keys new <scope>` | Channel | Session (DM) | Neuen API-Key generieren (Owner) |
| `/keys revoke <id>` | Channel | Session (DM) | Key widerrufen (Owner) |
| `/ping` | Channel | – | Latenz-Test |
| `/help` | Channel | – | Command-Liste |

**Slash-Command Auth:**
- **DM** = Bot antwortet nur via Direct Message (privater Channel). Nie im öffentlichen Channel.
- **Channel** = Antwort im Channel sichtbar für alle.
- **Session (DM)** = User muss im Dashboard eingeloggt sein und Session-Token via DM an Bot senden. Bot verifiziert Token mit Backend.
- **API-Key** = Bot hat eigenen Read-Only-Key mit entsprechendem Scope.
- Privacy Policy für Bot muss öffentlich sein (Discord Developer Portal Requirement)
- Bot darf nur Slash-Commands nutzen (kein Message Content Intent)

---

## Betrieb

### Datenbank-Backup
- Supabase Pro: tägliche automatische Backups (7 Tage Retention)
- Point-in-Time Recovery: Konfiguriert für 7 Tage (Supabase Pro Feature)
- **Vor jeder Migration:** Manuelles Backup via `pg_dump`:

  ```bash
  pg_dump --no-owner --no-acl -Fc -f pre-migration-$(date +%F).dump $DATABASE_URL
  ```

- **Off-Site Backup:** Wöchentlicher Export via `pg_dump` → verschlüsselt (GPG, AES-256) → externer Storage (z.B. Backblaze B2, S3-kompatibel). Retention: 30 Tage. Automatisierter Cron-Job.
- Wiederherstellungs-Test: Alle 30 Tage Backup-Restore in Test-Instanz
- Lokale JSONs bleiben als zusätzliche Backup-Schicht (nach Migration optional encrypted in Supabase Storage)

### Monitoring / Alerting
- **Healthcheck:** `GET /api/health` (öffentlich, kein Auth) — prüft DB-Verbindung + Stripe-API + Latenz
- **Rate-Limit-Alerts:** Wenn eine IP > 80% des Rate-Limits erreicht → Warnlog. > 100% → Alert
- **Webhook-Failures:** Stripe sendet `webhook_endpoint.delivery_failed` → Alert an Owner
- **DB-Connections:** Max 15 gleichzeitig (Supabase Pro Limit). Pooling via PgBouncer (Transaction Mode). Bei > 12 → Warnlog.
- **Uptime:** Externer Ping (z.B. UptimeRobot, 5-Min-Intervall) auf `/api/health`
- **Crash-Reporting:** Sentry (optional) für API + Bot. DSGVO-konform (keine PII, nur Error-Stack + Route)

### Incident Response

| Phase | Aktion | Zeit |
|-------|--------|------|
| **Detection** | Healthcheck schlägt fehl → PagerDuty/Telegram/WEBHOOK | < 1 Min |
| **Triage** | Logs prüfen (Supabase Logs + API-Logs) | < 5 Min |
| **Mitigation** | Rate-Limit verschärfen / Key revoken / DB rollback | < 15 Min |
| **Resolution** | Fix deployen (API-Rollback / Bot-Update) | < 1h |
| **Post-Mortem** | Root-Cause + Prävention (5 Whys) | < 24h |

### Schema-Versionierung

- `schema_version`-Tabelle trackt jede Migration mit `version (semver)`, `applied_at`, `checksum`
- Vor jeder Migration: Backend prüft `schema_version` gegen erwartete Version
- Bei Diskrepanz: Migration ablehnen + Alert
- Rollback: Alte Migration via `pg_dump`-Restore (siehe Backup-Strategie)
- Keine Auto-Migration in Production — nur per CI/CD-Trigger

---

## Plattform-Kompatibilität

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
| Datenpaket | Vanilla `.zip` | Niedrig |

---

## DSGVO / Privacy

- **Datenlöschung:** `DELETE /api/players/:uuid` — anonymisiert PII in DB + löscht lokale JSONs beim nächsten Poll (Mod löscht Datei bei `anonymized=true`-Flag im Poll-Response). Nach Löschung: `anonymized=true`, uuid bleibt als `TEXT`-Hash erhalten (nicht reversibel, aber Referenz für Käufe/Verlauf).
- **Datenportabilität:** `GET /api/players/:uuid/export` — JSON-Export aller eigenen Daten (Status-Historie, Settings, Käufe). Auth: Session (JWT) + Code-Verifikation.
- **Aufbewahrungsfrist:** Audit-Logs 90 Tage. Spieler-Daten bis zur Löschung.
- **Betroffenenanfrage:** Owner kann via Dashboard `POST /api/privacy/request` stellen → automatische Löschung innerhalb 24h bei Bestätigung per Code.
- **Drittlandtransfer:** Supabase Hosting nur in EU (Frankfurt). Keine US-Datenhaltung.

---

## JWT / Token

| Token | TTL | Storage | Rotation |
|-------|-----|---------|----------|
| **Access Token (JWT)** | 15 Minuten | In-Memory (Client) | Neu bei Login + Refresh |
| **Refresh Token** | 7 Tage | DB + HttpOnly Cookie | Bei jedem Refresh: altes revoken + neues ausstellen |
| **API Key (Mod)** | Unbegrenzt (revokable) | DB + Mod-Config | Manuell via Dashboard |
| **Recovery Codes** | Einmalig | DB (argon2id Hash) | Neue bei 2FA-Reset |

**JWT Claims:**
- `sub` = UUID
- `server_id`
- `role` (owner/admin/viewer)
- `iat`, `exp`

**JWT Secret Rotation:**
- `JWT_SECRET` wird bei Rotation aktualisiert
- `JWT_SECRET_PREVIOUS` erlaubt eingeschränkte Gültigkeit alter Token (max 1h Überlapp)
- Cron-Job prüft wöchentlich: `if (JWT_SECRET age > 90 days) → notify admin`
- Rotation: Neues Secret → beide Secrets aktiv (alt nur für Verify) → alt entfernen

---

## Obsolet / Nicht umsetzen (Sorted Set / Leaderboard)

- **Sorted Set (Scoreboard):** Der Mod hat keinen globalen Score/Leaderboard-Mechanismus. Die `players`-Tabelle enthält keine Scoredaten. Der Modoker ist ein reiner Status-Mod — kein ELO, kein Level, kein Coin-System. Falls in Zukunft ein Score-System dazukommt, wird Redis für Echtzeit-Leaderboards evaluiert.
