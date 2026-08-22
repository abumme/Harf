## Why

Being *live* in the Play Store — not merely "in review" — is the finish line for the MVP. Several store gates have long clocks that only start when we act (see `docs/03-store-logistics.md`): the closed-testing requirement, banking/IAP clearance, and target-API compliance. This change makes the app produce a compliant, signed release and captures the store-side process so the launch isn't blocked by a surprise at the end.

## What Changes

- Configure a **release-buildable app**: final `applicationId` (`uz.abumme.harfgame`), semantic `versionCode`/`versionName`, release build type, and R8/minify + resource shrinking for the Android release.
- Ensure **target-API compliance**: Android `targetSdk`/compile level meets the current Play new-app floor; verify before submission.
- Set up **release signing**: an upload keystore / Play App Signing, with signing config sourced from secrets (not committed); verify a signed AAB is produced.
- Prepare **store metadata**: localized listing (uz-Latn, ru, kk, en) — title/subtitle/description with local keywords, screenshots (board per language, share card, result), privacy policy URL, Data Safety form, content rating.
- Wire **RevenueCat store-side products**: create `lifetime` and `theme_*` in Play Console + link the Play app to RevenueCat; ensure IAP metadata is attached to the submission (from `harf-monetization`).
- Capture the **process gates** as tracked steps: identity verification, closed testing (12 testers × 14 continuous days), Paid Apps/banking/tax, staged/manual release, and the submit-by/go-live sequence.
- Provide **CI to produce the release AAB** (and keep the iOS build green via cloud CI for later App Store parity).

Non-goals: the App Store submission itself (iOS parity is a later track; keep the iOS build green but Play is the launch target); backend/leagues/gifting; ASO experimentation.

## Capabilities

### New Capabilities
- `release-build`: the app produces a signed, minified Android release AAB with the correct application id, versioning, and target-API level, suitable for Play submission.

### Modified Capabilities
<!-- none — store-side process steps are captured in tasks, not as behavior specs -->

## Impact

- `androidApp/build.gradle.kts`: release build type (minify + shrink), signing config from secrets, `applicationId`, versioning; `targetSdk`/compile level bump if needed.
- `gradle.properties` / CI secrets: keystore + RevenueCat keys (not committed).
- CI workflow: build + sign the release AAB; keep iOS build green.
- Store-side (no repo change): Play Console listing, Data Safety, content rating, IAP products, closed testing, banking; privacy policy hosted.
- Depends on `harf-gameplay` (a shippable game), `harf-monetization` (IAP products), and the rest of the MVP set being implemented.
