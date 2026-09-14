# Deploy the Harf backend to Oracle Cloud (free tier, Ubuntu)

Runs Postgres + the Ktor backend in Docker, both bound to `127.0.0.1`. The host's Caddy terminates
TLS and reverse-proxies `https://<domain>` → `127.0.0.1:8081`.

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
for the better part of an hour and may still be OOM-killed. Prefer the pull.

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
```

## 9. Point the app at it

The release default is already `https://api.lazydevs.uz/harf` (baked into the build), so a plain
release build targets production:
```bash
./gradlew :androidApp:assembleRelease
# or override: -Pharf.apiBaseUrl=https://api.lazydevs.uz/harf
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

Recompiling instead (only on a shape with ~2 GB spare):
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
lists the exact SQL under its Migration Plan.

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

## Notes

- Backend and Postgres listen on `127.0.0.1` only; the sole public entry is Caddy (80/443).
- `HARF_ENV=production` makes the server refuse to start without `JWT_SECRET` — intentional.
- Rotate `JWT_SECRET` only when you intend to invalidate all existing sessions.
