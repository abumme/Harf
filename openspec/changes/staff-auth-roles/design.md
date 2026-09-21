## Context

See proposal.md — Why. Current state that shapes the approach:

- The backend is one Ktor 3.5 process (`Application.module`) on a 1 GB Oracle box, behind the host's Caddy, which proxies `https://api.lazydevs.uz/harf/*` to `127.0.0.1:8081` and strips `/harf` (`handle_path`). The backend runs in Docker, so requests reach it from the Docker bridge gateway, not from `127.0.0.1`. Other apps are served from the same host by path.
- Authentication today is one Ktor `jwt("auth-jwt")` provider for players: HMAC access tokens carrying only `sub`, minted by anyone via `POST /api/v1/auth/anonymous`. There is no concept of a role.
- Schema is applied at startup by the `MigrationUtils` diff over the tables listed in `DatabaseFactory.init` (additive only; DROPs refused). Tables the running image does not list are ignored by its diff.
- Background loops use `superviseForever`; configuration comes from environment variables; `ServerConfig.requireSecureProductionConfig` guards production startup.
- CI (`.github/workflows/ci.yml`): `backend-tests` (`-PbackendOnly :sharedData:jvmTest :backend:test` against a Postgres service), `client-tests`, a `changes` job whose `BACKEND_PATHS` decide whether `publish-image` + `deploy` run. `backend/Dockerfile` is JDK build → JRE runtime; `.dockerignore` excludes client modules, docs and openspec.
- The Gradle build uses Kotlin 2.4.10 — the version Kobweb 0.25.1 is built against (Kobweb `COMPATIBILITY.md`: Compose HTML 1.11.1, Compose runtime 1.12.0) — alongside the AGP 9.1.1 and Compose Multiplatform 1.12.0-rc01 plugins. `settings.gradle.kts` includes the client modules unless `-PbackendOnly` is set. `:sharedData` already targets js and jvm.
- The settled product decisions for the whole admin panel (ADMIN-created credentials, roles, language scope, a Kobweb panel, same-origin hosting) are recorded in this change's proposal and specs and are not re-argued here.

## Goals / Non-Goals

**Goals:**
- A staff identity, session and permission model that `word-catalog`, `daily-word-calendar`, `player-accounts` and `analytics-core` extend by adding permissions, routes, pages and audit actions — without touching auth again.
- The panel ships inside the existing backend image and CI/deploy pipeline; no new server, service or Caddy route.
- One set of Kotlin types for the admin API, compiled into both the backend and the panel, so the contract cannot silently drift.

**Non-Goals:**
- Two-factor authentication, self-service password reset, email, invites.
- Any change to player authentication or `:sharedUI`; `:sharedData` only gains admin types.
- Kobweb's own server features (API routes, streams, fullstack layout) and search-engine visibility of the panel; the export's page snapshots are a by-product.
- Using the Telegram user ID stored on staff for authorization (that is `word-catalog`).
- Multi-instance session or rate-limit coordination (one backend instance).
- Serving the panel from a dedicated subdomain (see Risks).

## Decisions

