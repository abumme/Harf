## 1. Kobweb module spike and shared admin contract (adminWeb, :sharedData)

- [x] 1.1 **Spike the Kobweb module.**
  - Add the Kobweb catalog entries per design.md: versions `kobweb`, `compose-html`, `compose-runtime-kobweb`; libraries `kobweb-core`, `kobweb-silk`, `silk-icons-lucide`; plugin `kobweb-application`.
  - Create `adminWeb/` from Kobweb 0.25.1's `app/empty` template:
    - `build.gradle.kts` with `configAsKobwebApplication("adminWeb")`;
    - `.kobweb/conf.yaml` with `basePath: "harf/admin"`, port 8090 and the `adminWeb.js` script paths;
    - `.gitignore`;
    - one placeholder `@Page`.
  - Update `settings.gradle.kts`: include `:adminWeb` in the default build, and add a `-PadminWebOnly` mode that includes only `:sharedData` and `:adminWeb`.
  - Verify that each of these succeeds:
    - `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk" ./gradlew :adminWeb:jsBrowserDevelopmentWebpack`;
    - `./gradlew -PadminWebOnly :adminWeb:jsBrowserDevelopmentWebpack`;
    - `./gradlew -PbackendOnly :backend:compileKotlin`, which must not need `:adminWeb`.
  - Record the outcome, or the switch to the included-build fallback, in design.md.
- [x] 1.2 **Prove the export pipeline on the placeholder page.**
  - Run `./gradlew -PadminWebOnly :adminWeb:kobwebExport -PkobwebReuseServer=false -PkobwebEnv=DEV -PkobwebRunLayout=STATIC -PkobwebBuildTarget=RELEASE -PkobwebExportLayout=STATIC`.
  - Verify:
    - `adminWeb/.kobweb/site/index.html` exists and loads `/harf/admin/adminWeb.js`;
    - the script file is present;
    - the Kobweb process started by the export has exited afterwards.
- [x] 1.3 **Add the admin contract to `:sharedData`**, under `uz.abumme.harfgame.data.admin`:
  - `AdminRoutes`;
  - `Role` and `Permission`: this change's entries, plus the reserved `WORDS_READ`, `WORDS_WRITE` and `SUGGESTIONS_REVIEW`;
  - the auth, staff, audit and languages DTOs from design.md. The staff update DTO must tell an absent field apart from an explicit `null`;
  - a paged-result DTO.
  - Verify:
    - serialization round-trip tests in `sharedData/src/commonTest`, including absent vs explicit null, pass with `./gradlew -PbackendOnly :sharedData:jvmTest`;
    - `./gradlew -PadminWebOnly :sharedData:compileKotlinJs` succeeds.

## 2. Schema & password hashing (:backend)

- [x] 2.1 **Add backend dependencies.** Add `bcprov-jdk18on`, `ktor-server-rate-limit`, `ktor-server-forwarded-header`, `ktor-server-cors` and `ktor-server-conditional-headers` to `gradle/libs.versions.toml` and `backend/build.gradle.kts`. Verify `./gradlew -PbackendOnly :backend:compileKotlin` succeeds.
- [x] 2.2 **Add the staff tables.**
  - Add `StaffTable`, `StaffLanguagesTable`, `StaffSessionsTable` and `StaffAuditLogTable` per design.md: unique lowercase username, unique nullable `telegram_user_id`, audit indexes.
  - Register them in the `DatabaseFactory` migration list.
  - Extend `DatabaseSchemaTest` to assert that, on a fresh database, the four tables and their unique indexes exist, and a second `init` emits no statements.
- [x] 2.3 **Implement Argon2id hashing.**
  - PHC string with m=19456 KiB, t=2, p=1.
  - Constant-time verify.
  - A dummy-hash path for unknown users.
  - A "needs rehash" check.
  - `PasswordHasherTest` verifies round-trip, wrong password, tampered hash, parameter parsing, and that a hash string never equals the password.

## 3. Sessions, authentication and access control (:backend)

