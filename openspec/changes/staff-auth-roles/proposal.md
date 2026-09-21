## Why

Harf's content and players are managed today by hand-editing resource files, running SQL on the box, and tapping Telegram buttons that any allowlisted editor can use for any language. The team needs a web panel where an ADMIN runs the product and WORDERs (word editors) maintain the dictionary of their own languages. Nothing like that can exist without staff identities, a login, and roles that the server enforces, so this change lays that foundation before any content, calendar, player or analytics screen is built on it.

## What Changes

- **Staff accounts, separate from players.** ADMIN and WORDER accounts live apart from player accounts; players' anonymous and Google/Apple sign-in, their tokens and the app are untouched. A player token never opens anything in the panel.
- **Login page.** Staff sign in with a username and password. A failed login gives one generic message; five consecutive failures lock the account for 15 minutes; a network address is limited in how many login attempts it can make per minute.
- **Server-side sessions.** A signed-in browser holds a session that ends after 12 hours idle or 7 days at most, on logout, when an ADMIN resets that staff member's password, or when the account is disabled. State-changing requests must carry an anti-forgery token.
- **Roles enforced by the server.** ADMIN can do everything; a WORDER works only in the languages assigned to them. Role, status and language changes apply on the staff member's next request.
- **Staff management (ADMIN).** Create staff with username, password, role, languages, optional display name and optional Telegram user ID; edit; reset a password; disable and re-enable. Staff are never deleted, and the system never lets the last active ADMIN be disabled or demoted. Staff change their own password.
- **First ADMIN from configuration.** When no active ADMIN exists, the server creates (or recovers) one from environment variables at startup.
- **Audit log.** Every sign-in outcome and every staff-management action is recorded append-only. ADMIN sees all entries with filters; a WORDER sees only their own.
- **Admin web panel.** A new Kobweb 0.25.1 site (`:adminWeb`, Kotlin/JS with Compose HTML and Silk), Russian UI, exported as static files and served by the backend on the same origin as the API at `https://api.lazydevs.uz/harf/admin/`: login page, role-based home (ADMIN dashboard placeholder, WORDER words placeholder), navigation limited to permitted sections, staff management, audit log, and a change-password page.
- **Shared Kotlin contract.** The admin API's request/response types and route paths live in `:sharedData`, so the backend and the panel compile against the same definitions.
- **Build and deploy.** The backend image gains a stage that exports the panel (Kobweb's export drives a headless Chromium); CI gains an `admin-web` job that tests and exports it.

This is the foundation for `word-catalog`, `daily-word-calendar`, `player-accounts` and `analytics-core`, which add their sections, permissions and audit actions on top of it.

## Capabilities

### New Capabilities
- `staff-auth`: staff sign-in with username and password, failure handling, lockout and login rate limiting, session lifetime and revocation, anti-forgery protection, refusal of player tokens, own password change, and bootstrap/recovery of the first ADMIN.
- `staff-access-control`: the ADMIN and WORDER roles, server-enforced WORDER language scope, unauthorized versus forbidden outcomes, and when role, status and language changes take effect.
- `staff-management`: ADMIN listing, creating, editing, password reset, disabling and enabling of staff accounts, with username and password rules and the last-ADMIN guard.
- `staff-audit-log`: what is recorded about staff sign-ins and actions, append-only retention, and who can see which entries.
- `admin-panel`: the staff web panel — where it is served, the login page, role-based home and navigation, session-expiry handling, and the staff, audit and account pages.

### Modified Capabilities
<!-- none: player auth (`account-auth`) and every existing capability are unchanged -->

## Impact

- **Backend** (`:backend`): new `admin/` packages (auth, staff, audit, languages); new tables `staff`, `staff_languages`, `staff_sessions`, `staff_audit_log` (additive, applied by the startup migration); session authentication, access-control and anti-forgery handling scoped to the admin API; login rate limiting and forwarded-header handling; static serving of the exported panel with caching and security headers; development-only CORS for the local panel dev server. New dependencies through the version catalog: Bouncy Castle (Argon2id), Ktor rate-limit, forwarded-header and CORS plugins.
- **Shared** (`:sharedData`): admin DTOs and an `AdminRoutes` object under `uz.abumme.harfgame.data.admin`, used by `:backend` and `:adminWeb`. Additive only; nothing the app uses changes.
- **New Gradle module** (`:adminWeb`): Kobweb application (Kotlin/JS, Compose HTML, Silk, Lucide icons) with `.kobweb/conf.yaml` (base path `harf/admin`), an `AdminApi` client, session state, pages, and `jsTest` unit tests. Included in the default build and in a new `-PadminWebOnly` build mode; excluded from `-PbackendOnly`. Version catalog gains Kobweb, Compose HTML and the Compose runtime Kobweb expects. No separate JavaScript toolchain to maintain (Kotlin/JS manages its own). New commit scope `adminWeb`.
- **Build/CI/deploy**: `backend/Dockerfile` export stage on a pinned Playwright Java image (headless Chromium for Kobweb's export) and `ADMIN_WEB_DIR` baked into the runtime image; `.dockerignore`; `settings.gradle.kts`; `.github/workflows/ci.yml` (`admin-web` job with a Playwright browser cache, backend path filter includes `adminWeb`); `.env.example` (`ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD`, `ADMIN_COOKIE_PATH`, `TRUST_PROXY_HEADERS`, `ADMIN_DEV_ORIGIN`); `docs/deploy-oracle.md`; `CLAUDE.md` (module map, commands, commit scope). Caddy already proxies `/harf/*`, so no Caddy change.
- **No** `:sharedUI` change, no player-facing behavior change, and no app release.
- **Tests**: backend integration tests (test application + Postgres), `:adminWeb:jsTest` for the panel's pure logic, and a manual browser check of the exported panel; no screenshot golden impact.