### Staff are separate tables, not a role column on `users`
New tables (added to `db/DatabaseTables.kt`, registered in `DatabaseFactory`'s migration list):

```
staff            id varchar36 PK, username varchar32 UNIQUE (stored lowercase), display_name varchar64 NULL,
                 role varchar16 ADMIN|WORDER, status varchar16 ACTIVE|DISABLED, password_hash text,
                 password_changed_at ts, failed_login_count int, locked_until ts NULL,
                 telegram_user_id bigint NULL UNIQUE, created_at ts, created_by varchar36 NULL,
                 updated_at ts, last_login_at ts NULL
staff_languages  staff_id FK→staff CASCADE, lang varchar16, PK(staff_id, lang)
staff_sessions   id varchar36 PK, staff_id FK→staff CASCADE, token_hash varchar64 UNIQUE, created_at ts,
                 last_seen_at ts, expires_at ts, revoked_at ts NULL, ip varchar64 NULL, user_agent varchar256 NULL
staff_audit_log  id varchar36 PK, at ts, actor_kind varchar16 STAFF|SYSTEM|TELEGRAM, actor_staff_id varchar36 NULL,
                 action varchar48, target_type varchar24 NULL, target_id varchar64 NULL, lang varchar16 NULL,
                 details text NULL;  indexes (at), (actor_staff_id, at), (lang, at)
```

Staff languages are validated against `WordPackServerService.languages()` (the languages that have a pack), so this change needs no language list of its own. ADMIN rows ignore `staff_languages`; rows are kept when an account is promoted so a later demotion can restore them in the edit form.

*Alternatives rejected*: a `role` column on `users` (a player account could be escalated; player deletion cascades; anonymous accounts would need credentials); an external identity provider (no email infrastructure, new operational dependency, overkill for a handful of staff).

### Password hashing: Argon2id via Bouncy Castle
`org.bouncycastle:bcprov-jdk18on` `Argon2BytesGenerator`, Argon2id, m = 19 MiB, t = 2, p = 1 (OWASP baseline), 16-byte random salt, 32-byte output, stored as a PHC string (`$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`) so parameters can be raised later and old hashes still verify (rehash on successful login when parameters differ). Comparison is constant-time. An unknown username still runs one hash against a fixed dummy PHC string so response time does not reveal existence. Hashing runs on `Dispatchers.IO`; the per-IP rate limit bounds concurrent 19 MiB allocations on the 256 MB heap.

*Alternatives rejected*: `argon2-jvm` (JNA + native libargon2 not present in the slim JRE image); bcrypt (fine, but Argon2id is the current OWASP first choice and BC is pure Java); PBKDF2 (weaker against GPUs).

### Sessions: opaque server-side tokens in an HttpOnly cookie
- Login creates a 256-bit `SecureRandom` token, stores `SHA-256(token)` in `staff_sessions` with `expires_at = now + 7 d`, and sets cookie `harf_admin_session=<token>`: `HttpOnly`, `SameSite=Strict`, `Secure` when `HARF_ENV=production`, `Path=$ADMIN_COOKIE_PATH` (default `/`; production `/harf` so the cookie is not sent to the other apps on `api.lazydevs.uz`), no `Max-Age` beyond the absolute lifetime.
- Each authenticated request hashes the cookie, loads the session joined with its staff row and languages, and rejects when `revoked_at` is set, `now > expires_at`, `now - last_seen_at > 12 h`, or the staff row is DISABLED. `last_seen_at` is written at most once a minute per session to avoid a write per request.
- Logout sets `revoked_at` and clears both cookies. ADMIN password reset and disable revoke all of the member's sessions; own password change revokes all but the current one.
- An hourly `superviseForever` loop deletes sessions expired or revoked more than 7 days ago.

*Alternatives rejected*: staff JWTs with a role claim (revocation and "effective next request" need a database hit anyway; tokens in JS-readable storage are exposed to XSS); Ktor `Sessions` plugin (stores the raw id as the storage key, and its storage abstraction adds nothing over one table).

### Authentication provider + route-scoped access-control plugin
- A custom Ktor `AuthenticationProvider` named `staff-session` is registered next to the existing `jwt("auth-jwt")`. It resolves the cookie to a `StaffPrincipal(staffId, username, displayName, role, languages, sessionId)` and answers failures with `401 ApiErrorResponse("unauthorized", …)`. Admin routes sit under `authenticate("staff-session")` only, so a player JWT is never consulted there.
- `Permission` is an enum; `Role.permissions: Set<Permission>` is the single matrix (`ADMIN` = all; `WORDER` = `WORDS_READ`, `WORDS_WRITE`, `SUGGESTIONS_REVIEW`, `AUDIT_READ_OWN`, `ACCOUNT_SELF`). This change defines `STAFF_MANAGE`, `AUDIT_READ_ALL`, `AUDIT_READ_OWN`, `ACCOUNT_SELF`, and reserves the word/suggestion permissions for `word-catalog`; later changes add theirs.
- A route-scoped plugin `RequirePermission(permission)` installed per route group returns `403 forbidden` when the principal lacks it. Language-specific handlers call `principal.requireLanguage(lang)` (ADMIN passes; WORDER must have it) before any read or write.
- Because the principal is rebuilt from the database on each request, role, status and language changes apply to the next request with no extra mechanism.

*Alternatives rejected*: role checks inline in each handler (easy to forget one); path-based rules in one interceptor (language arrives in path, query or body depending on the route).

### Anti-forgery: session-bound double-submit token
- On login (and on `GET /api/v1/admin/auth/me` when missing) the server sets a readable cookie `XSRF-TOKEN = base64url(SHA-256("xsrf:" + sessionToken))` with the same path and `SameSite=Strict`. It is derived from the session token, so it needs no storage and a token from another session never matches.
- A route-scoped plugin on every admin route with method POST/PUT/PATCH/DELETE, except login, recomputes the value from the session cookie and requires an equal `X-XSRF-TOKEN` header, else `403 forbidden`. All admin POST bodies must be `application/json`: a state-changing admin request (login included) whose body has another content type is refused with `415 unsupported_media_type` before any handler runs.
- The panel's `AdminApi` client reads `XSRF-TOKEN` from `document.cookie` and sends it as `X-XSRF-TOKEN` on every POST/PUT/PATCH/DELETE. No framework does this implicitly, so that client is the single place it happens, and it is unit-tested.
- Login is exempt (no session yet); login CSRF is mitigated by `SameSite=Strict` and the JSON-only body, which a cross-site form cannot send.

*Alternatives rejected*: a plain random double-submit cookie (a sibling app on the same host could plant a matching pair); a per-session CSRF column (same guarantee as derivation, one more column).

### Login throttling: RateLimit + forwarded headers + account lockout
- `ktor-server-rate-limit`: a `login` limiter of 10 requests per 60 s keyed by `call.request.origin.remoteAddress`, applied only to the login route; excess → `429 rate_limited`.
- `ktor-server-forwarded-header` (`XForwardedHeaders`), installed only when `TRUST_PROXY_HEADERS=true`, using the last `X-Forwarded-For` entry (the address Caddy appended). Inside Docker the peer is the bridge gateway, so "trust only loopback" cannot work; trusting the header is safe because the container port is published on `127.0.0.1` only and Caddy is its sole client. Dev/test default `false`.
- Lockout lives on the `staff` row: each failure increments `failed_login_count`; the 5th sets `locked_until = now + 15 min` and resets the count; while locked, login answers `423 locked` without checking the password. Success resets the count and records `last_login_at`. The counter update and audit entry are one transaction; concurrent failures use `UPDATE … SET failed_login_count = failed_login_count + 1` to avoid lost increments.

*Alternatives rejected*: a distinct locked response hidden behind the generic failure (staff usernames are not secret and the IP limit bounds enumeration, while a clear "try later" saves real staff confusion); an in-memory failure map (lost on restart, which is exactly when an attacker would retry).

### Bootstrap and recovery
In `main()`, after `DatabaseFactory.init()` and before the server binds: if `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD` are set and no `staff` row is `ADMIN` + `ACTIVE`, then in one transaction create that ADMIN or, if the username exists, set role ADMIN, status ACTIVE, the new password hash, clear the lock, revoke its sessions, and write a `STAFF_BOOTSTRAPPED` entry with `actor_kind = SYSTEM`. A password outside 12–128 characters logs an error and skips. When an active ADMIN exists and the variables are still set, log a warning to remove them. Never fails startup: the player API must keep serving.

### Audit writer inside the action's transaction
`AuditLog.record(actor, action, targetType, targetId, lang, details)` inserts into `staff_audit_log` and is called inside the same `dbQuery` as the mutation, so the entry and the change commit or roll back together. `details` is a small JSON object of changed fields as `{"field": {"from": …, "to": …}}`; a `redact` step drops any key named like `password`, `token` or `hash` as a second line of defence. Actions in this change: `AUTH_LOGIN_SUCCEEDED`, `AUTH_LOGIN_FAILED`, `AUTH_ACCOUNT_LOCKED`, `AUTH_LOGGED_OUT`, `AUTH_PASSWORD_CHANGED`, `STAFF_CREATED`, `STAFF_UPDATED`, `STAFF_PASSWORD_RESET`, `STAFF_DISABLED`, `STAFF_ENABLED`, `STAFF_BOOTSTRAPPED`. A failed login for an unknown username records `AUTH_LOGIN_FAILED` with no target and only the client address. Failed attempts on an existing account (wrong password, disabled, or refused while locked) are recorded with `actor_kind = SYSTEM` and the account as target, with a `reason` detail (`wrong_password`, `disabled`, `locked`): nobody proved to be that member, so they are not attributed as the member's own activity. `AUTH_ACCOUNT_LOCKED` is also a SYSTEM entry. Session expiry is not audited (no event occurs).

Reading: `AUDIT_READ_ALL` lists everything with filters; `AUDIT_READ_OWN` forces `actor_staff_id = me` and rejects an explicit other actor with 403. Later changes add a visibility rule that hides `DAILY_*` actions from WORDERs.

### Admin API routes (this change)
All under `/api/v1/admin`, JSON, errors as `ApiErrorResponse(error, message)`. Request/response DTOs and path constants live in `:sharedData` (see "Shared Kotlin contract"); backend code in `backend/src/main/kotlin/uz/abumme/harfgame/backend/admin/<area>/{<Area>Routes,<Area>Service}.kt`.

| Method & path | Permission | Result |
|---|---|---|
| `POST /auth/login` `{username, password}` | none (rate limited) | `200 MeDto` + cookies; `401 unauthorized` (generic), `423 locked`, `429 rate_limited` |
| `POST /auth/logout` | session | `204` |
| `GET /auth/me` | session | `200 MeDto {id, username, displayName, role, languages, permissions}` |
| `POST /auth/password` `{currentPassword, newPassword}` | `ACCOUNT_SELF` | `204`; `422 validation_failed` for a wrong current password (`currentPassword: wrong`) or an out-of-range new one — never 401, which the panel treats as an ended session |
| `GET /staff` | `STAFF_MANAGE` | `200 [StaffDto]` (no hashes) |
| `POST /staff` `{username, password, role, languages, displayName?, telegramUserId?}` | `STAFF_MANAGE` | `201 StaffDto`; `422` with `field`, `409 conflict` |
| `GET /staff/{id}` | `STAFF_MANAGE` | `200 StaffDto` |
| `PATCH /staff/{id}` `{displayName?, role?, languages?, telegramUserId?}` (explicit `null` clears) | `STAFF_MANAGE` | `200 StaffDto`; `409 conflict` last-ADMIN guard |
| `POST /staff/{id}/password` `{newPassword}` | `STAFF_MANAGE` | `204` |
| `POST /staff/{id}/disable`, `POST /staff/{id}/enable` | `STAFF_MANAGE` | `200 StaffDto`; `409` last-ADMIN guard |
| `GET /audit?actor&action&lang&from&to&page&size` | `AUDIT_READ_ALL` or `AUDIT_READ_OWN` | `200 Page<AuditEntryDto>` |
| `GET /languages` | session | `200 [lang]` scoped to the principal |

Validation errors carry the offending field in `ApiErrorResponse.message` as `field: reason` so the panel can place it; the envelope type stays unchanged. The last-ADMIN guard runs inside the transaction with `SELECT … FOR UPDATE` on active ADMIN rows so two concurrent demotions cannot both pass.

### Panel technology: a Kobweb static site
- `:adminWeb` is a Kobweb 0.25.1 application written in Kotlin/JS with Compose HTML and Silk.
  - Compose HTML renders real DOM, so tables, text selection, browser find and password-manager autofill behave natively.
  - The panel is written in Kotlin, like the rest of the repository.
- Kobweb's "existing backend" guide recommends exporting a *static layout* and letting the backend that already exists serve the files. This change follows that guide and uses none of Kobweb's server features (no `includeServer`, no `@Api` routes).

*Alternatives rejected*:
- **Angular + Taiga UI** — the earlier plan, replaced by the user's choice of Kobweb.
- **Compose Multiplatform for Web** — renders to a canvas: weak text selection, no native tables, no password autofill.
- **Kobweb's fullstack layout with its own server** — a second JVM process on the 1 GB box, duplicating the Ktor API.
- **Server-rendered HTML from Ktor** — no Kotlin component model for the interactive pages later changes need.

### Shared Kotlin contract
- Admin request/response DTOs (`@Serializable`) live in `:sharedData` under `uz.abumme.harfgame.data.admin.{auth,staff,audit}`.
  - Next to them is an `AdminRoutes` object of path constants built on `ApiRoutes.API_PREFIX`.
  - `:backend` (jvm) and `:adminWeb` (js) depend on the same classes, so a renamed field or path fails compilation on both sides.
- The `Role` and `Permission` enums are shared in the same package, so the panel can check permissions by type.
  - The role → permission matrix stays in the backend (`Role.permissions`) as the only authority.
  - `MeDto.permissions` carries the member's effective permissions; the panel never derives them itself.
  - Later changes add enum entries and DTO packages the same way.
- The existing `ApiErrorResponse` envelope is reused unchanged.

*Alternatives rejected*:
- **A generated API description with a generated client** — an extra build step and snapshot test for a contract Kotlin already expresses.
- **DTOs duplicated inside `:adminWeb`** — silent drift.

### `:adminWeb` module
- **Setup** mirrors Kobweb 0.25.1's `app/empty` template:
  - Plugins: `kotlin-multiplatform` and `compose-compiler` (already in the catalog at Kotlin 2.4.10), plus `com.varabyte.kobweb.application`.
  - `kotlin { configAsKobwebApplication("adminWeb") }`.
  - `jsMain` dependencies: `androidx.compose.runtime:runtime` 1.12.0, `org.jetbrains.compose.html:html-core` 1.11.1, `kobweb-core`, `kobweb-silk`, `silk-icons-lucide`, `kotlinx-serialization-json` and `project(":sharedData")`.
  - No `org.jetbrains.compose` plugin is applied.
- **Version catalog additions.**
  - Versions: `kobweb = "0.25.1"`, `compose-html = "1.11.1"`, `compose-runtime-kobweb = "1.12.0"`.
  - Libraries: `kobweb-core`, `kobweb-silk`, `silk-icons-lucide`, `compose-html-core`, `compose-runtime-kobweb`.
  - Plugin: `kobweb-application`.
  - Kobweb artifacts come from Maven Central and the Gradle Plugin Portal, both already configured.
- **`settings.gradle.kts`.**
  - `:adminWeb` is part of the default build.
  - A new `-PadminWebOnly` mode includes only `:sharedData` and `:adminWeb`, so it needs no Android SDK or `:sharedUI`.
  - `-PbackendOnly` keeps excluding `:adminWeb`.
- **Spike first.** Before any panel code:
  - Prove the module configures and builds next to the AGP and Compose Multiplatform plugins: `:adminWeb:jsBrowserDevelopmentWebpack` in the default build and with `-PadminWebOnly`.
  - Prove it resolves `:sharedData`'s js variant.
  - Record the outcome here.
  - If the plugins cannot coexist in one build, switch to an included build (`includeBuild("adminWeb")` with `:sharedData` substituted) before continuing.
  - **Outcome (2026-09-16): no fallback needed.** The Kobweb 0.25.1 application plugin (with the KSP plugin it applies) coexists with AGP 9.1.1 and the Compose Multiplatform 1.12.0-rc01 plugins as a regular subproject; the root `build.gradle.kts` declares it `apply(false)` like the others.
    - `:adminWeb:jsBrowserDevelopmentWebpack` succeeds in the default build (with the Android SDK) and with `-PadminWebOnly` (no SDK), and resolves `:sharedData`'s js variant (a placeholder page referenced `ApiRoutes`).
    - `-PbackendOnly :backend:compileKotlin` does not configure `:adminWeb`.
    - The export works on the placeholder page (`index.html` loads `/harf/admin/adminWeb.js`). Run through Gradle rather than the kobweb CLI, `kobwebExport` leaves the Kobweb server it started running, so `adminWeb/build.gradle.kts` finalizes `kobwebExport` with `kobwebStop`; the export command stays unchanged.
    - The Kotlin/JS yarn lock (`kotlin-js-store/yarn.lock`) differs between the default and `-PadminWebOnly` builds (different npm dependency sets), so Gradle prints "Lock file was changed" when switching modes. It stays uncommitted, as today (open question below: resolved as "not committed").
    - When port 8090 is taken, the Kobweb server picks the next free port (it used 8091 on the dev machine); the dev CORS origin must then match that port.
- **`.kobweb/conf.yaml`.**
  - `site.title` and `site.basePath: "harf/admin"`.
  - `server.port: 8090`, used by the dev and export servers and kept away from the backend's 8080.
  - Dev/prod `script` paths `build/kotlin-webpack/js/{developmentExecutable,productionExecutable}/adminWeb.js`, and `prod.siteRoot: ".kobweb/site"`.
  - `adminWeb/.gitignore` ignores `.kobweb/*` except `conf.yaml`.
- **Icons: `silk-icons-lucide`**, which renders inline SVG. `silk-icons-fa` is avoided: depending on it adds a cdnjs Font Awesome stylesheet to `<head>`, which the panel's CSP and no-third-party stance forbid.
- **API origin.** It reaches the site through `kobweb.app.globals`, and the site reads it via `AppGlobals`:
  - `apiBase = "/harf"` when `project.kobwebBuildTarget` is `RELEASE`;
  - `apiBase = "http://localhost:8080"` for `DEBUG`.

### Panel structure
- **App entry.**
  - `@App` wraps `SilkApp` and a `Surface`.
  - `@InitSilk` registers base styles and color-mode aware `CssStyle`s (light/dark from the system preference).
  - Kobweb's base-path aware `Link` and router navigation are used everywhere, with `BasePath.prependTo` for anything that is not base-path aware.
- **Pages** (`@Page`; routes are relative to the base path):
  - `/` — role-based home: ADMIN dashboard placeholder, or a WORDER words placeholder naming their languages.
  - `/login`, `/staff` (list), `/staff/new`, `/audit`, `/account`, `/forbidden`.
  - `/staff/edit?id=…` — a query parameter rather than a dynamic route, so every page is exported.
  - `Router.setErrorPage` renders a Russian not-found page for unknown routes.
- **`AdminApi`.**
  - Suspend functions over `window.fetch` (behind a small interface, so tests can fake it), using kotlinx.serialization and the shared DTOs.
  - `credentials` is `same-origin` in RELEASE and `include` in DEBUG.
  - Sends `X-XSRF-TOKEN` on mutating methods.
  - Decodes `ApiErrorResponse` (splitting `field: reason`) into the shared `ApiResult.Error`.
  - A 401 from any call except login clears session state and navigates to `/login?next=<current route>`.
  - A 403 renders the forbidden view.
- **Session state.**
  - An app-level holder loads `GET /api/v1/admin/auth/me` once. It skips the call when `AppGlobals.isExporting`, so exported snapshots contain only the shell.
  - It exposes `me`, `hasPermission(permission)` and `signOut()`.
  - A `RequireSession(permission)` wrapper shows a loading shell, redirects to login with `next`, or shows the forbidden view.
  - Navigation entries are a pure function of `me.permissions`.
  - On sign-out or 401 the holder is cleared before navigating, and pages keep no data beyond what they load on entry, so Back shows no staff data.
- **UI.**
  - Silk widgets: `Button`, `TextInput`, `Checkbox`, `Switch`, `Tabs`, `Tooltip`/`Popover`, `Overlay` (dialogs), `SimpleGrid`, `Surface`, `Callout`, `Divider`.
  - Silk has no data table, select, calendar or chart widget. A `components` package holds small reusable pieces built from Compose HTML elements styled with `CssStyle`:
    - `DataTable` — server-paged; used by the staff list and the audit log;
    - `SelectField` — a native `<select>`;
    - `ConfirmDialog`;
    - `FieldError`.
  - Later changes add the calendar grid and inline-SVG charts to the same package.
- **Password managers.** The login form is a real `<form>` with `autocomplete="username"` / `current-password`, and new-password fields use `new-password`.
- **Strings.** Every UI text lives in one Russian `Strings` object; pages contain no literal UI text, which keeps adding a second language cheap.

### Exporting and serving the panel
- **Export.**
  - Command: `./gradlew -PadminWebOnly :adminWeb:kobwebExport -PkobwebReuseServer=false -PkobwebEnv=DEV -PkobwebRunLayout=STATIC -PkobwebBuildTarget=RELEASE -PkobwebExportLayout=STATIC`.
  - Output in `adminWeb/.kobweb/site`, all under the `harf/admin` base path:
    - one `<route>.html` snapshot per page (`index.html`, `login.html`, `staff.html`, `staff/new.html`, …);
    - `adminWeb.js`;
    - public resources.
  - The export starts Kobweb's server and snapshots each page in a headless Chromium via Playwright. The browser is downloaded on first use; `KOBWEB_EXPORT_BROWSER_PATH` can point at an installed one instead.
- **Serving from Ktor**, only when `ADMIN_WEB_DIR` is set and exists, with the semantics of Kobweb's existing-backend guide (`staticFiles("/admin", …) { enableAutoHeadResponse(); extensions("html"); default("index.html") }`).
  - *Implementation note:* a small route (`admin/web/AdminWebRoutes.kt`) resolves `{path}.html`, then `{path}`, then `index.html`, never outside the directory, and answers GET and HEAD. Ktor's `staticFiles` checks for a directory before trying the extension, so `/admin/staff` (next to the exported `staff/` folder holding `new.html` and `edit.html`) would have served `index.html` instead of `staff.html`. `ETag` (size + mtime) and `Last-Modified` feed Ktor `ConditionalHeaders` for the `304`s.
  - `/admin/staff` serves `staff.html`.
  - Any unknown path serves `index.html`. Its script routes client-side and shows the error page for unknown routes, so reloading or bookmarking any panel address loads the panel.
  - A request to exactly `/admin` redirects with a relative `Location: admin/`, so the public `/harf/admin` becomes `/harf/admin/` without the backend knowing the prefix.
- **Caching.** Kobweb names the script `adminWeb.js` with no content hash, so nothing is served as immutable.
  - HTML and `adminWeb.js`: `Cache-Control: no-cache`, with `Last-Modified`/`ETag` revalidation (Ktor `ConditionalHeaders`).
  - Other resources: `max-age=3600`.
  - A deploy is picked up on the next page load.
- **Security headers on `/admin/*`.**
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`.
  - `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`, `X-Content-Type-Options: nosniff`.
  - Inline styles are required: Silk injects its style rules at runtime, and the exported snapshots carry `<style>` blocks and `style` attributes.
  - Kobweb's release `index.html` loads its script by `src`, with no inline script.
  - A task checks the browser console on every page for CSP violations.

*Alternatives rejected*:
- **Caddy `file_server` fed by CI uploads** — a second deploy path, and an edit to a Caddyfile shared with other sites.
- **Serving the unexported webpack output** — relies on Kobweb internals instead of the documented export.

### Local development: Kobweb dev server and development-only CORS
- **Dev server.** `./gradlew -PadminWebOnly :adminWeb:kobwebStart -t` serves the DEBUG panel with live reload at `http://localhost:8090/harf/admin/`; `:adminWeb:kobwebStop` stops it. The backend runs on `http://localhost:8080`, which the DEBUG `apiBase` points at.
- **CORS.** The backend installs Ktor `CORS` for the admin API only when `HARF_ENV` is not `production` and `ADMIN_DEV_ORIGIN` (e.g. `http://localhost:8090`) is set.
  - Allowed: that single origin, with credentials; methods GET/POST/PATCH/DELETE; headers `Content-Type` and `X-XSRF-TOKEN`.
  - In production it is never installed, whatever the variable says.
- **Cookies.** Development cookies use `Path=/` and no `Secure`. Cookies are not port-scoped, and `localhost:8090` → `localhost:8080` is same-site, so the `SameSite=Strict` session cookie is sent and the panel can read `XSRF-TOKEN`.
- **Checking the export.** The base path is baked into the export, so the exported artifact is checked behind a proxy that adds `/harf` (production Caddy or a local reverse proxy), not against the bare backend.

### Panel tests
- **`:adminWeb:jsTest`** (kotlin-test) covers the panel's pure logic:
  - `AdminApi` request building: XSRF header only on mutating methods, credentials mode, JSON body;
  - error mapping: 401 → login with `next`, 403, `ApiErrorResponse` parsing and `field: reason` splitting;
  - permission → navigation mapping;
  - table paging state;
  - password-match validation.
  - Logic is kept out of composables and `fetch` sits behind an interface, so tests need no DOM.
- **Test runner.** Kotlin/JS browser tests run through Karma with ChromeHeadless (`testTask { useKarma { useChromeHeadless() } }`); CI runners ship Chrome. If Karma cannot run locally or in CI, fall back to a `nodejs()` test setup or move the pure logic into a source set testable on Node, and record the choice here.
  - **Choice (2026-09-16): Karma + ChromeHeadless**, configured explicitly in `adminWeb/build.gradle.kts`. It runs locally (Windows, Chrome 153); no Node fallback was needed. CI is unverified until the first `admin-web` run.
- **UI flows** are checked manually in a browser against a local backend: login, redirects, staff forms, audit filters, sign-out and Back. The panel has no screenshot goldens.

### Build, CI and deploy
- **`backend/Dockerfile`** gains a stage `FROM mcr.microsoft.com/playwright/java:v1.61.0-noble AS admin-web`.
  - Playwright 1.61.0 is the version Kobweb 0.25.1 uses (its `gradle/libs.versions.toml`). The image carries a JDK and Chromium's system libraries. The tag exists on mcr.microsoft.com (checked 2026-09-16, incl. `-amd64`/`-arm64` variants); pin its digest when implementing.
  - The stage copies the Gradle wrapper and build files, `sharedData/` and `adminWeb/`, then runs the export command with `-PadminWebOnly`.
  - The runtime stage copies `adminWeb/.kobweb/site` to `/app/admin-web` and sets `ENV ADMIN_WEB_DIR=/app/admin-web`.
  - The backend build stage is unchanged.
- **`.dockerignore`** keeps `adminWeb/` in the context and excludes `adminWeb/build`, `adminWeb/.kobweb/site` and `adminWeb/.kobweb/server`.
- **`ci.yml`.**
  - A new `admin-web` job:
    - checkout, Java 17, Gradle setup;
    - `./gradlew -q -PadminWebOnly :adminWeb:kobwebBrowserCacheId` as the key for caching Playwright's browser directory, as in Kobweb's GitHub workflow guide;
    - `-PadminWebOnly :adminWeb:jsTest`, then the export;
    - upload `adminWeb/.kobweb/site` as an artifact.
  - `publish-image.needs` adds `admin-web`.
  - `BACKEND_PATHS` adds `adminWeb` (`sharedData` is already listed).
- **`.env.example`** documents:
  - `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD` — set once, remove after first sign-in;
  - `ADMIN_COOKIE_PATH` — `/harf` in production;
  - `TRUST_PROXY_HEADERS` — `true` behind Caddy;
  - `ADMIN_DEV_ORIGIN` — local development only, ignored in production.
  - `ADMIN_WEB_DIR` is set by the image.
- **`docs/deploy-oracle.md`.**
  - The new variables in §3, and a panel check in §8 (`https://api.lazydevs.uz/harf/admin/`).
  - Recovery via the bootstrap variables.
  - A note that `docker compose … up -d --build` on the box now also runs the Chromium-based export, which is slow and memory-hungry on the 1 GB shape; prefer the published image.
  - No Caddy change: `/harf/*` is already proxied.
- **`CLAUDE.md`.**
  - `:adminWeb` in "Where code goes": a Kobweb site exported statically and served by the backend, with admin DTOs in `:sharedData`.
  - Its commands: dev server, `jsTest`, export, `-PadminWebOnly`.
  - `adminWeb` in the commit-convention module list.
  - The `admin-web` CI job.

## Risks / Trade-offs

- [Other apps on `api.lazydevs.uz` share the panel's origin; cookie `Path` is not a security boundary, so an XSS in a sibling app could issue same-origin requests with a staff session] → the sibling apps are the team's own; `SameSite=Strict`, `frame-ancestors 'none'` and the session-bound XSRF token block cross-site and framing attacks; if a less-trusted app is ever hosted there, move the panel and admin API to a dedicated subdomain (only `ADMIN_COOKIE_PATH`, Caddy and the Kobweb `basePath` change).
- [`TRUST_PROXY_HEADERS=true` on a deployment reachable without Caddy lets clients spoof their address and dodge the login rate limit] → the compose file publishes the port on `127.0.0.1` only; per-account lockout still applies regardless of address.
- [Locked-account response reveals that a username exists] → accepted: staff usernames are low-value, attempts are rate limited per address, and lockout protects the account itself.
- [Argon2id's 19 MiB per hash under a burst of logins on a 256 MB heap] → 10 attempts/min per address; logins are rare; parameters are in the PHC string and can be tuned without invalidating hashes.
- [Bootstrap variables left in `.env` recreate or recover an ADMIN whenever no active ADMIN exists] → startup warns while they are set alongside an active ADMIN; deploy doc says to remove them after first sign-in.
- [Kobweb is pre-1.0; APIs, Gradle tasks and export behavior change between minor versions] → 0.25.1 is pinned in the catalog. Upgrades are deliberate `adminWeb` changes, checked against Kobweb's `COMPATIBILITY.md` and release notes. Kobweb-specific APIs stay at the edges (app entry, pages); logic stays in plain Kotlin.
- [Kobweb's Gradle plugin may not coexist with the AGP and Compose Multiplatform plugins in one build] → the spike task runs first; the fallback is an included build with `:sharedData` substituted.
- [Kotlin moves in lockstep: Kobweb 0.25.1 is built for Kotlin 2.4.10, and one build shares one Kotlin plugin] → check `COMPATIBILITY.md` before any Kotlin bump for the app. If the app must move ahead of Kobweb, split `:adminWeb` into an included build.
- [Export needs a headless Chromium: longer CI and image builds, a heavy build stage, snapshot timeouts] → a Playwright browser cache in CI and a cached Playwright image layer; raise `kobweb.app.export.timeout` if needed; pages skip session loading while exporting.
- [Silk has no data table, select, calendar or chart widgets] → small in-repo components built on Compose HTML + `CssStyle`, reused by the later changes.
- [Kotlin/JS browser tests depend on ChromeHeadless through Karma] → CI runners ship Chrome; a `nodejs()` fallback is documented; pure logic is kept DOM-free.
- [`adminWeb.js` has no content hash] → `no-cache` with revalidation costs one conditional request per page load and never leaves a stale panel after a deploy.
- [Development-only CORS enabled in production by mistake] → installation is guarded by `HARF_ENV` in code, and a test asserts that production mode sends no CORS headers even with `ADMIN_DEV_ORIGIN` set.

## Migration Plan

1. Merge; CI runs `backend-tests` and `admin-web`, then `publish-image` builds the image with the exported panel baked in (`ADMIN_WEB_DIR=/app/admin-web`) and deploys it as usual.
2. Before or right after the deploy, on the box add to `.env`: `ADMIN_COOKIE_PATH=/harf`, `TRUST_PROXY_HEADERS=true`, `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD` (a strong generated password); `docker compose -f docker-compose.prod.yml up -d` to apply.
3. Startup migration creates the four new tables (additive, no change to existing tables) and bootstraps the ADMIN.
4. Smoke test: open `https://api.lazydevs.uz/harf/admin/`, sign in, create a WORDER, reload `https://api.lazydevs.uz/harf/admin/staff` and confirm the page loads, sign in as the WORDER in another browser, confirm the staff section is absent and refused, confirm the browser console shows no CSP violations; confirm the player app still syncs.
5. Remove `ADMIN_BOOTSTRAP_*` from `.env` and restart.
6. Rollback: redeploy the previous image tag. Its schema diff does not list the new tables, so it starts without manual SQL; the tables stay unused and the previous image serves no panel. Player auth is untouched either way.

## Open Questions

- Whether to commit the Kotlin/JS lock store (`kotlin-js-store/`) — resolved: not committed, as before this change. The default and `-PadminWebOnly` builds resolve different npm sets, so one committed lock would be reported as changed by the other mode; `.dockerignore` keeps excluding it.
- Export tuning for CI (`kobweb.app.export.timeout`, `numThreads`) — still open; with the defaults the 8 pages export in 10–20 s locally. Set when the `admin-web` job first runs.

## Implementation notes (apply, 2026-09-16)

Details the design left open, decided while implementing; none narrows the specs.

- **Shared contract** (`:sharedData`, `uz.abumme.harfgame.data.admin`): `AdminRoutes` (paths, cookie and header names), `AdminErrors` (error codes), `FieldError` (`field: reason` in `ApiErrorResponse.message`) with the `FieldReasons` vocabulary (`required`, `invalid`, `too_long`, `length`, `taken`, `unknown`, `wrong`, `last_admin`), `StaffRules` (username, password and display-name rules the panel pre-checks), `Role`, `Permission`, `PageDto<T>` (zero-based `page`), and `Patch<T>` (`Unchanged` or `Set(value)`) for PATCH bodies: an absent key stays `Unchanged`, an explicit JSON `null` is `Set(null)`. DTO packages are `admin.auth`, `admin.staff` and `admin.audit`; audit query and action names are `AuditParams` and `AuditActions`. Times are epoch milliseconds.
- **Backend wiring**: `admin/AdminBackend.kt` builds the services once (`AdminBackend(config, packLanguages, clock, passwordHasher)`) and exposes `installAdminPlugins`, `AuthenticationConfig.adminAuthentication`, `StatusPagesConfig.adminErrorHandlers` and `Route.adminRoutes`. `Application.module(admin = …)` takes it, so tests inject a clock, a cheap hasher and config. Refusals are thrown as `AdminApiException`; inside `dbQuery` this also rolls the transaction back. `requirePermission(vararg anyOf)` wraps route groups; `AUDIT_READ_ALL` or `AUDIT_READ_OWN` gates `/audit`. The environment is read by `AdminConfig.fromEnv` (`ADMIN_COOKIE_PATH`, `TRUST_PROXY_HEADERS`, `ADMIN_DEV_ORIGIN`, `ADMIN_WEB_DIR`).
- **Mutations** that check invariants run at READ COMMITTED with `SELECT … FOR UPDATE`: the last-ADMIN guard locks all active ADMIN rows, in id order, before the target row. A test runs overlapping demote and disable transactions; it fails when the lock is removed.
- **Cookies** carry `Max-Age` equal to the 7-day absolute lifetime; logout clears both with `Max-Age=0`. `GET /auth/me` re-issues a missing `XSRF-TOKEN` cookie.
- **Rate limit**: RateLimit's own `429` has no body, so a StatusPages handler adds `ApiErrorResponse("rate_limited", …)`. It applies to any 429; only the login route is rate limited today. `AdminConfig.loginAttemptsPerMinute` (10) exists for tests.
- **Schema migration** is split into `DatabaseFactory.migrate(db)` and `pendingMigrationStatements()`, so a test can assert that a second run emits nothing.
- **Panel globals**: besides `apiBase`, the build passes `apiCredentials` (`same-origin` for RELEASE, `include` for DEBUG). `-PadminApiBase=http://localhost:<port>` points a DEBUG build at a backend on another port. `kobwebExport` is finalized by `kobwebStop`, and fails if the exported `adminWeb.js` contains `http://localhost` (a stale DEBUG compilation once leaked into an export after a failed continuous build).
- **Panel look**: tokens are CSS custom properties per Silk color mode (`.silk-light`, `.silk-dark`); the initial mode is the saved choice, else the system preference, and a sidebar switch toggles and saves it. The game's tile colors carry state (active is the correct green, locked the present amber, disabled the absent grey) and spell the HARF wordmark. The font is Onest (SIL OFL 1.1), bundled under `public/fonts/onest` so the panel loads nothing from third parties (`font-src 'self'`). A `/forbidden` page exists; protected pages show the same view in place.
- **Local check of the export** ("behind a proxy that adds `/harf`"): start the backend with `ADMIN_WEB_DIR=adminWeb/.kobweb/site` on a free port, then run `docker run --rm -p 127.0.0.1:8088:80 -v <Caddyfile>:/etc/caddy/Caddyfile:ro caddy:2` with the Caddyfile `:80 { handle_path /harf/* { reverse_proxy host.docker.internal:<port> } }`, and open `http://127.0.0.1:8088/harf/admin/`. Cookies are not `Secure` outside production, so plain HTTP works.
- **Docker export stage (verified locally 2026-09-16)**: `docker build -f backend/Dockerfile .` succeeds; the export stage takes ~7 minutes (Kotlin/JS build plus 8 snapshots) and downloads no browser, because the Playwright image already carries Chromium for 1.61.0 (`PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`). That image runs Gradle on its own JDK 25; the Kotlin/JS build needs no toolchain. The resulting container serves `/admin/` from `/app/admin-web`. The CI `admin-web` job is not verified until its first run.
- **Ports**: `conf.yaml` keeps 8090. When it is taken, Kobweb uses the next free port (8091 on the dev machine), and `ADMIN_DEV_ORIGIN` must name that port.
