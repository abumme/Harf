# Tasks

## 1. Puzzle and round foundations

- [x] 1.1 Record each language's first public puzzle day in the backend calendar (source of truth, with the bundled calendar snapshot as offline fallback) and add a range-checked historical puzzle lookup; verify tests reject pre-publication, today, future, and out-of-range days in each language timezone.
- [x] 1.2 Extend published schedules without changing historical answers or modulo-wrapping archive requests; verify an old day resolves identically across pack versions and after the initial 800-day horizon.
- [x] 1.3 Add explicit official/archive round context and normal/hard mode to shared round models with backward-compatible serialized defaults; verify old daily snapshots and results still deserialize as normal official rounds.

## 2. Hard mode

- [x] 2.1 Implement a pure accumulated-clue validator over graphemes; verify tests for fixed greens, displaced yellows, repeated minimum counts, gray duplicates, and clues from multiple rows.
- [x] 2.2 Validate hard-mode guesses before scoring and emit localized rule violations without spending attempts; verify a rejected word leaves the board and attempt count unchanged while a valid word scores normally.
- [x] 2.3 Persist mode selection before a round starts and block mode changes after its first accepted guess; verify restart, normal-to-hard purchase timing, and the single official result per language/day.
- [x] 2.4 Preserve hard-mode rows and constraints when switching Uzbek Latin/Cyrillic; verify both scripts represent the same lexeme and switching cannot bypass a revealed clue.

## 3. Archive play and history

- [x] 3.1 Add the Founder-gated archive route and language/day browser through yesterday; verify a non-owner sees a clear unlock path and an owner can select every published past day without exposing today.
- [x] 3.2 Add a local archive round store keyed by owner, language, day, and run id; verify two archived days and today's round restore independently after relaunch.
- [x] 3.3 Route archive completion into separate replayable run history, not `ResultLog` or daily sync/Play Games; verify repeated archived completions leave official stats, streaks, and achievements unchanged.
- [x] 3.4 Add account-owned archive-run storage and authenticated list/upload endpoints with idempotent run ids and deletion cascade; verify duplicate uploads create one run and cross-account reads/writes are rejected.
- [x] 3.5 Queue completed archive runs offline and synchronize history by run id after sign-in/reconnect; verify an offline run appears once on a second device and unfinished rounds remain local.
- [x] 3.6 Add archive history and replay actions with a new run id per replay; verify earlier completed playthroughs remain visible after another replay.
- [x] 3.7 Mark archive date and hard mode in shared result headers; verify ordinary daily shares retain their existing grid/header behavior and archive shares cannot be mistaken for today's result.

## 4. Account ownership and sign-in

- [x] 4.1 Add an authenticated backend entitlement read backed by verified RevenueCat customer state and purchase-event refresh; verify a forged client ownership flag grants nothing and refunds/confirmed changes update access.
- [x] 4.2 Require sign-in before purchase and bind mobile RevenueCat identity to the Harf account from the start (no anonymous purchase state); handle restore, logout, and account switch; verify ownership stays with the correct account without a second charge.
- [x] 4.3 Distinguish verified empty ownership from refresh failure and scope cached entitlements by owner; verify offline access persists for the same owner while another account cannot inherit the cache.
- [x] 4.4 Complete Google sign-in on iOS and JVM desktop using supported provider flows; verify a Google-linked account resolves to the same backend user id on Android, iOS, and desktop.
- [x] 4.5 Add Google sign-in to web using its supported browser flow; verify it reaches the existing linked account and the backend rejects invalid audience/state/nonce data.
- [ ] 4.6 (Deferred — later phase) Add Apple browser sign-in to Android and JVM desktop with server-validated callback handling; verify an Apple-linked iOS account signs into both clients and a cancelled callback leaves the session unchanged. Gated on Apple Services ID and store configuration; this release ships Apple native on iOS only.
- [ ] 4.7 (Deferred — later phase) Add Apple sign-in to web with the configured Services ID and return URL; verify it reaches the same backend account as iOS and rejects invalid callback data.
- [x] 4.8 Expose safe linking of a second provider and isolate account-owned archive/purchase state on sign-out, adoption, switch, and deletion; verify conflicting established accounts are not merged and a returning owner recovers access.

## 5. Founder surfaces and offer copy

- [x] 5.1 Connect archive and hard-mode entry points to the lifetime gate and show the Founder badge from ownership rather than store availability; verify free daily play works without a purchase or store connection.
- [x] 5.2 Provide desktop/web Founder sign-in and mobile-purchase guidance without a dead checkout action; verify an existing owner can unlock by signing in and a non-owner sees an accurate path.
- [x] 5.3 Update app string resources, hard-mode messages, and result labels in supported locales; verify resource keys/format arguments match and each rendered rule error names the violated clue.
- [x] 5.4 Build a custom in-app Compose Founder paywall and align localized legal offers and store descriptions to match archive, hard mode, badge, and separate themes; verify screenshots or review captures of each purchase surface show the same delivered terms.
- [x] 5.5 Configure the Founder store products as non-consumable one-time purchases mapped to `harf_founder`; verify sandbox purchase, reinstall, and restore on each mobile store without a second charge.

## 6. Integration and release gates

- [x] 6.1 Exercise the same linked account on Android, iOS, JVM desktop, and web; verify Founder access and completed archive history follow that account via Google on all four platforms and Apple native on iOS.
- [x] 6.2 Exercise sign-in-before-purchase, provider conflict on linking, account switch, logout, account deletion, offline refresh, and confirmed entitlement removal; verify no ownership or archive history leaks between accounts.
- [x] 6.3 Verify daily and archive play across language midnights, schedule updates, repeated graphemes, Uzbek scripts, replay, and relaunch; confirm official results/streaks/Play Games are unaffected by archive runs.
- [x] 6.4 Run IDEA MCP inspections on changed symbols and compile in project order: JVM, Android, Wasm/JS, then iOS where a macOS runner is available; record the results and any platform limitation before enabling Founder sales.
