## Why

Today the vocabulary is bundled in the app ([`word-packs`](../../specs/word-packs/spec.md)) and the daily word is `day mod packSize` ([`daily-puzzle`](../../specs/daily-puzzle/spec.md)). That means: adding or fixing words needs a full app release, and the current sets are tiny (uz: 6 answers → repeats every 6 days). We want to update and grow vocabulary **over the air, without an app release**, while keeping Harf's defining feature — the daily round plays fully offline with no network call.

## What Changes

- Add a **server-served, versioned word pack per language** (answers + guess dictionary + a date→word schedule), stored in Postgres and exposed over a public, cacheable read endpoint.
- The client **caches** the fetched pack locally and treats vocabulary as *freshest-of*: a valid cached server pack overrides the **bundled** pack, which remains the offline fallback. Gameplay never blocks on the network — offline uses the cache, or the bundle if nothing is cached yet.
- Replace the `day mod size` selection with a **date→word schedule**. The daily answer for a date comes from the active schedule version; **past and current days are immutable** — a vocabulary update only ever changes the mapping for dates on/after a future effective date, so a day a player already saw never changes when they later sync. All devices on the same schedule version agree; the bundled baseline schedule is itself a valid shared version, so offline-before-first-sync devices still agree with each other.
- Fetch updates opportunistically (on launch/foreground when online and the cached version is stale) using a version/ETag check; apply a fetched pack only after it passes integrity validation (tokenizes, answers ⊆ guesses), else keep the previous pack.

Non-goals: anti-cheat by hiding answers (offline-first requires answers on the device, so this is out of reach and not a goal); an admin UI for editing vocabulary (packs are seeded/updated by a maintainer process — the editing tool is separate); changing scoring, tokenization, or the local-midnight rollover.

## Capabilities

### New Capabilities
- `word-pack-distribution`: the backend serves a versioned word pack + date→word schedule per language over a public cacheable endpoint, and the client fetches, validates, and caches updates offline-first without blocking gameplay.

### Modified Capabilities
- `word-packs`: the active vocabulary is the freshest valid pack — a locally-cached server pack when present, otherwise the bundled pack — both loadable offline; a fetched pack that fails integrity validation is rejected in favor of the last good one.
- `daily-puzzle`: the daily answer is selected from an active date→word schedule (cached server schedule, else bundled baseline) rather than `day mod size`; the answer for any past or current day is immutable across vocabulary updates, and devices sharing a schedule version agree.

## Impact

- Backend: new `WordPacksTable` (Exposed) storing `lang, version, effectiveFrom, answers, guesses, schedule, updatedAt`; a public `GET /api/v1/wordpacks/{lang}` route (ETag/`If-None-Match`, no auth) in `sharedData` `ApiRoutes`; a seed path to load the current bundled files as version 1.
- Contracts (`sharedData`): `WordPackDto` (version, effectiveFrom, answers, guesses, schedule) + route constant.
- Client (`sharedUI`): `WordPackRepository` gains a cached-pack source with bundled fallback and integrity validation; a `WordPackSyncManager` (Ktor) fetching by version/ETag on launch/foreground; local cache (KSafe/files); `DailyPuzzleProvider` selects from the schedule with a deterministic bundled fallback.
- Tests: schedule immutability across a version bump; offline fallback to cache then bundle; rejecting an invalid fetched pack; endpoint 200/304 by ETag; same-version devices agree.
