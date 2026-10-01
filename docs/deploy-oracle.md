# Deploy the Harf backend to Oracle Cloud (free tier, Ubuntu)

Runs Postgres + the Ktor backend in Docker, both bound to `127.0.0.1`. The host's Caddy terminates
TLS and reverse-proxies `https://<domain>` → `127.0.0.1:8081`. The backend image also carries the
staff admin panel (`adminWeb/`, exported statically at image build time) and serves it at
`https://api.lazydevs.uz/harf/admin/` — no separate service and no Caddy change (§14).

**Check the shape's architecture first — `uname -m`.** The published image is built for
**`linux/amd64`** (Oracle AMD shapes). On an Ampere shape (`aarch64`) that image won't run; either
flip the workflow to arm64 (see §12) or build on the box, which is arch-agnostic.

The backend publishes on host port **8081**, not 8080 — 8080 is usually already taken. Override with
`HARF_HOST_PORT` in `.env`.

Files: [`backend/Dockerfile`](../backend/Dockerfile), [`docker-compose.prod.yml`](../docker-compose.prod.yml),
[`.env.example`](../.env.example), [`.github/workflows/ci.yml`](../.github/workflows/ci.yml),
[`.github/scripts/deploy-backend.sh`](../.github/scripts/deploy-backend.sh).

## 1. Open the ports (two firewalls — this is the #1 Oracle gotcha)

**a. Cloud Security List / NSG** (Oracle console → VCN → your subnet → Security List → Add Ingress Rules):
open **TCP 80** and **TCP 443** from `0.0.0.0/0`. SSH (22) is already open. Do **not** open 8081 or 5432.

**b. The instance's own iptables** (Oracle Ubuntu images block everything but 22):
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 2. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker   # run docker without sudo
```
`docker compose` (v2 plugin) ships with it.

## 3. Get the code and configure secrets

```bash
git clone https://github.com/abumme/Harf.git ~/harf && cd ~/harf
cp .env.example .env
# generate real secrets:
sed -i "s|JWT_SECRET=CHANGE_ME|JWT_SECRET=$(openssl rand -base64 48)|" .env
sed -i "s|CHANGE_ME_strong_db_password|$(openssl rand -base64 24)|" .env
nano .env   # confirm GOOGLE_CLIENT_IDS is your Web client id
```

In the same `.env`, the staff admin panel needs (see `.env.example` for details):

| Variable | Production value |
|---|---|
| `ADMIN_COOKIE_PATH` | `/harf` — the staff cookies are never sent to the other apps on `api.lazydevs.uz` |
| `TRUST_PROXY_HEADERS` | `true` — the login rate limit keys on the client address Caddy reports |
| `ADMIN_BOOTSTRAP_USERNAME`, `ADMIN_BOOTSTRAP_PASSWORD` | the first ADMIN (e.g. `openssl rand -base64 24` for the password); **remove both after the first sign-in** (§14) |
| `ADMIN_DEV_ORIGIN` | leave empty — local development only, ignored when `HARF_ENV=production` |
| `DAILY_HISTORY_START` | the public launch date, e.g. `2026-10-01`: days before it never count as used by the daily-word calendar. Empty while testing: each calendar counts from its first run (§14) |

`ADMIN_WEB_DIR` is set inside the image; don't set it in `.env`.

### Sign in with Apple key

Deleting an account must revoke the user's Apple token (Apple requires it, TN3194), which needs the
Sign in with Apple `.p8`. Without it linking still works and deletion revokes nothing — the backend
says so once at startup and carries on.

Create the key in Apple Developer → Certificates, Identifiers & Profiles → Keys with *Sign in with
Apple* enabled, associated with the primary App ID; the `.p8` downloads **once**. Then, on the box:

```bash
mkdir -p ~/harf/secrets && chmod 700 ~/harf/secrets
# scp the key across, then:
chmod 600 ~/harf/secrets/AuthKey_<KEYID>.p8
```

`docker-compose.prod.yml` mounts `./secrets` read-only at `/run/secrets/apple`, so `.env` names the
path **inside the container** — a host path silently reads as nothing:

| Variable | Production value |
|---|---|
| `APPLE_AUDIENCES` | `uz.abumme.harfgame` — the iOS bundle id the identity tokens carry |
| `APPLE_TEAM_ID` | the 10-character team id (Apple Developer → Membership details) |
| `APPLE_KEY_ID` | the key id, i.e. what sits between `AuthKey_` and `.p8` in the file name |
| `APPLE_PRIVATE_KEY_PATH` | `/run/secrets/apple/AuthKey_<KEYID>.p8` — the container path, not `~/harf/secrets/...` |
| `APPLE_PRIVATE_KEY`, `APPLE_CLIENT_ID` | leave empty: the key comes from the file, and the client id defaults to the first `APPLE_AUDIENCES` entry |

Verify after `up -d` — the warning is the only signal, a working key logs nothing:

```bash
docker compose -f docker-compose.prod.yml logs backend | grep -i "sign in with apple" || echo "key loaded"
docker compose -f docker-compose.prod.yml exec backend ls -l /run/secrets/apple/   # if it warns
```

`no key is configured` means the path is wrong; `could not be read` means the file was found but
doesn't parse as a PKCS#8 EC key (truncated download, or not a `.p8`).

Only ever run this on a **fresh** box. Regenerating `JWT_SECRET` on an existing deployment logs
every user out, and a new `POSTGRES_PASSWORD` won't match the password already baked into the
`postgres_data` volume — the backend then fails to connect.

Confirm the host ports in `.env` are actually free before starting:
```bash
sudo ss -ltnp | grep -E ':(8081|5432)\b' || echo "both free"
```

**If the repo itself is private**, this clone needs credentials too — reuse the deploy PAT from §5
with `repo` scope added: `git clone https://<user>:<TOKEN>@github.com/abumme/Harf.git ~/harf`
(note this writes the token into `.git/config`). Or skip cloning altogether: the box only needs
`docker-compose.prod.yml` and `.env`, which you can `scp` across and keep in `~/harf`.

