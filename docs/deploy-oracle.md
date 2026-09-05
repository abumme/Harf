# Deploy the Harf backend to Oracle Cloud (free tier, Ubuntu)

Runs Postgres + the Ktor backend in Docker, both bound to `127.0.0.1`. The host's Caddy terminates
TLS and reverse-proxies `https://<domain>` → `127.0.0.1:8080`. Works on an Ampere (aarch64) or AMD
(x86_64) shape — the images are multi-arch.

Files: [`backend/Dockerfile`](../backend/Dockerfile), [`docker-compose.prod.yml`](../docker-compose.prod.yml),
[`.env.example`](../.env.example).

## 1. Open the ports (two firewalls — this is the #1 Oracle gotcha)

**a. Cloud Security List / NSG** (Oracle console → VCN → your subnet → Security List → Add Ingress Rules):
open **TCP 80** and **TCP 443** from `0.0.0.0/0`. SSH (22) is already open. Do **not** open 8080 or 5432.

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
git clone <your-repo-url> harf && cd harf
cp .env.example .env
# generate real secrets:
sed -i "s|JWT_SECRET=CHANGE_ME|JWT_SECRET=$(openssl rand -base64 48)|" .env
sed -i "s|CHANGE_ME_strong_db_password|$(openssl rand -base64 24)|" .env
nano .env   # confirm GOOGLE_CLIENT_IDS is your Web client id
```

## 4. Build and run

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend   # watch startup
```
Tables are created automatically on first start. Health check:
```bash
curl http://127.0.0.1:8080/          # -> "Harf Backend is running"
```

> Low-RAM shape (the 1 GB AMD micro)? The Gradle build in Docker can OOM. Add swap first:
> `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`
> (Ampere shapes have plenty of RAM — no swap needed.)

## 5. Point Caddy at it

Caddy is already installed and `api.lazydevs.uz` serves apps by path, so mount Harf under `/harf`.
`handle_path` strips the prefix, so the backend still sees `/api/v1/...`. Add to `/etc/caddy/Caddyfile`
(inside the existing `api.lazydevs.uz { ... }` block if you have one, else create it):
```
api.lazydevs.uz {
    handle_path /harf/* {
        reverse_proxy 127.0.0.1:8080
    }
    # ... your other path blocks ...
}
```
```bash
sudo systemctl reload caddy
```
Caddy auto-issues/renews the Let's Encrypt certificate — needs the DNS record below and ports 80/443 open (step 1).

## 6. DNS

`api.lazydevs.uz` **A record** → your instance's public IP (likely already set if the domain serves other apps).

## 7. Verify end-to-end

```bash
curl https://api.lazydevs.uz/harf/    # -> "Harf Backend is running", valid TLS
```

## 8. Point the app at it

The release default is already `https://api.lazydevs.uz/harf` (baked into the build), so a plain
release build targets production:
```bash
./gradlew :androidApp:assembleRelease
# or override: -Pharf.apiBaseUrl=https://api.lazydevs.uz/harf
```
`GOOGLE_CLIENT_IDS` is already set in `.env`, so the server trusts Google sign-in tokens.

Testing over the raw IP instead? Use a **debug** build (`-Pharf.apiBaseUrl=http://<public-ip>:8080`,
and temporarily publish port 8080) — a release build requires trusted HTTPS.

## 9. Updates

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

## 10. Backups

```bash
# dump (add to cron):
docker exec harf-postgres pg_dump -U harf harf | gzip > harf-$(date +%F).sql.gz
# restore:
gunzip -c harf-YYYY-MM-DD.sql.gz | docker exec -i harf-postgres psql -U harf -d harf
```

## Notes

- Backend and Postgres listen on `127.0.0.1` only; the sole public entry is Caddy (80/443).
- `HARF_ENV=production` makes the server refuse to start without `JWT_SECRET` — intentional.
- Rotate `JWT_SECRET` only when you intend to invalidate all existing sessions.
