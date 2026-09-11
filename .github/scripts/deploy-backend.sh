#!/usr/bin/env bash
# Runs ON the Oracle box — the `deploy` job in .github/workflows/ci.yml pipes it over SSH.
#
# Moves the running harf-backend container onto one exact image (pulled by digest), health-checks
# it on the loopback port Caddy proxies to, and rolls back to the previous image if it never comes
# up. It only updates an existing stack; the first start is manual (docs/deploy-oracle.md §5).
#
# Usage: deploy-backend.sh <ghcr.io/<owner>/harf-backend@sha256:...>
set -euo pipefail

new_ref=${1:?usage: deploy-backend.sh <image@sha256:digest>}
container=harf-backend

if ! docker inspect "$container" >/dev/null 2>&1; then
  echo "::error::No '$container' container on this host. Start the stack by hand first (docs/deploy-oracle.md §5); CI only updates it."
  exit 1
fi

label() { docker inspect -f "{{ index .Config.Labels \"$1\" }}" "$container"; }

# Re-run compose exactly as it was last run, so the project name, .env and file paths all match
# whatever the box was set up with.
project=$(label com.docker.compose.project)
workdir=$(label com.docker.compose.project.working_dir)
service=$(label com.docker.compose.service)
IFS=, read -ra config_files <<< "$(label com.docker.compose.project.config_files)"
file_args=()
for f in "${config_files[@]}"; do file_args+=(-f "$f"); done

# The compose file names the image by tag (…:latest). Deploying means pointing that tag at the new
# image locally, so a later manual `docker compose up -d` keeps running what CI shipped.
image_name=$(docker inspect -f '{{ .Config.Image }}' "$container")
previous_id=$(docker inspect -f '{{ .Image }}' "$container")

compose() { docker compose -p "$project" --project-directory "$workdir" "${file_args[@]}" "$@"; }

layers() { docker image inspect -f '{{ json .RootFS.Layers }}' "$1"; }

run_image() {
  docker tag "$1" "$image_name"
  compose up -d --no-deps --no-build "$service"
}

healthy() {
  local addr body
  for _ in $(seq 1 30); do
    addr=$(docker port "$container" 8080/tcp 2>/dev/null | head -n1 || true)
    if [[ -n "$addr" ]] && body=$(curl -fsS --max-time 3 "http://$addr/" 2>/dev/null) \
        && [[ "$body" == *"Harf Backend is running"* ]]; then
      return 0
    fi
    sleep 3
  done
  return 1
}

echo "Pulling $new_ref"
docker pull --quiet "$new_ref" >/dev/null

# Same layers means same code; only build metadata (created time, revision label) differs.
# Restarting would just drop in-flight requests for nothing.
if [[ "$(layers "$new_ref")" == "$(layers "$previous_id")" ]]; then
  echo "Running image already has these layers; nothing to deploy."
  exit 0
fi

echo "Switching $container: $previous_id -> $new_ref"
run_image "$new_ref"

if healthy; then
  docker image prune -f >/dev/null
  echo "Deployed $new_ref"
  exit 0
fi

echo "::error::New backend never passed its health check; rolling back to $previous_id"
docker logs --tail 80 "$container" 2>&1 || true
run_image "$previous_id"
if healthy; then
  echo "::warning::Rolled back. Production is on the previous image."
else
  echo "::error::Rollback is unhealthy too. Check the box by hand."
fi
exit 1