## 4. Add swap (mandatory on the 1 GB shapes)

`free -h` first. The `VM.Standard.E2.1.Micro` shape has 954 MiB and ships with **no swap**, which
isn't enough to run a JVM plus Postgres — never mind compiling. Add it once:

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # survives reboot
free -h                                                       # confirm Swap: 2.0Gi
```

The `.env` defaults are sized for this: `JAVA_OPTS=-Xmx256m`, `DB_MAX_POOL_SIZE=5`, and Postgres
runs with `shared_buffers=64MB` (set in `docker-compose.prod.yml`). On a larger shape you can raise
all three.

## 5. Run it

The GHCR package is **private**, so the box authenticates before it can pull. Create a *classic*
PAT — fine-grained tokens don't cover GHCR — at `https://github.com/settings/tokens` → Generate new
token (classic), tick **`read:packages`** and nothing else. Then on the box:

```bash
echo <TOKEN> | docker login ghcr.io -u <github-username> --password-stdin
```

Run that as the **same user that runs compose**. Credentials land in that user's
`~/.docker/config.json`, so a `sudo docker login` writes to `/root/.docker` and a later rootless
`docker compose pull` still fails with `denied`. The login survives reboots; if you gave the token
an expiry, pulls start failing with `denied` when it lapses and you re-run the command with a new
token.