- [x] 3.1 **Implement the session store.**
  - Create: a 256-bit token, stored as SHA-256, with a 7-day `expires_at`.
  - Resolve: reject revoked sessions, sessions past the absolute or 12-hour idle limit, and sessions of disabled staff. Write `last_seen_at` at most once a minute.
  - Revoke one session, all sessions of a staff member, or all but one.
  - `StaffSessionStoreTest` uses an injectable clock to cover each expiry edge and the throttled `last_seen_at` write.
- [x] 3.2 **Register the `staff-session` authentication provider** next to `auth-jwt`.
  - It produces a `StaffPrincipal`, with role and languages loaded per request.
  - It answers failures with `401 unauthorized`.
  - `StaffAuthIntegrationTest.playerJwtIsRefusedOnAdminRoutes` proves a valid player access token gets 401 on `GET /api/v1/admin/auth/me`.
- [x] 3.3 **Add permission and language checks.**
  - The backend `Role.permissions` matrix over the shared `Role`/`Permission` enums.
  - The `RequirePermission` route-scoped plugin.
  - `StaffPrincipal.requireLanguage`.
  - `AccessControlTest` covers:
    - ADMIN allowed;
    - WORDER forbidden on staff routes;
    - WORDER allowed or forbidden per language;
    - removing a language, demoting and disabling each taking effect on the next request, without re-login.
- [x] 3.4 **Implement the session-bound XSRF token.**
  - Set the cookie on login and on `me`.
  - Check the header on POST/PUT/PATCH/DELETE, except login.
  - Require a JSON content type.
  - Tests assert:
    - a mutation without the header gets 403 and leaves data unchanged;
    - a token from another session gets 403;
    - a GET without the header gets 200.
- [x] 3.5 **Configure cookies** from `ADMIN_COOKIE_PATH` and `HARF_ENV`: `Secure` only in production, `SameSite=Strict`, `HttpOnly` on the session cookie only, `Path=/` by default. A test reads the `Set-Cookie` headers in both modes.

## 4. Login, lockout, rate limit and account (:backend)

- [x] 4.1 **Implement the auth routes** per design.md, using the shared DTOs and `AdminRoutes`:
  - `POST /api/v1/admin/auth/login`;
  - `POST /auth/logout`;
  - `GET /auth/me`, with effective permissions;
  - `GET /languages`.
  - `StaffAuthIntegrationTest` covers:
    - successful login: the `me` payload and cookies;
    - case-insensitive usernames;
    - an identical 401 body for a wrong password, an unknown user and a disabled account;
    - logout followed by 401;
    - scoped language lists for ADMIN and WORDER.
- [x] 4.2 **Implement lockout on the staff row.**
  - Failures increment the counter atomically; the 5th failure locks the account for 15 minutes with `423 locked`.
  - A successful sign-in resets the counter.
  - Lock expiry follows an injectable clock.
  - Tests cover:
    - the lock on the 5th failure;
    - a correct password refused while locked;
    - unlock after 15 minutes;
    - 4 failures followed by a success resetting the count.
- [x] 4.3 **Install login rate limiting and forwarded headers.**
  - `RateLimit` with a `login` limiter: 10 attempts per 60 s per origin address.
  - `XForwardedHeaders`, installed only when `TRUST_PROXY_HEADERS=true` and using the last forwarded address.
  - Tests assert:
    - the 11th attempt from one address gets 429 without a credential check;
    - with trusted headers, two clients with different `X-Forwarded-For` addresses are limited separately.
- [x] 4.4 **Implement `POST /auth/password`.**
  - Check the current password.
  - Apply the 12–128 character rule.
  - Answer 422 on failure, never 401.
  - Revoke the member's other sessions and keep the current one.
  - A test with two sessions asserts the other session is signed out, the current one still works, and the old password no longer signs in.
- [x] 4.5 **Implement startup bootstrap and recovery** from `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD` in `main()`.
  - Create the ADMIN, or promote an existing account: enable it, reset its password, unlock it and revoke its sessions.
  - Skip with an error when the password is invalid.
  - Warn when the variables are set while an active ADMIN exists.
  - Never fail startup.
  - `StaffBootstrapTest` covers an empty system, recovery of a disabled WORDER, a no-op when an active ADMIN exists, and skipping a too-short password.
