#!/usr/bin/env bash
# Runs ON the Oracle box — the `deploy-web` job in .github/workflows/ci.yml uploads the web app archive
# and pipes this script over SSH.
#
# Unpacks the static web app (webApp's composeCompatibilityBrowserDistribution) into a new release
# directory and atomically repoints the `current` symlink Caddy serves at it, keeping the last few
# releases for a manual rollback. The web root and the Caddy block are one-time manual setup
# (docs/deploy-oracle.md §15); this script only publishes into them.
#
# Usage: deploy-web.sh <uploaded archive.tgz> <revision>
set -euo pipefail

archive=${1:?usage: deploy-web.sh <archive.tgz> <revision>}
revision=${2:?usage: deploy-web.sh <archive.tgz> <revision>}
root=/srv/harf-web
keep=5

trap 'rm -f "$archive"' EXIT

if [[ ! -d "$root" || ! -w "$root" ]]; then
  echo "::error::$root is missing or not writable by $(id -un). Create it once (docs/deploy-oracle.md §15)."
  exit 1
fi

release="$root/releases/$(date -u +%Y%m%d-%H%M%S)-$revision"
mkdir -p "$release"
tar -xzf "$archive" -C "$release"

if [[ ! -f "$release/index.html" || ! -f "$release/webApp.js" ]]; then
  echo "::error::The archive has no index.html/webApp.js at its root; keeping the current release."
  rm -rf "$release"
  exit 1
fi

# rename(2) over the old link is atomic: Caddy never sees a missing `current`.
ln -sfn "$release" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
echo "Published $release"

# Release names sort by time; the newest is `current`, so it is never among the pruned.
mapfile -t old < <(find "$root/releases" -mindepth 1 -maxdepth 1 -type d | sort | head -n -"$keep")
if (( ${#old[@]} )); then
  rm -rf -- "${old[@]}"
  echo "Pruned ${#old[@]} old release(s)"
fi
