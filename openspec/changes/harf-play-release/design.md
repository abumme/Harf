## Context

See proposal.md — Why, and `docs/03-store-logistics.md` for the full gate analysis (Play closed-testing clock, banking, target-API). Windows dev machine; iOS only via cloud CI. Play is the launch target; App Store parity is a later track. Depends on the rest of the MVP being implemented.

## Goals / Non-Goals

**Goals:**
- A signed, compliant, CI-produced release AAB.
- The process gates captured so none is discovered late.
- Manual/staged release so approved ≠ live prematurely.

**Non-Goals:**
- App Store submission (keep iOS green only).
- Backend/leagues/gifting; ASO experiments.

## Decisions

**Signing: Play App Signing + upload key from secrets.**
Generate an upload keystore; enroll in Play App Signing. Signing config reads keystore path/passwords from environment/secrets in CI, never committed. Alternative: manage the app signing key ourselves — rejected (Play App Signing is the recommended, recoverable path).

**Release build: R8 minify + resource shrink.**
Enable minify + shrinkResources for the release build type; add keep rules only if a runtime failure surfaces (Compose/serialization/Room/RevenueCat generally need none or minimal). Alternative: ship unminified — rejected (size + norms).

**Versioning: versionCode from CI run number (or date), versionName semantic.**
Monotonic versionCode derived in CI; versionName human-readable. Avoids manual bump mistakes.

**Target API: verify against the live Play floor at submission.**
The new-app target-API floor ratchets annually; treat the exact number as a verify-now step rather than hard-coding a possibly-stale value. Bump `targetSdk`/compile as needed and re-test.

**CI lane: GitHub Actions for Android release; keep the iOS build green (Actions macOS or Codemagic).**
Android release AAB built + signed in CI as an artifact. iOS parity is later, but keep its build green now so the App Store track isn't a cold start.

**Process gates are tasks, not specs.**
Identity verification, closed testing (12×14), banking/Paid-Apps, listing copy, Data Safety, content rating, staged release, and the submit-by/go-live sequence are operational — captured as tasks with verification, not as behavior specs.

## Risks / Trade-offs

- **Closed-testing clock (12 testers × 14 continuous days) can dominate the timeline.** → Start it as early as a stub allows; recruit extra testers for dropout margin; the clock runs in parallel with development.
- **IAP silently fails in review until banking clears.** → Complete Paid Apps/banking early; attach IAP metadata to the first submission; test in sandbox.
- **R8 strips something needed (RevenueCat/serialization/Room).** → Test the minified release build on-device before submission; add targeted keep rules only where a failure is proven.
- **Target-API floor changed since scaffold.** → Verify against current Play docs before building the submission.

## Migration Plan

Additive build/config + external process. Rollback: disable the release job / revert build config; no runtime data involved. Go live via manual/staged release only after approval, before the deadline.

## Open Questions

- Whether iOS App Store submission joins this change or stays a separate later track — default: separate; this change keeps the iOS build green but does not submit.