- [x] 4.6 **Add hourly session cleanup.** A supervised loop deletes sessions expired or revoked more than 7 days ago; a unit test over the store asserts only those rows are deleted.

## 5. Staff management and audit log (:backend)

- [x] 5.1 **Implement the audit writer.**
  - It writes in the same transaction as the action, stores JSON `from/to` details, and redacts password, token and hash keys.
  - Record every action listed in design.md.
  - `StaffAuditTest` asserts:
    - entries exist for login success, failure and lock, logout, own password change, each staff action, and bootstrap;
    - no entry contains a password or token;
    - a failure for an unknown username stores no username;
    - a refused action leaves no success entry.
- [x] 5.2 **Implement the staff routes.**
  - `GET/POST /staff`, `GET/PATCH /staff/{id}`, `POST /staff/{id}/password` and `POST /staff/{id}/disable|enable`.
  - Validation, with errors reported as `field: reason` messages:
    - username pattern and case-insensitive uniqueness;
    - password of 12–128 characters;
    - display name of at most 64 characters;
    - at least one pack language for a WORDER;
    - a unique Telegram id.
  - `StaffManagementIntegrationTest` covers:
    - a created account can sign in immediately;
    - a duplicate username gets 409;
    - a WORDER without languages gets 422, as does an unknown language;
    - a duplicate Telegram id gets 409;
    - an explicit null clears the Telegram id;
    - a password reset revokes sessions and clears a lock;
    - disabling revokes sessions, and enabling restores sign-in;
    - there is no delete route.
- [x] 5.3 **Implement the last-ADMIN guard** with row locking inside the transaction. Tests assert:
  - the only ADMIN cannot disable or demote themselves: the request gets 409 and nothing changes;
  - one of two ADMINs can be disabled;
  - two concurrent demotions of the last two ADMINs leave exactly one active ADMIN.
- [x] 5.4 **Implement `GET /audit`.**
  - Filters: actor, action, language, date.
  - Newest-first paging.
  - `AUDIT_READ_ALL` sees everything; `AUDIT_READ_OWN` is forced to the caller's own entries and gets 403 when naming another actor.
  - Tests cover an ADMIN filtering by actor, language and date range; a WORDER seeing only their own entries; and a WORDER getting 403 when naming another actor.

## 6. Panel serving and development CORS (:backend)

- [x] 6.1 **Serve the exported panel.**
  - Serve `ADMIN_WEB_DIR` at `/admin` with `staticFiles { enableAutoHeadResponse(); extensions("html"); default("index.html") }`.
  - Redirect `/admin` to `admin/` with a relative redirect.
  - Caching: HTML and `adminWeb.js` get `Cache-Control: no-cache` with conditional-request support; other resources get `max-age=3600`.
  - Add the CSP, X-Frame-Options, Referrer-Policy and nosniff headers from design.md.
  - `AdminWebServingTest` uses a temp directory with `index.html`, `staff.html`, `adminWeb.js` and `favicon.ico`, and asserts:
    - `/admin/staff` returns `staff.html`;
    - an unknown path such as `/admin/unknown/page` returns `index.html`;
    - `/admin` redirects;
    - the headers are present;
    - a conditional request returns 304;
    - nothing is served when `ADMIN_WEB_DIR` is unset.
- [x] 6.2 **Install development-only CORS for the admin API.**
  - Install it only when `HARF_ENV` is not production and `ADMIN_DEV_ORIGIN` is set.
  - Allow that single origin, with credentials and the `Content-Type` and `X-XSRF-TOKEN` headers.
  - `AdminDevCorsTest` asserts:
    - in development, a preflight from that origin is allowed with credentials;
    - another origin gets no CORS headers;
    - in production mode, no CORS headers are sent even with the variable set.

## 7. Panel foundation (adminWeb)

