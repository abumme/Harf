# Google Sign-In setup (Android)

How Harf's "Link Google" flow is wired, and the one-time Google Cloud Console
setup it needs. Android uses **Credential Manager** to obtain a Google **ID
token**; the backend verifies that token's `aud` against the **Web** client ID.

## The three client IDs — who uses which

| Kind | Value | Where it's used |
|------|-------|-----------------|
| **Web** | `508164918683-9rce0g2mrjqn9pcshk33kdf870rg1hua.apps.googleusercontent.com` | The app sends it as `serverClientId`; the backend verifies token `aud` against it. **This is the only ID referenced in code/config.** |
| **Android (debug)** | `508164918683-1lto6b3598v6ea32a1sviv4kl12pm0rn...` | Never referenced in code. Just authorizes the `uz.abumme.harfgame` + debug-SHA pair in Google's registry so Credential Manager returns a token. |
| **Android (release)** | *not created yet* | Same role, for the Play App Signing certificate. Required before Play launch. |

Key idea: **Android OAuth clients carry no secret and are never named in code.**
They only tell Google "this package + this signing cert is allowed to ask."
The app always presents the **Web** client ID; the backend always verifies
against the **Web** client ID.

### Where the Web ID lives

- Client: `sharedUI/build.gradle.kts` → `GOOGLE_SERVER_CLIENT_ID` build-config field.
- Backend: runtime env `GOOGLE_CLIENT_IDS` (read in `Application.kt` via
  `System.getenv`). On the server it goes in `~/harf/.env` — **not** a build-time
  secret, **not** in GitHub secrets. The GHCR image build never needs it.

## Console setup (one-time)

1. **OAuth consent screen** (APIs & Services → OAuth consent screen / "Branding"):
   app name `Harf`, audience **External**, support + contact email.
2. **Publishing status = Testing** for now → add every tester's Gmail under
   **Audience → Test users** (cap 100, no Google verification needed). A Gmail
   not listed gets `sign-in failed` immediately, before the token ever reaches
   the backend. Flip to **Production** at public launch (basic
   `openid/email/profile` scopes need no verification review).
3. **Android OAuth client** (Credentials → Create → OAuth client ID → Android):
   package `uz.abumme.harfgame`, plus the SHA-1 of the signing cert (below).
4. **Web client, for the browser game** (`WebGoogleOAuthClient`: a popup with
   `response_type=id_token`, answered by `webApp`'s `oauth-callback.html`):
   Authorized JavaScript origin `https://harf.lazydevs.uz`, Authorized redirect URI
   `https://harf.lazydevs.uz/oauth-callback.html`. The redirect URI is derived from
   the page's address, so a copy served elsewhere (a local `http://localhost:<port>/`)
   needs its own `…/oauth-callback.html` entry, or Google answers
   `redirect_uri_mismatch` in the popup.

## SHA-1 fingerprints

Debug (from `~/.android/debug.keystore`, shared across dev machines/emulators):

```
71:0E:FF:B9:2F:B0:7E:69:57:9B:EF:06:84:0D:FF:AC:2C:D6:B6:28
```
```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

**Release SHA-1 — TODO before Play.** Upload the AAB, then Play Console →
Release → Setup → App signing → copy the **App signing key certificate** SHA-1
(Google re-signs the app). Add it as a second Android OAuth client (same
package). Without it, Google sign-in fails on installs from Play.

## Testing against a local backend

Debug build defaults `apiBaseUrl` to `http://localhost:8080`. On a physical
phone `localhost` is the phone, so tunnel it to the dev machine with
`adb reverse`; on the emulator use `10.0.2.2`.

Cleartext HTTP is blocked by default on API 28+. `src/debug/AndroidManifest.xml`
sets `android:usesCleartextTraffic="true"` for **debug only** — never merged
into release.

```bash
# 1. Postgres (defaults match the backend: harf / harf_password / harf)
docker run -d --name harf-postgres -p 5432:5432 \
  -e POSTGRES_USER=harf -e POSTGRES_PASSWORD=harf_password -e POSTGRES_DB=harf postgres:16

# 2. Backend (dev mode: no HARF_ENV, JWT_SECRET optional; only the Web ID matters)
GOOGLE_CLIENT_IDS=508164918683-9rce0g2mrjqn9pcshk33kdf870rg1hua.apps.googleusercontent.com \
  ./gradlew :backend:run
curl http://localhost:8080/            # -> "Harf Backend is running"

# 3. Physical phone: tunnel + install
adb reverse tcp:8080 tcp:8080
./gradlew :androidApp:assembleDebug -Pharf.apiBaseUrl=http://localhost:8080
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
#   (signature mismatch on install → `adb uninstall uz.abumme.harfgame` first)

# Emulator instead: skip adb reverse, build with -Pharf.apiBaseUrl=http://10.0.2.2:8080
```

Then Settings → **Link Google** with a test-user Gmail. Verify server-side:

```sql
SELECT provider, user_id FROM oauth_identities;   -- GOOGLE row = linked
```

## Production

Release builds bake the production URL (no override needed: Android
`https://api.lazydevs.uz/harf`, web/iOS/desktop `https://harf.lazydevs.uz`) and
refuse loopback URLs. The Oracle box's `~/harf/.env` carries
`GOOGLE_CLIENT_IDS`; see `docs/deploy-oracle.md`.
