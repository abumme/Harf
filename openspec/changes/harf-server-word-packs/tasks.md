Ordered foundation → consumer; each group is an independent commit. Additive first (nothing reads the new pack until the client switch), so intermediate states stay green and offline behavior is unchanged until the last step.

## 1. Contract: WordPackDto + route

- [ ] 1.1 Add `WordPackDto(lang, version, effectiveFrom, anchorEpochDay, answers, guesses, schedule)` (`@Serializable`) to `sharedData` and a `WORDPACKS` route constant (`/api/v1/wordpacks/{lang}`) to `ApiRoutes`. Verify `:sharedData:jvmTest` compiles and a serialization round-trip test passes.

## 2. Backend: storage + seeded pack

- [ ] 2.1 Add `WordPacksTable` (Exposed) with `lang` (PK), `version`, `effectiveFrom`, `anchorEpochDay`, `answers`, `guesses`, `schedule` (JSON text), `updatedAt`; register it in `DatabaseFactory`. Verify `DatabaseSchemaTest` (or an added test) creates the table on an isolated Postgres.
- [ ] 2.2 Add a seed path that inserts version 1 per language from a maintained backend copy of the current vocab (answers/guesses + a generated no-adjacent-repeat schedule with a fixed `anchorEpochDay`). Verify a test that seeding is idempotent and produces a valid pack (answers ⊆ guesses, no adjacent schedule repeat).

## 3. Backend: serve endpoint

- [ ] 3.1 Add a public `GET /api/v1/wordpacks/{lang}` route returning `WordPackDto` with `ETag: <version>`, and `304 Not Modified` when `If-None-Match` matches the current version; `404` for an unserved language. Verify real-Ktor tests: 200 with body + ETag, 304 on matching `If-None-Match`, 404 for an unknown language.

## 4. Client: cache + fetch

- [ ] 4.1 Add a `WordPackCache` that persists the last valid fetched `WordPackDto` per language (version + payload) locally and survives restart. Verify a test that a stored pack reloads after a simulated restart.
- [ ] 4.2 Add a `WordPackSyncManager` (Ktor) that fetches per language sending `If-None-Match` with the cached version; on `200` validate then cache, on `304`/error no-op; never throws to the caller. Verify MockEngine tests: 200 caches, 304 keeps cache, network error is swallowed and leaves the cache intact.
- [ ] 4.3 Trigger the sync opportunistically on launch/foreground (alongside the existing bootstrap/foreground path). Verify it runs without blocking startup and does nothing useful offline (no crash, no error surfaced).

## 5. Client: freshest-of resolution + validation

- [ ] 5.1 Make `WordPackRepository.load(id)` resolve the active pack as the valid cached pack else the bundled pack, applying the existing integrity check before adopting a cached pack; a cached pack that fails validation is ignored in favor of the bundle. Verify tests: valid cache overrides bundle; invalid cache falls back to bundle; offline load with empty cache uses the bundle.

## 6. Client: schedule-based daily selection

- [ ] 6.1 Switch `DailyPuzzleProvider` to select the answer from the active schedule (`schedule[epochDay - anchorEpochDay]`) — cached schedule if present, else the bundled baseline schedule — keeping the per-language timezone rollover and the Uzbek single-lexeme resolution. Verify tests: same day + same version → same word; consecutive days differ; a schedule with an appended future segment leaves past-day answers unchanged (immutability).
- [ ] 6.2 Generate/ship the bundled baseline schedule per language from the bundled answers (fixed anchor, no adjacent repeat) so offline-before-first-sync is deterministic and consistent. Verify a test that the bundled baseline covers the current day and yields a valid answer offline.

## 7. Integration verification

- [ ] 7.1 End-to-end against the isolated-Postgres harness: seed → `GET` 200/304/404 → client caches → offline play uses cache → invalid pack rejected → past day immutable after an appended update. Verify all pass.
- [ ] 7.2 Compile-check JVM → Android → Wasm/JS → iOS; run `:sharedData:jvmTest`, `:sharedUI:jvmTest`, `:backend:test`. Verify green.