- [x] 7.1 **Build the app entry and configuration.**
  - `@App` with `SilkApp` and `Surface`.
  - `@InitSilk` base styles and color-mode aware styles.
  - The Russian `Strings` object.
  - The Lucide icons dependency.
  - `apiBase` passed through `kobweb.app.globals`, chosen from `project.kobwebBuildTarget` (`/harf` for RELEASE, `http://localhost:8080` for DEBUG), with a typed accessor.
  - Verify:
    - `./gradlew -PadminWebOnly :adminWeb:kobwebStart -t` serves the panel at `http://localhost:8090/harf/admin/`;
    - the exported `adminWeb.js` carries the RELEASE value;
    - stop the dev server with `:adminWeb:kobwebStop` afterwards.
- [x] 7.2 **Implement `AdminApi`** over a fetch interface.
  - JSON with the shared DTOs.
  - Credentials mode per build target.
  - `X-XSRF-TOKEN` read from the cookie and sent on POST/PUT/PATCH/DELETE.
  - `ApiErrorResponse` mapped to `ApiResult.Error`, splitting `field: reason`.
  - A 401 hook that doesn't fire for the login call, plus 403 mapping.
  - Configure the `jsTest` runner: Karma with ChromeHeadless, or the fallback from design.md. Record the choice.
  - `AdminApiTest` passes with `./gradlew -PadminWebOnly :adminWeb:jsTest` and covers:
    - the XSRF header sent on POST/PATCH/DELETE but not on GET;
    - credentials mode;
    - error parsing;
    - the 401 hook not firing for login.
- [x] 7.3 **Implement session state and navigation.**
  - A session state holder:
    - loads `me` once, skipping the call when `AppGlobals.isExporting`;
    - offers `hasPermission`;
    - `signOut` clears the state before navigating.
  - `RequireSession(permission)` redirects to `/login?next=` or shows the forbidden view.
  - Navigation entries are a pure function of permissions.
  - `NavigationTest` asserts a WORDER gets words, activity and account, and an ADMIN gets dashboard, staff, audit log and account.
  - `SessionFlowTest` asserts a 401 leads to the login route with `next`, with state cleared before navigating.
- [x] 7.4 **Build the shared components.**
  - `DataTable` with server paging state, `SelectField`, `ConfirmDialog` and `FieldError`.
  - A layout shell with permission-filtered navigation, the current user, and sign-out on every page.
  - The forbidden view.
  - A Russian not-found page, set via `Router.setErrorPage`.
  - `PagingStateTest` covers page bounds and the reset to the first page when a filter changes.
  - Verify on the dev server that the shell and the not-found page render.

## 8. Panel pages (adminWeb)

- [x] 8.1 **Build the login page and home placeholders.**
  - The login page is a real `<form>` with `autocomplete` attributes.
  - It shows the generic, temporarily-blocked and rate-limited messages, keeps the username, and returns to `next` after success.
  - Role-based home placeholders at `/`: an ADMIN dashboard, and a WORDER words page listing their languages.
  - `LoginMessagesTest` maps 401, 423 and 429 to messages.
  - Verify manually against a local backend with `ADMIN_DEV_ORIGIN=http://localhost:8090`: each message, both homes, and the return to `next`.
- [x] 8.2 **Build the staff pages**: `/staff`, `/staff/new` and `/staff/edit?id=`.
  - The list shows role, status, languages, Telegram id, lock state and last sign-in.
  - The create and edit forms require languages for a WORDER and place server `field: reason` errors on their fields.
  - Password reset and disable/enable go through `ConfirmDialog`.
  - `StaffFormTest` covers client validation and field-error mapping.
  - Verify manually that disable calls the API only after confirmation, and that a duplicate username is shown on the username field.
- [x] 8.3 **Build the audit and account pages.**
  - `/audit` for an ADMIN: actor, action, language and date filters, with paging.
  - `/audit` for a WORDER: activity mode, without the actor filter.
  - `/account`: username, role and languages, plus a password-change form that asks for the new password twice.
  - `PasswordFormTest` asserts a mismatch blocks submit.
  - Verify WORDER mode manually.
