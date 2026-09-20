# Design

## Context

See `proposal.md` for motivation and `specs/` for behavioral requirements. `DailyPuzzleProvider` currently selects only today's puzzle from a dated schedule; the initial schedule is 800 days long and `answerFor` wraps outside it. `GameViewModel` checks word length and dictionary membership before scoring; `InProgressRound` and `ResultLog` represent one official daily round per language/day. The game's finish callback writes that log, pushes stats, and submits Play Games progress. Archive play cannot reuse that callback or store.

The lifetime entitlement id is `harf_founder`. The mobile purchase controller uses RevenueCat with an anonymous customer by default; desktop and web have no purchase implementation. `EntitlementRepository` seeds from one global cached value and currently treats a failed RevenueCat customer-info read as empty ownership. The mobile paywall is hosted by RevenueCat, so local XML text changes alone cannot update the actual offer. Harf accounts and provider identities exist in the backend, but Google and Apple sign-in are not yet operational on every client.

## Goals / Non-Goals

**Goals:**
- Keep official daily and archive state separate at the type, storage, callback, and API boundaries.
- Use one verified Harf account identity to resolve purchased access across platforms without trusting client-supplied purchase flags.
- Preserve previously verified access through transient failures while preventing one account from inheriting another account's cache.
- Make old serialized daily rounds and result records readable with a normal-mode default.

**Non-Goals:**
- No web or desktop checkout in this change; these clients unlock purchases made on mobile after sign-in.
- No synchronization of unfinished archive rounds across devices; completed runs and history synchronize. An unfinished run still restores locally.
- No six-tile or multi-word Saturday bonus mode. Its exact format remains a separate future decision.
- No email/password or email-link registration. Both chosen providers have browser-based access paths on the target platforms.

## Decisions

### 1. Separate official daily and archive pipelines

Introduce an explicit round context (`OfficialDaily` or `Archive(day, runId)`) and mode (`Normal` or `Hard`) in the shared game layer. Route selection, persistence, finish handling, and result sharing all use this context. The official daily path keeps the existing one-record-per-language/day invariant and is the only path that writes `ResultLog`, pushes ordinary stats, or submits Play Games progress. Archive finish appends an archive run with a stable client-generated `runId` instead. Replay creates another run id, preserving prior history. Locally persisted unfinished archive runs are keyed by account/anonymous owner, language, day, and run id so they cannot overwrite today's round or another archive run. Completed runs include outcome, attempt count, mode, and played rows needed for history and sharing.

The backend stores completed archive runs in an account-owned table with a unique `(userId, runId)` constraint and authenticated list/upload endpoints. Repeated uploads are idempotent. The client queues offline completions durably, uploads on reconnect/foreground, then merges by run id. On account adoption, download the destination account's history first and move only runs belonging to the verified caller, without combining two established accounts. Account deletion cascades to archive data. This is separate from the last-write-wins stats snapshot because replay history is append-only.

Alternative rejected: reuse `ResultLog` or stats sync with a mode flag. Those stores intentionally count one official result per day; mixing replay runs would create ambiguous streaks and duplicate achievements.

### 2. Use an explicit historical-day selector and published boundary

Add a puzzle lookup by `(languageId, epochDay)` that uses the same timezone, Uzbek paired-lexeme mapping, and schedule as daily play. The archive UI limits selection to `[firstPublishedDay(language), today(language) - 1]`; first publication is release metadata, not the 2026-01-01 schedule anchor. The published schedule's historical prefix must be immutable and extendable beyond the current 800-day horizon. A client with a cached or bundled schedule can play known days offline; if an older or later published day is not present locally, it requests the historical schedule when online and shows a retry state while unavailable. It must never wrap to another day's word for an archive request.

Alternative rejected: pass a synthetic `Instant` to `daily()` and rely on modulo wrapping. That hides the publication boundary and can silently show the wrong historical answer when the schedule range changes.

### 3. Validate hard mode as a pure grapheme constraint

Derive constraints from **all** scored rows before a new complete dictionary word is scored: fixed correct positions; forbidden former positions for each present grapheme; and a minimum occurrence count per grapheme equal to the strongest confirmed count from any row. Do not globally ban absent graphemes because an extra copy can be absent while another copy is present/correct. Use the existing grapheme tokenizer and scorer so Uzbek digraphs/letters and duplicates are treated consistently. Return a typed violation (`fixed position`, `missing grapheme/count`, `known wrong position`) for localized feedback. Invalid guesses keep the input and do not consume a turn.

The mode is chosen before the first accepted guess and persisted with the round; existing saved rounds default to normal. Today's hard-mode result remains the one official result and may carry a mode marker for display/share; it cannot be restarted as another official attempt after a normal result. Archive replays may independently choose a mode. Uzbek script switching must map the same lexeme and accumulated constraints across scripts rather than creating a fresh official attempt.

Alternative rejected: fewer guesses, gray-letter bans, or a different answer. They either change daily comparability or make duplicate feedback misleading.

### 4. Verify ownership against one account across platforms

