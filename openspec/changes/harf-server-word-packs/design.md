## Context

See `proposal.md - Why`. Current state:
- Vocab is bundled Compose resources loaded by `WordPackRepository` (`files/<id>_answers.txt`, `files/<id>_guess.txt`); Uzbek daily answers come from `UzbekDailyWords.kt`.
- `DailyPuzzleProvider` picks `answers[epochDay mod size]`, so growing the list reshuffles which word falls on which day.
- Backend already has Postgres + Exposed + Ktor; the client already has a Ktor sync stack and KSafe-backed stores.

Constraints: the daily round must keep working with zero network (offline-first is the product's defining feature), and a day a player already saw must never change under them.

## Goals / Non-Goals

**Goals:** update/grow vocabulary without an app release; keep offline play; keep the daily word deterministic, non-repeating, immutable for past/current days, and consistent across devices on the same version.

**Non-Goals (design-level):** hiding answers from the device (impossible with offline-first — answers must be local to play offline); a vocabulary-editing UI; changing scoring/tokenization/rollover.

## Decisions

**1. Explicit append-only date→word schedule (not `day mod size`).** A pack carries `anchorEpochDay` and an ordered `schedule: List<answer>`; the answer for a day is `schedule[epochDay - anchorEpochDay]`. Updates may only **append** future entries and never reindex existing ones, so `schedule[i]` is immutable once published — past/current days are frozen. *Alternative rejected:* keep `day mod size` — a changed size reshuffles history, breaking immutability and cross-device agreement.

**2. Bundled baseline is a real version.** The app ships a bundled pack + a bundled schedule (generated at build from the bundled answers, same anchor). All devices on that app version agree offline before any sync; the server only extends the horizon. This is what lets offline-first and OTA-updates coexist — an unsynced device plays a valid shared baseline; a synced device gets more/newer future days. Divergence is only possible for **future** dates the server changed, which the future effective-date rule bounds.

**3. One current row per language, versioned, served by ETag.** `WordPacksTable(lang PK, version, effectiveFrom, anchorEpochDay, answers, guesses, schedule, updatedAt)` (JSON text columns). `GET /api/v1/wordpacks/{lang}` returns `WordPackDto` with `ETag: <version>`; `If-None-Match: <version>` → `304`. Public, no auth (vocab is not a secret; it must reach the device anyway). *Alternative rejected:* static files — DB fits the existing stack and lets a maintainer update a row.

**4. Freshest-of resolution with validation gate.** `WordPackRepository.load(id)` resolves active = valid cached pack, else bundled. A fetched pack is adopted only after the existing integrity check (tokenizes, supported length, answers ⊆ guesses) passes; otherwise the last good pack stays. *Alternative rejected:* trust-and-swap — one bad server row would break play.

**5. Offline-first sync, opportunistic.** A `WordPackSyncManager` fetches per language on launch/foreground when online, sending `If-None-Match` with the cached version; `200` → validate + cache, `304`/error → no-op. It never blocks the game — the repo reads whatever is currently resolved. Mirrors the existing `SyncManager.retryPending` trigger pattern.

**6. Client cache in a swappable local store.** Cache the fetched `WordPackDto` per language (version + payload). KSafe for parity with other stores; large guess lists (en ~535 today, larger later) may move to file storage behind the same interface. `ponytail:` KSafe first; swap to file cache only if payload size measurably hurts.

**7. Seeding.** The server can't read client Compose resources, so version 1 is seeded from a maintained copy of the current vocab (a backend resource / seed input). The schedule generator (a maintainer tool that shuffles answers with no adjacent repeat and appends future days) is out of this change's runtime scope — the change consumes a schedule, it doesn't build the editor.

## Risks / Trade-offs

- **Payload size over the wire** → ETag/`304` + only-when-stale fetch keep it cheap; enable gzip on the endpoint. Guess lists can grow; revisit chunking only if a pack gets large.
- **Unsynced vs synced divergence on future dates** → accepted and bounded by the future-effective-date rule + the bundled baseline; the spec commits only to same-version agreement, which is the honest guarantee offline-first allows.
- **Switching selection changes which word shows on a given day vs the old `day mod size` build** → acceptable pre-launch; the bundled baseline schedule defines the new canonical sequence from launch.
- **Uzbek** → schedule entries are lexemes resolvable in both scripts (as `UzbekDailyWords` already is); the bundled Uzbek schedule stays the baseline, server extends it.

## Migration Plan

- Additive and backward-compatible: add the table + endpoint + `WordPackDto` first (nothing consumes it), seed version 1, then switch the client to freshest-of + schedule selection with the bundled baseline as fallback. A client with no network behaves exactly as today (bundled), so rollout is safe.
- Rollback: the client always has the bundled pack; disabling the sync fetch reverts to bundled-only with no data loss.

## Open Questions

- Cache medium: KSafe vs a plain file for the cached pack — decide during the client-cache task based on the largest real pack size; does not affect the specs or the endpoint contract.
