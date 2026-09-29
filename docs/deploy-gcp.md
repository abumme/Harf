# Deploy the Harf backend to Google Cloud (Cloud Run + Cloud SQL)

Runs the backend as a single always-on Cloud Run instance against a managed Postgres (Cloud SQL),
reached over a private IP so the container keeps using a plain JDBC URL. No application code
changes: the image, the env vars and the startup sequence are the same ones
[`docs/deploy-oracle.md`](deploy-oracle.md) describes.

**Why one instance.** `Application.kt` starts three in-process loops — the suggestion review worker,
the Telegram long-poll and the daily report timer. Telegram allows exactly one poller per bot, and a
second copy would also send every daily report twice, so the service is pinned to
`--min-instances=1 --max-instances=1` with CPU always allocated. Lifting that pin is the
[Later: multiplayer](#later-multiplayer-and-scaling-past-one-instance) section, not a flag flip.

**Cost.** The free tier does not cover this shape — Cloud SQL has no free tier at all, and an
always-on instance is by definition not serverless-cheap. Budget roughly **$45–50/month for the
Cloud Run instance** (1 vCPU, 1 GiB, never scaled to zero), plus the database: a shared-core
`db-f1-micro` is around $7–10/month but carries no SLA, while the smallest SLA-backed tier costs
several times that. Price your exact region in
<https://cloud.google.com/products/calculator> before committing — these are estimates, and the
Cloud SQL figures in particular come from third-party sources rather than Google's own pricing page.
The $300 trial credit covers the first ~3 months either way.

**What you need before starting:** a Google account, a billing account (a card, even on the trial),
a domain you control, and the repository's `.env` values from the Oracle box if you are migrating.

Files this guide touches: [`backend/Dockerfile`](../backend/Dockerfile) (unchanged),
[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) (new jobs),
[`.env.example`](../.env.example) (the same variable set).

---

## 0. Install the CLI and pick your names

Install the Google Cloud CLI. On Windows, download the installer from
<https://cloud.google.com/sdk/docs/install> and let it run `gcloud init` at the end; it puts
`gcloud` on PATH for new terminals. Then:

```bash
gcloud auth login
gcloud components install beta     # some commands below are still under `beta`
```

Fix these values once and reuse them in every command. Everything below assumes they are exported
in your shell (PowerShell users: `$env:PROJECT_ID = "harf-prod"`).

```bash
export PROJECT_ID=harf-prod          # must be globally unique
export REGION=europe-west1           # see the note below
export SQL_INSTANCE=harf-postgres
export SERVICE=harf-backend
export REPO=harf                     # Artifact Registry repository name
```

**Region.** Pick it once — moving later means rebuilding everything. For users in Uzbekistan the
practical choices are `europe-west1` (Belgium) or `europe-west4` (Netherlands), which are Tier 1
pricing regions, versus `europe-central2` (Warsaw), which is geographically closer but sits in the
more expensive Tier 2. `europe-west1` is the default here: cheapest, and the latency difference to
Tashkent is small next to the round trip itself.

## 1. Create the project and enable the APIs

```bash
gcloud projects create "$PROJECT_ID" --name="Harf"
gcloud config set project "$PROJECT_ID"
```

Link billing — copy your billing account id from `gcloud billing accounts list`:

```bash
gcloud billing accounts list
gcloud billing projects link "$PROJECT_ID" --billing-account=XXXXXX-XXXXXX-XXXXXX
```

Enable everything this guide uses, in one call:

```bash
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  compute.googleapis.com \
  servicenetworking.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iamcredentials.googleapis.com
```

`compute.googleapis.com` looks out of place on a serverless deploy — it is there because Direct VPC
egress (how Cloud Run reaches the database in §2) is built on Compute Engine networking. The first
enable takes a couple of minutes.

## 2. Cloud SQL on a private IP

The backend must talk to Postgres with an ordinary JDBC URL, because that is all
`DatabaseFactory.init()` knows how to do. The way to get that without touching the code is to give
the database a private address inside your VPC and let Cloud Run reach it over Direct VPC egress —
no socket-factory library, no proxy sidecar, and the database never gets a public IP at all.

First, hand Google a range inside your network for its managed services (the default VPC is fine;
a dedicated network only buys isolation):

```bash
gcloud compute addresses create google-managed-services-default \
  --global --purpose=VPC_PEERING --prefix-length=16 --network=default

gcloud services vpc-peerings connect \
  --service=servicenetworking.googleapis.com \
  --ranges=google-managed-services-default \
  --network=default
```

A `/24` is the documented minimum; `/16` is what Google recommends and what the worked example uses.

Now the instance. Pick the tier deliberately:

```bash
gcloud sql instances create "$SQL_INSTANCE" \
  --database-version=POSTGRES_16 \
  --region="$REGION" \
  --network="projects/$PROJECT_ID/global/networks/default" \
  --no-assign-ip \
  --tier=db-custom-1-3840 \
  --availability-type=ZONAL \
  --storage-size=10 --storage-type=SSD \
  --backup-start-time=20:00 \
  --enable-point-in-time-recovery \
  --root-password='CHANGE_ME_strong_root_password'
```

- **`--tier`** — `db-custom-1-3840` is 1 vCPU / 3.75 GB, the smallest tier Google will stand behind.
  `db-f1-micro` is cheaper (third-party estimates put it near $7–10/month; Google's own pricing page
  wouldn't give a clean figure) but its docs say plainly that shared-core machines "aren't included
  in the Cloud SQL SLA" and are "designed to provide low-cost test and development instances only.
  Don't use them for production instances." Use `db-f1-micro` while you're evaluating on the trial
  credit, and move up before real users arrive — changing tier later means a restart, not a rebuild.
- **`--no-assign-ip`** — no public IP, ever. The only route in is your VPC.
- **`--backup-start-time`** is UTC. `20:00` UTC is 01:00 in Tashkent, i.e. after the daily rollover.
- **`--enable-point-in-time-recovery`** is the thing you're actually paying Cloud SQL for; it's what
  the Oracle box's hand-rolled `pg_dump` cron cannot do.

Create the database and the application user (the backend defaults to `harf`/`harf`):

```bash
gcloud sql databases create harf --instance="$SQL_INSTANCE"
gcloud sql users create harf --instance="$SQL_INSTANCE" --password='CHANGE_ME_strong_db_password'
```

Then grab the private address — every later step needs it:

```bash
export DB_PRIVATE_IP=$(gcloud sql instances describe "$SQL_INSTANCE" \
  --format="value(ipAddresses.filter(\"type:PRIVATE\").extract(\"ipAddress\"))")
echo "$DB_PRIVATE_IP"
```

A few things the docs warn about, which bite later rather than now:

- The subnet Cloud Run egresses through must be **in the same region** as the service and at least a
  `/26`. Cloud Run consumes roughly twice as many IPs as it has instances, so an undersized subnet
  fails with "insufficient free IP addresses in the subnetwork."
- The first start after Direct VPC egress is configured "might experience connection establishment
  delays of a minute or more" — don't diagnose a broken deploy in the first 60 seconds.
- Whether the runtime account also needs `roles/cloudsql.client` on this path is genuinely murky in
  the docs: that role exists for the Admin-API/proxy mechanism, and the private-IP samples don't use
  it. Deploy without it; if connections fail with a permission error rather than a timeout, grant it
  with `gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$RUNTIME_SA"
  --role=roles/cloudsql.client`.

## 3. Push the first image

Cloud Run can pull public images straight from GHCR, but `abumme/Harf` is private, and private GHCR
images require an Artifact Registry remote repository to proxy them. Pushing to Artifact Registry
directly is simpler and is what §9 automates.

```bash
gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Harf backend images"

gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

export IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/backend:bootstrap"
docker build --platform linux/amd64 -f backend/Dockerfile -t "$IMAGE" .
docker push "$IMAGE"
```

`--platform linux/amd64` is not optional: Cloud Run "specifically supports the Linux x86_64 ABI
format." The build runs the Gradle backend build inside the container, so the first one takes a
while; `-PbackendOnly` keeps the Android SDK out of it.

Artifact Registry's free tier is 0.5 GiB, and these JVM images are not small, so prune old digests
occasionally:

```bash
gcloud artifacts docker images list "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/backend" \
  --include-tags --sort-by=~UPDATE_TIME
gcloud artifacts docker images delete IMAGE@sha256:DIGEST --delete-tags
```

## 4. Store the secrets

Two values never belong in a plain env var. Create them once; Cloud Run reads them at start.

```bash
# JWT signing key — generate a fresh one, or paste the Oracle box's to keep sessions alive
openssl rand -base64 48 | gcloud secrets create harf-jwt-secret --data-file=-

# Telegram bot token (skip if you're not running the editor bot)
printf '%s' 'YOUR_BOT_TOKEN' | gcloud secrets create harf-telegram-token --data-file=-

# The Cloud SQL password you set in §2
printf '%s' 'YOUR_DB_PASSWORD' | gcloud secrets create harf-db-password --data-file=-
```

Grant the runtime service account read access to each (`roles/secretmanager.secretAccessor` is the
only role that works here):

```bash
export RUNTIME_SA="harf-run@$PROJECT_ID.iam.gserviceaccount.com"
gcloud iam service-accounts create harf-run --display-name="Harf Cloud Run runtime"

for s in harf-jwt-secret harf-telegram-token harf-db-password; do
  gcloud secrets add-iam-policy-binding "$s" \
    --member="serviceAccount:$RUNTIME_SA" \
    --role=roles/secretmanager.secretAccessor
done
```

## 5. Deploy the service

`DB_PRIVATE_IP` comes from §2, `IMAGE` from §3.

```bash
gcloud run deploy "$SERVICE" \
  --image="$IMAGE" \
  --region="$REGION" \
  --service-account="$RUNTIME_SA" \
  --min-instances=1 --max-instances=1 \
  --no-cpu-throttling \
  --cpu=1 --memory=1Gi --cpu-boost \
  --concurrency=80 \
  --timeout=300 \
  --network=default --subnet=default --vpc-egress=private-ranges-only \
  --allow-unauthenticated \
  --set-env-vars="HARF_ENV=production,DB_JDBC_URL=jdbc:postgresql://$DB_PRIVATE_IP:5432/harf,DB_USER=harf,DB_MAX_POOL_SIZE=5,JWT_ISSUER=harf-backend,JWT_AUDIENCE=harf-client,GOOGLE_CLIENT_IDS=$GOOGLE_CLIENT_IDS,LOG_LEVEL=INFO,JAVA_OPTS=-Xmx512m -XX:MaxMetaspaceSize=128m" \
  --set-secrets="JWT_SECRET=harf-jwt-secret:latest,DB_PASSWORD=harf-db-password:latest,TELEGRAM_BOT_TOKEN=harf-telegram-token:latest"
```

Flag by flag, because several are load-bearing:

- **`--no-cpu-throttling`** — CPU allocated outside requests. Without it the review worker, the
  Telegram poll and the report timer freeze between requests. It also switches the service to
  instance-based billing, which is what makes this cost ~$45–50/month.
- **`--min-instances=1 --max-instances=1`** — see the top of this document. Note the max is
  documented as breachable "for a brief period" under traffic spikes.
- **`--cpu-boost`** — extra vCPU during startup only. A JVM plus `seed()` and `mergeGuesses()` is a
  slow cold start; this shortens it for free.
- **`--memory=1Gi` with `-Xmx512m`** — the JVM sizes its heap off the container limit and the real
  RSS lands well above the heap, which is exactly the trap `.env.example` warns about on the 1 GB
  Oracle box. 1 GiB with a 512 MB cap leaves comfortable headroom.
- **`--timeout=300`** — the default 5 minutes. Raise it (up to 3600) only when live multiplayer
  needs long-lived WebSockets.
- **`--allow-unauthenticated`** — the game's API is public; auth is the app's own JWT layer.
- **`PORT` is absent on purpose** — Cloud Run injects it, and `Application.kt:78` already reads it
  and binds `0.0.0.0`.

### What happens during a redeploy

Cloud Run documents that in-flight requests keep going during a traffic switch and does **not**
guarantee the old revision is stopped before the new one starts, so a deploy can briefly have two
instances alive. For this backend that is survivable, and worth knowing why:

- **Telegram** — Telegram allows one poller per token, so the loser gets a conflict error. The
  supervised loop in `Application.kt:115` catches it, waits 5s and retries, so the new instance takes
  over on its own once the old one stops.
- **Daily reports** — deduplicated in the database (`reportRecorded`/`recordReport`), so an overlap
  cannot double-send.
- **The review worker** — its writes are transactional; a duplicate run wastes a little work.

If you ever want a hard gate anyway, deploy with `--no-traffic` and switch over deliberately with
`gcloud run services update-traffic "$SERVICE" --to-latest --region="$REGION"`.

## 6. Verify before you point anything at it

The deploy prints a `https://SERVICE-HASH-REGION.a.run.app` URL, already on HTTPS with a valid
certificate. That URL is the whole verification surface:

```bash
export URL=$(gcloud run services describe "$SERVICE" --region="$REGION" --format='value(status.url)')

curl -sS "$URL"                                  # -> Harf Backend is running
curl -sS "$URL/api/v1/wordpacks/en" | head -c 200 # -> the seeded pack
```

If the second call returns a pack, then the database connection, the schema migration in
`DatabaseFactory.init()` and `seed()` all worked — an empty database bootstraps itself, so there is
nothing to import unless you are migrating (§8).

Logs, when it doesn't:

```bash
gcloud run services logs read "$SERVICE" --region="$REGION" --limit=100
```

The two failures worth recognising: a **connection timeout** to the private IP means the VPC egress
flags or the peering range are wrong (and remember the documented "minute or more" delay right after
Direct VPC egress is first configured), while `Refusing destructive migration` means the image's
schema diff wants to drop a column — the same guard `docs/deploy-oracle.md` §13 describes.

## 7. Custom domain

Cloud Run has a built-in domain mapping, and it is still Preview: Google's own doc says "Due to
latency issues, they are not production-ready and are not supported at General Availability. At the
moment, this option is not recommended for production services." It is available in `europe-west1`
and `europe-west4` among others, certificates take ~15 minutes and occasionally up to 24 hours.

For a trial hostname that is fine:

```bash
gcloud beta run domain-mappings create \
  --service="$SERVICE" --domain=gcp-api.lazydevs.uz --region="$REGION"
```

It prints the DNS records to add at your registrar. For the production hostname, use a **global
external Application Load Balancer** with a serverless NEG pointing at the service — that is the
path Google documents for production, and it adds Cloud CDN, Cloud Armor and your own certificates,
at the cost of a permanently running load balancer on the bill. **Firebase Hosting rewrites** are
the third documented option and the cheapest way to get a custom domain in front of Cloud Run.

Whichever you choose, the client needs rebuilding against the new host — see Appendix B.

## 8. Migrating the Oracle data (skip for a fresh start)

Only needed if you want existing accounts, stats and accepted word suggestions to survive. On the
Oracle box:

```bash
docker exec harf-postgres pg_dump -U harf --no-owner --no-acl harf > harf.sql
```

`--no-owner --no-acl` matters: the dump must not try to recreate Oracle-side roles that don't exist
in Cloud SQL. Upload it and import:

```bash
gcloud storage buckets create "gs://$PROJECT_ID-migration" --location="$REGION"
gcloud storage cp harf.sql "gs://$PROJECT_ID-migration/"

# the Cloud SQL instance's own service account must be able to read the object
export SQL_SA=$(gcloud sql instances describe "$SQL_INSTANCE" --format='value(serviceAccountEmailAddress)')
gcloud storage buckets add-iam-policy-binding "gs://$PROJECT_ID-migration" \
  --member="serviceAccount:$SQL_SA" --role=roles/storage.objectViewer

gcloud sql import sql "$SQL_INSTANCE" "gs://$PROJECT_ID-migration/harf.sql" --database=harf
```

Import into the database **before** the first Cloud Run deploy, or stop the service first: the
backend's startup migration and `seed()` would otherwise race the import. Delete the bucket
afterwards — it holds every user record you have.

Cut over by moving DNS, and keep the Oracle stack running until the new one has served real traffic
for a day. Rollback is the DNS record going back.

## 9. Continuous deployment from GitHub Actions

The existing `publish-image` job pushes to GHCR and `deploy` SSHes into the Oracle box. The Cloud
Run equivalent authenticates with Workload Identity Federation, so no JSON key is ever stored in
GitHub.

One-time setup:

```bash
export GITHUB_ORG=abumme
export GITHUB_REPO=abumme/Harf
export CI_SA="harf-ci@$PROJECT_ID.iam.gserviceaccount.com"

gcloud iam service-accounts create harf-ci --display-name="Harf GitHub Actions"

gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions Pool"

gcloud iam workload-identity-pools providers create-oidc harf \
  --location=global --workload-identity-pool=github \
  --display-name="Harf repo provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository_owner == '$GITHUB_ORG'" \
  --issuer-uri="https://token.actions.githubusercontent.com"

export POOL_ID=$(gcloud iam workload-identity-pools describe github --location=global --format='value(name)')

# org-level filter above, repo-level filter here — together they scope access to this one repo
gcloud iam service-accounts add-iam-policy-binding "$CI_SA" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/$POOL_ID/attribute.repository/$GITHUB_REPO"

# what CI is allowed to do
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$CI_SA" --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:$CI_SA" --role=roles/run.admin
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$CI_SA" --role=roles/iam.serviceAccountUser

gcloud iam workload-identity-pools providers describe harf \
  --location=global --workload-identity-pool=github --format='value(name)'
```

The **attribute condition is not optional in practice** — without it, any GitHub repository in the
world can enter the pool. Note also that a deleted pool or provider name cannot be reused for 30
days, so don't churn these names.

Put the last command's output in the workflow as `workload_identity_provider`. The job, added to
`.github/workflows/ci.yml` alongside the existing ones:

```yaml
  deploy-cloudrun:
    name: Deploy to Cloud Run
    needs: backend-tests
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-24.04
    timeout-minutes: 30
    permissions:
      contents: read
      id-token: write          # required for Workload Identity Federation
    env:
      REGION: europe-west1
      IMAGE: europe-west1-docker.pkg.dev/harf-prod/harf/backend
    steps:
      - uses: actions/checkout@v7      # must come BEFORE auth
      - id: auth
        uses: google-github-actions/auth@v3
        with:
          project_id: harf-prod
          workload_identity_provider: projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/harf
          service_account: harf-ci@harf-prod.iam.gserviceaccount.com
      - run: gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet
      - name: Build and push
        run: |
          docker build --platform linux/amd64 -f backend/Dockerfile \
            -t "$IMAGE:${{ github.sha }}" .
          docker push "$IMAGE:${{ github.sha }}"
      - uses: google-github-actions/deploy-cloudrun@v3
        with:
          service: harf-backend
          region: ${{ env.REGION }}
          image: ${{ env.IMAGE }}:${{ github.sha }}
```

`deploy-cloudrun` keeps the service's existing configuration, so the flags from §5 survive a
redeploy; only the image changes. Checkout must precede `auth`, or later steps can't authenticate.

Rolling back is a traffic switch, not a rebuild:

```bash
gcloud run revisions list --service="$SERVICE" --region="$REGION"
gcloud run services update-traffic "$SERVICE" --region="$REGION" --to-revisions=REVISION_NAME=100
```

The same caveat as the Oracle deploy applies: a rollback cannot cross a schema change, because the
older image's startup guard refuses to drop the columns the newer one added. Roll forward instead.

## 10. Put a cost ceiling on it

Instance-based billing runs whether anyone plays or not, so set a budget alert on day one:

```bash
gcloud billing budgets create \
  --billing-account=XXXXXX-XXXXXX-XXXXXX \
  --display-name="Harf monthly" \
  --budget-amount=80USD \
  --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
```

Budgets alert; they do not cap spending. The things that actually move the bill are the Cloud SQL
tier, the always-on instance, and egress — watch `gcloud sql instances describe` and the billing
console for the first month.

---

## Later: multiplayer and scaling past one instance

Nothing in this section is needed today, and none of it is wired up in the codebase yet. It is here
so the single-instance pin above doesn't read as a dead end.

**The pin is a code constraint, not a Cloud Run one.** Before the service can ever run two
instances, three things in `Application.kt` have to move out of the request path:

1. **Telegram** — `TelegramBot.runPolling()` long-polls `getUpdates`, and Telegram serves exactly one
   poller per bot token. A second instance makes both flap. Switch to a webhook (`setWebhook` to a
   route on this service) and the constraint disappears.
2. **Daily reports** — `DailyReportScheduler.sendDue` fires from an in-process timer, so N instances
   send N copies. Move it behind an authenticated endpoint that Cloud Scheduler calls (3 jobs per
   billing account are free).
3. **The suggestion review worker** — harmless to run twice (its writes are transactional), but
   wasteful; same treatment as the reports, or leave it on a single worker service.

Only after that does `--max-instances` above 1 mean anything.

**If multiplayer is asynchronous** — challenge a friend, both play the same word, compare at the end
— nothing here changes. It is new tables, new REST routes and push notifications, which is the shape
of the sync API that already exists. One instance carries it.

**If multiplayer is live** (watching an opponent's tiles fill in real time), it needs WebSockets, and
Cloud Run supports them with no extra configuration, with four consequences worth designing around:

- Connections are capped by the request timeout: 5 minutes by default, 60 minutes maximum. Clients
  must reconnect and resume, so match state has to live on the server, not in the connection.
- Any open WebSocket keeps the instance active and billed, so scale-to-zero stops applying once
  people are actually playing.
- Session affinity is best-effort only. With two or more instances, two players in one match can
  land on different instances, which is why a shared backplane (Redis Pub/Sub via Memorystore, or
  Firestore) becomes necessary — roughly $35–40/month for the smallest managed Redis.
- With `--max-instances=1` none of that applies: match state stays in memory, and one vCPU carries a
  lot of connections for a game that sends a few messages per round.

So the order is: async multiplayer on the setup in this guide → live multiplayer still on one
instance → the three cleanups above → Redis and a raised instance cap, in that order, only when
measurements say one instance is full.

Reference: [Cloud Run WebSockets](https://docs.cloud.google.com/run/docs/triggering/websockets).

---

## Appendix A: environment variables

Cloud Run gets exactly the variables `.env` holds on the Oracle box, minus the compose-only ones.

| Variable | Where it comes from on Cloud Run | Notes |
|---|---|---|
| `HARF_ENV` | plain env var, `production` | makes the server refuse to start without `JWT_SECRET` |
| `PORT` | set by Cloud Run itself | do **not** set it; the container already reads it |
| `DB_JDBC_URL` | plain env var | `jdbc:postgresql://PRIVATE_IP:5432/harf` |
| `DB_USER` / `DB_PASSWORD` | env var / Secret Manager | password never as a plain env var |
| `DB_MAX_POOL_SIZE` | plain env var | keep small; Cloud SQL shared-core instances cap connections |
| `JWT_SECRET` | Secret Manager | rotating it invalidates every session |
| `JWT_ISSUER` / `JWT_AUDIENCE` | plain env vars | unchanged from `.env.example` |
| `GOOGLE_CLIENT_IDS` | plain env var | public client ids, same values as today |
| `APPLE_AUDIENCES` | plain env var | blank until Apple sign-in ships |
| `TELEGRAM_BOT_TOKEN` | Secret Manager | blank disables the bot entirely |
| `TELEGRAM_EDITOR_CHAT_ID` / `_IDS` / `_TOPICS` | plain env vars | unchanged |
| `WORD_LOOKUP_ENABLED` | plain env var | `false` turns off Wiktionary verification |
| `LOG_LEVEL` | plain env var | `INFO`; `DEBUG` echoes every SQL statement |
| `JAVA_OPTS` | plain env var | see the memory note in the deploy step |
| `POSTGRES_*`, `HARF_HOST_PORT`, `DB_HOST_PORT` | **not used** | compose-only, no meaning on Cloud Run |

## Appendix B: pointing the apps at the new backend

The client bakes its API base URL in at build time (`sharedUI/build.gradle.kts`), so a new backend
URL means new client builds:

```bash
./gradlew :androidApp:assembleRelease -Pharf.apiBaseUrl=https://api.example.com
```

or set `harf.apiBaseUrl` in `local.properties` for local runs. Until you cut DNS over, the Cloud Run
service also answers on its generated `https://SERVICE-HASH-REGION.a.run.app` URL, which is fine for
a test build.