Use the Harf account's opaque user id as the identified RevenueCat App User ID on mobile after sign-in/link. Keep anonymous purchase support, then associate or restore that purchase when the player links an account; explicitly handle RevenueCat alias/transfer outcomes and report conflicts instead of silently moving ownership between established accounts. A backend entitlement endpoint reads purchase-provider-verified state for the authenticated account and exposes only the entitlement ids needed by clients. Mobile refreshes after purchase/restore and account transition; desktop/web use the authenticated endpoint. Backend verification must use server credentials, never a client-provided `lifetime=true` flag. Webhook invalidation and an on-demand verification path keep the account view current after a purchase, restore, or refund.

Change purchase reads to distinguish **verified empty** from **temporary failure**. Cache the last verified entitlement state per account (and separately for an unlinked mobile purchase identity); failed refreshes preserve that account's cache, while confirmed removal updates it. Switch/logout/delete clears the active view before loading the new identity. The badge reads ownership, not `isAvailable`. The existing no-op purchase controller remains appropriate on desktop/web; purchase capability and ownership access are separate.

[RevenueCat's identity guidance](https://www.revenuecat.com/docs/customers/identifying-customers) documents cross-platform access with the same App User ID and the nontrivial anonymous-to-identified alias rules. Its [restore guidance](https://www.revenuecat.com/docs/getting-started/restoring-purchases) motivates verifying that Founder is configured as non-consumable before sale.

Alternative rejected: copy a mobile entitlement boolean to Harf's backend or global settings. It could be forged or shown to the wrong signed-in account.

### 5. Use portable provider login, with provider prerequisites as release gates

Preserve the existing backend's verified-provider identity model and allow Google and Apple identities to attach to one Harf account. Use supported native sign-in where already available; use browser-based provider flows for web/desktop and for a provider without a native flow on a mobile platform. Bind every flow to a server-checked state and nonce, verify issuer/audience/signature or exchange a one-time authorization code server-side as appropriate, and never put provider client secrets in the app. Register all relevant client audiences and return URLs. Avoid automatically merging two separately linked accounts with conflicting identities or purchases.

Google supports [web sign-in](https://developers.google.com/identity/gsi/web/guides/offerings) and [desktop OAuth with PKCE](https://developers.google.com/identity/protocols/oauth2/native-app). Apple supports [browser access on other platforms](https://developer.apple.com/sign-in-with-apple/usage-guidelines-for-websites-and-other-platforms/); its [web configuration](https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web) requires a Services ID associated with an enabled Apple app. Apple browser sign-in therefore depends on completing that store configuration before promising it to owners. If a provider is not configured, show a clear unavailable state without blocking free daily play. Email registration is unnecessary for this design.

Alternative rejected: add a new email identity system before testing the existing providers' supported browser flows. It would add credential, verification, recovery, and abuse handling without a demonstrated access gap.

### 6. Keep purchase copy in its actual surface

Update the RevenueCat hosted paywall and its supported localizations along with in-app `strings.xml`, legal offers, and store listings. On desktop/web, show an account sign-in action and a truthful mobile purchase path, without a dead purchase button. In the game, locked archive/hard-mode entry points may open an optional paywall; the daily round remains reachable. Archive share headers identify the historical date and mode, so replay shares cannot be mistaken for today's official result. Use localized explanations for hard-mode violations, including counts and positions.

Alternative rejected: change only `paywall_founder_subtitle`; the mobile hosted paywall bypasses that Compose text.

## Risks / Trade-offs

- **Historical schedule wrapping or a changed past answer** → Make published-day lookup range-checked and historical schedule prefixes immutable; test across pack versions and beyond the current horizon.
- **Account or purchase aliasing grants the wrong person access** → Verify customer state server-side, test anonymous-to-linked and conflicting-account cases, scope cache/history by account, and check RevenueCat restore behavior before release.
- **Apple browser login cannot be configured before its App Store prerequisite** → Treat provider credentials, Services ID, callbacks, and a real cross-device sign-in as release gates for the platforms where Apple login is promised.
- **Offline ownership can outlive a refund until verification succeeds** → Preserve access during transient failures as the existing entitlement spec requires; refresh on foreground and immediately after account or purchase events, and revoke on confirmed negative state.
- **Replay history grows without bound** → Page history by language/day and use idempotent run ids; keep ordinary stats queries limited to the official result log.
- **Hosted paywall localization differs from app strings** → Review the actual rendered mobile offer in every supported locale and define a fallback or custom mobile screen where the hosted surface cannot display required copy.

## Migration Plan

1. Add backend archive and entitlement APIs and additive schema first; leave existing daily endpoints and records intact.
2. Add account-scoped caches and backward-compatible serialized mode defaults; migrate the current verified mobile cache only when its owner identity is known, otherwise require fresh verification.
3. Add the shared game contexts, historical selector, archive UI, hard-mode validator, and separate archive sync without enabling Founder sales.
4. Configure provider clients/callbacks, RevenueCat account identity, the non-consumable product, and hosted/localized offer. Exercise purchase, restore, account switch, offline, and four-platform sign-in flows in test environments.
5. Enable the Founder offer only after all three extras, account access, and public copy have been verified together. If rollout must stop, hide the sale/entry points while preserving purchased entitlements, daily gameplay, and stored archive history for recovery.