- [x] 8.4 **Handle sign-out and Back.** Clear the state before navigating to login. Verify manually in a browser that pressing Back after sign-out shows the login page with no staff data.
- [x] 8.5 **Check the exported panel behind a proxy that adds `/harf`.** For example, run a throwaway Caddy container with `handle_path /harf/*` in front of a local backend with `ADMIN_WEB_DIR=adminWeb/.kobweb/site`. Verify:
  - every page loads;
  - a reload of `/harf/admin/staff` shows the staff page;
  - an unknown `/harf/admin/…` address shows the not-found page;
  - the browser console shows no CSP violations.

## 9. CI, image and configuration (CI & deploy)

- [ ] 9.1 **Add the `admin-web` job** to `.github/workflows/ci.yml`.
  - Java 17 and Gradle setup.
  - A Playwright browser cache keyed by `./gradlew -q -PadminWebOnly :adminWeb:kobwebBrowserCacheId`.
  - `-PadminWebOnly :adminWeb:jsTest`, then the export.
  - Upload `adminWeb/.kobweb/site` as an artifact.
  - Add `admin-web` to `publish-image.needs` and `adminWeb` to `BACKEND_PATHS`.
  - Verify the YAML parses locally and a PR run shows the job green.
- [x] 9.2 **Add the export stage to `backend/Dockerfile`.**
  - Base it on `FROM mcr.microsoft.com/playwright/java:v1.61.0-noble`, pinned by digest after confirming the tag.
  - The stage copies the Gradle wrapper, build files, `sharedData/` and `adminWeb/`, and runs the export with `-PadminWebOnly`.
  - The runtime stage copies `adminWeb/.kobweb/site` to `/app/admin-web` and sets `ENV ADMIN_WEB_DIR=/app/admin-web`.
  - `.dockerignore` keeps `adminWeb/` but excludes `adminWeb/build`, `adminWeb/.kobweb/site` and `adminWeb/.kobweb/server`.
  - Verify `docker build -f backend/Dockerfile .` succeeds and the container serves `/admin/`.
- [x] 9.3 **Document the new variables in `.env.example`.**
  - `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD`, noting they should be removed after the first sign-in.
  - `ADMIN_COOKIE_PATH` and `TRUST_PROXY_HEADERS`, with production values.
  - `ADMIN_DEV_ORIGIN`, marked development only.
  - Verify by reading the file.

## 10. Documentation (docs)

- [x] 10.1 **Update `docs/deploy-oracle.md`**:
  - the new `.env` variables;
  - a panel URL check in the verify section;
  - ADMIN recovery via the bootstrap variables;
  - a rollback note that the new tables need no manual SQL;
  - a note that `up -d --build` on the box runs the Chromium-based export, so the published image is preferred.
  - Verify by reading the sections.
- [x] 10.2 **Update `CLAUDE.md`**:
  - `:adminWeb` in "Where code goes": a Kobweb site, exported statically and served by the backend, with admin DTOs in `:sharedData`;
  - the commands: `-PadminWebOnly`, `:adminWeb:kobwebStart -t` / `kobwebStop`, `:adminWeb:jsTest` and the export command;
  - `adminWeb` in the commit-convention module list;
  - the `admin-web` CI job.
  - Verify by reading the file.

## 11. Verification

- [x] 11.1 `./gradlew -PbackendOnly :sharedData:jvmTest :backend:test` is green against Postgres.
- [x] 11.2 `./gradlew -PadminWebOnly :adminWeb:jsTest` is green, and the export command from task 1.2 succeeds.
- [x] 11.3 With the Android SDK, the default build still configures and `:sharedUI:jvmTest` is unaffected by `:adminWeb`. Failures already known locally are not regressions.
- [x] 11.4 `openspec validate staff-auth-roles --strict` passes.
- [ ] 11.5 **After deploying with the production `.env` values:**
  - sign in at `https://api.lazydevs.uz/harf/admin/` and create a WORDER;
  - reload `https://api.lazydevs.uz/harf/admin/staff` and confirm the page loads;
  - in a second browser, confirm the WORDER lands on words, sees no staff entry, and gets the not-permitted page on the staff address;
  - confirm the session cookie has `Path=/harf`, `Secure`, `HttpOnly` and `SameSite=Strict`;
  - confirm the browser console shows no CSP violations;
  - confirm the player app still syncs stats;
  - remove the bootstrap variables and restart.