Then pull and start — on a small shape this is the only realistic route, since compiling needs
~2 GB of its own:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f backend   # watch startup
```

The image architecture must match the shape (§12).

> Making the package public instead (Package settings → Change visibility) removes the login step
> entirely. That exposes only the built image, **not** the source repo — they're separate settings.
> The image does contain the compiled backend, though, so it's still a real disclosure.

**Compiling on the box instead** (`up -d --build`) needs ~2 GB of RAM. It's fine on a 6 GB+ Ampere
shape and is the one route that needs no registry at all, but on the 1 GB micro it will thrash swap
for the better part of an hour and may still be OOM-killed. It also runs the staff panel's export
stage: a ~2 GB Playwright image, a Kotlin/JS build and a headless Chromium snapshotting every page,
which is slow and memory-hungry on its own. Prefer the pull.

Tables are created automatically on first start. Health check:
```bash
curl http://127.0.0.1:8081/          # -> "Harf Backend is running"
```

## 6. Point Caddy at it

Caddy is already installed and `api.lazydevs.uz` serves apps by path, so mount Harf under `/harf`.
`handle_path` strips the prefix, so the backend still sees `/api/v1/...`. Add to `/etc/caddy/Caddyfile`
(inside the existing `api.lazydevs.uz { ... }` block if you have one, else create it):
```
api.lazydevs.uz {
    handle_path /harf/* {
        reverse_proxy 127.0.0.1:8081
    }
    # ... your other path blocks ...
}
```
Validate before reloading — a broken Caddyfile takes down every other site on the host:
```bash
sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak
sudo caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile   # expect "Valid configuration"
sudo systemctl reload caddy    # reload, not restart — restart drops live connections
```

Caddy auto-issues/renews the Let's Encrypt certificate — needs the DNS record below and ports 80/443
open (step 1). **The first request after adding a new hostname usually fails** with
`tlsv1 alert internal error`: issuance takes a few seconds and the cert doesn't exist yet. Wait and
retry before assuming something's broken. `sudo journalctl -u caddy -n 30` shows the ACME progress.

## 7. DNS

`api.lazydevs.uz` **A record** → your instance's public IP (likely already set if the domain serves other apps).

## 8. Verify end-to-end

```bash
curl https://api.lazydevs.uz/harf/    # -> "Harf Backend is running", valid TLS
curl -sI https://api.lazydevs.uz/harf/admin/staff | grep -iE '^HTTP|content-security-policy|x-frame-options'
#   -> HTTP/2 200 plus the panel's CSP and "x-frame-options: DENY"
```
Then open `https://api.lazydevs.uz/harf/admin/` in a browser: the Russian login page loads (§14 for
the first sign-in). A `404` there means the running image predates the panel.

## 9. Point the app at it

The production URL is baked into the build, so a plain release build targets production: Android calls
`https://api.lazydevs.uz/harf` (its published builds already do), web, iOS and desktop call the game's own domain
`https://harf.lazydevs.uz` (§15). Both reach the same backend.
```bash
./gradlew :androidApp:assembleRelease
# or override every target: -Pharf.apiBaseUrl=https://api.lazydevs.uz/harf
```
`GOOGLE_CLIENT_IDS` is already set in `.env`, so the server trusts Google sign-in tokens.

Testing over the raw IP instead? Use a **debug** build (`-Pharf.apiBaseUrl=http://<public-ip>:8081`,
and temporarily publish port 8081) — a release build requires trusted HTTPS.

## 10. Updates

Once §13 is set up this is automatic: a push to `main` that touches the backend runs the tests,
publishes the image and deploys it. By hand (or before §13 is configured), on the box:
```bash
cd ~/harf && git pull          # picks up compose/.env.example changes
docker compose -f docker-compose.prod.yml pull backend
docker compose -f docker-compose.prod.yml up -d
docker image prune -f          # drop the superseded image
```

Recompiling instead (only on a shape with ~2 GB spare; this also runs the Chromium-based panel
export, §5):
```bash
cd ~/harf && git pull && docker compose -f docker-compose.prod.yml up -d --build
```

CI only ships the image. It never touches `docker-compose.prod.yml` or `.env` on the box, so after a
change to either, still `git pull` (or `scp`) and `up -d` by hand.

## 11. Backups

```bash
# dump (add to cron):
docker exec harf-postgres pg_dump -U harf harf | gzip > harf-$(date +%F).sql.gz
# restore:
gunzip -c harf-YYYY-MM-DD.sql.gz | docker exec -i harf-postgres psql -U harf -d harf
```

## 12. Publishing the image

The `publish-image` job in `.github/workflows/ci.yml` builds and pushes once the backend tests pass,
on every push to `main` that touches backend inputs (and on `v*` tags), tagging `latest`, the git
tag, and `sha-<short>`. It runs on `ubuntu-24.04` and builds `linux/amd64`, matching the current AMD
deploy shape.

**Moving to an Ampere/aarch64 shape?** Change both lines of the `publish-image` job together —
`runs-on: ubuntu-24.04-arm` and `platforms: linux/arm64`. GitHub's arm runner is free on public repos and ~10x faster than QEMU.
Leaving them mismatched with the server yields `no matching manifest for linux/<arch>` on pull.

To push by hand from a workstation:
```bash
docker login ghcr.io -u <github-username>      # classic PAT with write:packages
docker buildx build --platform linux/amd64 \
  -f backend/Dockerfile -t ghcr.io/abumme/harf-backend:latest --push .
```
Building the *other* architecture runs the whole Gradle build under QEMU — budget 15–30 minutes.
Prefer CI.

## 13. Continuous deployment

After `publish-image`, the `deploy` job in `.github/workflows/ci.yml` SSHes into the box and runs
[`.github/scripts/deploy-backend.sh`](../.github/scripts/deploy-backend.sh), which:

1. pulls the image CI just built, by digest;
2. stops there if its layers match what is already running (a CI-only change, say), so nothing restarts;
3. points the compose file's image tag at it and recreates the `backend` container only (Postgres is
   never touched);
4. waits up to ~90 s for `Harf Backend is running` on the loopback port Caddy proxies to;
5. if it never comes up, puts the previous image back and fails the run, so a bad build can't leave
   production crash-looping.

That automatic rollback can't cross a schema change. A new image adds its tables and columns as it
starts, before the health check. The previous image then sees those columns as unmapped, its startup
guard refuses to drop them, and it won't start either: the run ends with `Rollback is unhealthy too`.
Roll forward instead (fix and redeploy, or switch the new feature off in `.env`), or drop the added
columns by hand before starting the older image. The OpenSpec design of the change that added them
lists the exact SQL under its Migration Plan. New *tables* are different: an older image's schema check
only looks at the tables it knows, so rolling back past the staff panel (tables `staff`,
`staff_languages`, `staff_sessions`, `staff_audit_log`) needs no SQL — they just sit unused, and the
older image serves no panel.

It re-runs compose with the project, directory and files the running container was started with,
so it works wherever you set the stack up. It only *updates* a running stack; the first start is
still §5.

### One-time setup

On your workstation, make a keypair used only by CI (no passphrase, since CI can't type one):
```bash
ssh-keygen -t ed25519 -N "" -C harf-ci-deploy -f harf_ci_deploy
```

On the box, as **the user that runs compose** (the one holding the GHCR login from §5, in the
`docker` group), authorize the public key. The `restrict` prefix disables port/agent/X11 forwarding
and PTYs for this key:
```bash
echo 'restrict ssh-ed25519 AAAA…paste harf_ci_deploy.pub here… harf-ci-deploy' >> ~/.ssh/authorized_keys
```

Back on the workstation, store four repository secrets and delete the local private key:
```bash
gh secret set ORACLE_SSH_HOST --repo abumme/Harf --body '<public IP or hostname>'
gh secret set ORACLE_SSH_USER --repo abumme/Harf --body '<that user, e.g. ubuntu>'
gh secret set ORACLE_SSH_KEY  --repo abumme/Harf < harf_ci_deploy
ssh-keyscan -t ed25519 <host> | gh secret set ORACLE_SSH_KNOWN_HOSTS --repo abumme/Harf
rm harf_ci_deploy
```

Check the scanned host key before trusting it: `ssh-keyscan -t ed25519 <host> | ssh-keygen -lf -`
locally must print the same fingerprint as `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on the
box. That pin is what stops a spoofed host from receiving deploys.

These are repository secrets because GitHub Environments aren't available to private repos on the
org's Free plan. `docker` group membership is root-equivalent, so treat `ORACLE_SSH_KEY` like a root
credential: anyone who can push a workflow to this repo can use it.

### Operating it

- **Redeploy or retry:** Actions → CI → Run workflow on `main`. It republishes and redeploys, and is a
  no-op if the running image already has those layers.
- **`denied` on pull:** the box's GHCR token (§5) has expired. Log in again on the box, then re-run.
- **Roll back by hand:** every build stays on GHCR as `sha-<short>`:
  ```bash
  docker pull ghcr.io/abumme/harf-backend:sha-<short>
  docker tag ghcr.io/abumme/harf-backend:sha-<short> ghcr.io/abumme/harf-backend:latest
  docker compose -f docker-compose.prod.yml up -d backend
  ```
  An image older than a schema change won't start until that change's columns are dropped by hand
  (see the note under the deploy steps above).

## 14. Staff admin panel

`https://api.lazydevs.uz/harf/admin/` is the staff panel (ADMIN and WORDER accounts, separate from
players). The backend serves the exported panel from the image (`ADMIN_WEB_DIR=/app/admin-web`) at
`/admin`; Caddy's existing `handle_path /harf/*` block covers it, so there is nothing to add to the
Caddyfile.

**First ADMIN.** With `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD` in `.env` (§3), the
backend creates that ADMIN at startup while no active ADMIN exists. The log says
`Bootstrapped ADMIN '<name>'`; a password outside 12–128 characters logs an error and creates
nothing (the player API starts regardless). Then:

1. `docker compose -f docker-compose.prod.yml up -d` so the container picks up the new `.env`;
2. sign in at `https://api.lazydevs.uz/harf/admin/`, create the staff accounts you need;
3. remove both `ADMIN_BOOTSTRAP_*` lines from `.env` and `up -d` again. While an active ADMIN exists
   and they are still set, every start logs a warning to remove them.

**Recovery** when no ADMIN can sign in any more: put the bootstrap variables back, naming the account
to recover (or a new username), and restart. The variables act only while no *active* ADMIN exists;
then the server makes that account an active ADMIN with the given password, clears its lock and ends
its sessions, and the audit log records `STAFF_BOOTSTRAPPED` by the system. Remove the variables again
afterwards.

- Every ADMIN disabled: the variables alone are enough.
- The only active ADMIN lost their password: that account is still active, so the variables are
  ignored. Mark it disabled first, then restart with the variables naming it:
  ```bash
  docker exec harf-postgres psql -U harf -d harf -c "UPDATE staff SET status = 'DISABLED' WHERE username = '<name>'"
  ```
- One ADMIN among several lost their password: another ADMIN resets it in the panel.

**Checks after a deploy that changes the panel:** the session cookie `harf_admin_session` shows
`Path=/harf`, `Secure`, `HttpOnly`, `SameSite=Strict` in the browser's dev tools, and the console shows
no Content-Security-Policy violations.

### Word catalog: first deploy

The word catalog moves every language's vocabulary out of the `word_packs` JSON into the `words` table
and adds four columns to `word_suggestions`. `word_packs` stays the published snapshot.

1. **Before deploying**, back up the packs and suggestions on the box:
   ```bash
   docker exec harf-postgres pg_dump -U harf -t word_packs -t word_suggestions harf > ~/word_packs_pre_catalog.sql
   ```
   Keep it until the checks below pass and a staff edit has reached a device; it is the last pack and
   suggestion state from before the catalog.
2. Deploy as usual (§13). At startup the log shows one `Word catalog <lang>: carried over N words from pack
   version V …` line per language (a one-time import; it never runs again for a language whose catalog has
   rows) and then only `merged N new bundled words` lines for languages the image's dictionaries extend.
3. **Verify:** `curl -s https://api.lazydevs.uz/harf/api/v1/wordpacks/en -o /dev/null -D - | grep -i etag`
   shows the same version as before the deploy (the carry-over publishes nothing); the panel's **Слова**
   page lists about 23,000 English words; `curl -s -o /dev/null -D - -H 'Accept-Encoding: gzip'
   https://api.lazydevs.uz/harf/api/v1/wordpacks/en | grep -i content-encoding` shows `gzip` (a GET: the
   pack route does not answer HEAD).

**Link the Telegram editors to staff accounts**, then retire the legacy allowlist:

1. For each Telegram user id in `TELEGRAM_EDITOR_IDS`, create or edit a staff account in the panel
   (**Сотрудники**): set its **Telegram ID** and its languages (an ADMIN decides in every language).
2. Have each editor tap Accept or Reject on a pending suggestion in their language: the message shows the
   outcome, and the panel's **Предложения → История** names the staff member.
3. Remove `TELEGRAM_EDITOR_IDS` from `.env` and `docker compose -f docker-compose.prod.yml up -d`. While it
   is set, every start logs `WARNING: TELEGRAM_EDITOR_IDS is set`.

**Rollback** (prefer rolling forward): the previous image refuses to start while `word_suggestions` has
columns it does not model. Drop them, then redeploy the previous tag:
```bash
docker exec harf-postgres psql -U harf -d harf -c "ALTER TABLE word_suggestions DROP COLUMN telegram_chat_id, DROP COLUMN telegram_message_id, DROP COLUMN telegram_text, DROP COLUMN review_reason"
```
`words` can stay (the old image ignores it) and clients keep the last published pack. The old image's
startup merge is append-only, so bundled words staff removed come back until the catalog is redeployed.

### Daily-word calendar: first deploy

The calendar makes the server own the word of the day: ADMINs pick words for days from the day after tomorrow in the
panel (**Календарь**, **Пул слов дня**), every other day is filled automatically without repeating words, and the
packs' schedule is rebuilt from it. It adds the tables `lexeme_pairs`, `daily_words`, `calendar_notices` and
`calendar_state` (nothing is dropped or rewritten).

1. Set `DAILY_HISTORY_START` in `.env` to the public launch date, or leave it empty while testing (§3). Changing it
   later can change future automatic picks, never a past day, today or tomorrow.
2. **Before deploying**, note today's and tomorrow's word per language:
   ```bash
   for l in en ru kk uz-latn uz-cyrl; do curl -s https://api.lazydevs.uz/harf/api/v1/wordpacks/$l > ~/pack_pre_calendar_$l.json; done
   ```
3. Deploy as usual (§13). At startup, once per calendar, the log shows
   `Daily calendar <en|kk|ru|uz>: imported N legacy days, M eligible words (…), R repeats scheduled`: the stored schedule
   through tomorrow became history, today's answers became the answer pool (Uzbek: the pairs of `uz_lexemes.tsv`), and
   the next 60 days were filled. Later starts log nothing for these calendars. Every minute the server extends each
   calendar as days pass, so every pack's version advances about once a day.
4. **Verify:** today's and tomorrow's words are unchanged — the pack's `anchorEpochDay` stays `20454` (2026-01-01), so
   the word of a day is `schedule[day - 20454]`:
   ```bash
   for l in en ru kk uz-latn uz-cyrl; do curl -s https://api.lazydevs.uz/harf/api/v1/wordpacks/$l | jq -r --arg d $(( $(date -u +%s) / 86400 )) '.schedule[($d|tonumber) - .anchorEpochDay:($d|tonumber) - .anchorEpochDay + 2] | join(", ")'; done
   ```
   (compare with the same expression on `~/pack_pre_calendar_<lang>.json`; around midnight use each language's own day).
   The panel's **Календарь** shows 60 days ahead per calendar; signed in as a WORDER, `/harf/api/v1/admin/calendar/en`
   answers `403` and the navigation has no calendar entries. Pick a word for the day after tomorrow and check it appears
   in `GET /api/v1/wordpacks/<lang>` with a new version.

**Rollback:** redeploy the previous image; nothing to drop. It ignores the new tables and keeps serving the last
published schedule, which it never changes.

**Before a store release**, refresh the calendar snapshot the app bundles for offline fresh installs:
`./gradlew :sharedUI:refreshCalendarSnapshot` (production by default; `-Pharf.apiBaseUrl=…` for another server), review
and commit `sharedUI/src/commonMain/composeResources/files/<lang>_calendar.json`, or run the **Refresh calendar
snapshot** workflow, which opens a pull request with them.

## 15. Web app

The browser build (`:webApp:composeCompatibilityBrowserDistribution`: Wasm, falling back to JS) is static files
served by the host's Caddy at `https://harf.lazydevs.uz/`, from `/srv/harf-web/current`. The same site proxies `/api/*`
to the backend, and the web build calls `https://harf.lazydevs.uz/api/v1/...` (the default for web, iOS and desktop,
§9), so its calls are same-origin and need no CORS (the backend allows none in production).

**Continuous deployment.** On a push to `main` that touches web app inputs (`webApp`, `sharedUI`, `sharedData`, the
Gradle build), or on **Run workflow**, `client-tests` builds the distribution and uploads it as the `web-app` artifact,
then the `deploy-web` job ships it over the same SSH secrets as §13 and runs
[`.github/scripts/deploy-web.sh`](../.github/scripts/deploy-web.sh) on the box. That script unpacks it into
`/srv/harf-web/releases/<time>-<sha>`, swaps the `current` symlink atomically and keeps the last 5 releases. The job then
fetches the public URL through Caddy and fails if it doesn't serve the app.

### One-time setup

The web root belongs to the CI user from §13, so deploys need no sudo:
```bash
sudo install -d -o <CI user> -g <CI user> -m 755 /srv/harf-web
```

Point a `harf.lazydevs.uz` **A record** at the instance, add this site to `/etc/caddy/Caddyfile`, then validate and
reload as in §6:
```
harf.lazydevs.uz {
    handle /api/* {
        reverse_proxy 127.0.0.1:8081
    }
    handle {
        root * /srv/harf-web/current
        header Cache-Control "no-cache"
        file_server
    }
}
```
Only `/api/*` reaches the backend here: the staff panel stays on `api.lazydevs.uz/harf/admin/` with its `/harf` cookie
path. Keep the `handle_path /harf/*` block of `api.lazydevs.uz` too — Android builds and the panel use it, and the
backend answers the same on both doors. Builds before the move also served the game at `api.lazydevs.uz/harf/play/`;
`redir /harf/play/* https://harf.lazydevs.uz/ 308` in that block sends old links over.

Every file is revalidated on each load (a cheap `304` when unchanged):
most of the bundle (`webApp.js`, `originWasmWebApp.js`, `skiko.*`, `composeResources/`) keeps its name across
builds, so a cached copy would mix old and new code after a deploy.

### Operating it

- **Redeploy:** Actions → CI → Run workflow on `main`.
- **Roll back by hand:** `ls /srv/harf-web/releases`, then
  `ln -sfn /srv/harf-web/releases/<older> /srv/harf-web/current.next && mv -Tf /srv/harf-web/current.next /srv/harf-web/current`.
  No Caddy reload is needed.
- Purchases are unavailable on the web (the no-op billing). Paid themes and Founder extras stay locked there unless
  the signed-in account bought them on a phone (read from `GET /api/v1/entitlements`, which needs
  `REVENUECAT_SECRET_KEY`). Google sign-in on the web is a popup returning to `/oauth-callback.html`; its origin and
  redirect URI are registered on the Web client (`docs/google-signin-setup.md`).

## Notes

- Backend and Postgres listen on `127.0.0.1` only; the sole public entry is Caddy (80/443).
- `HARF_ENV=production` makes the server refuse to start without `JWT_SECRET` — intentional. It also
  makes the staff cookies `Secure` and disables the development-only CORS for the panel's dev server.
- Rotate `JWT_SECRET` only when you intend to invalidate all existing sessions.
