## 1. Release build config

- [ ] 1.1 Set final `applicationId` `uz.abumme.harfgame` and CI-driven versionCode + semantic versionName in `androidApp/build.gradle.kts`; verify the values appear in a built AAB.
- [ ] 1.2 Add a release build type with R8 minify + resource shrinking; verify a release AAB builds and launches on-device without R8-stripped crashes (add keep rules only if a failure appears).
- [ ] 1.3 Verify `targetSdk`/compile level against the current Play new-app floor and bump if needed; verify the release targets the required level.

## 2. Signing

- [ ] 2.1 Create an upload keystore and enroll Play App Signing; store credentials as CI secrets (nothing committed); verify no secret is in version control.
- [ ] 2.2 Wire the release signing config to read from secrets; verify CI produces a signed release AAB.

## 3. CI

- [ ] 3.1 Add a GitHub Actions job that builds + signs the release AAB and uploads it as an artifact; verify a green run outputs the signed AAB.
- [ ] 3.2 Keep the iOS build green in CI (Actions macOS or Codemagic); verify an iOS compile/build job passes.

## 4. Store products & privacy (store-side)

- [ ] 4.1 Create `lifetime` and `theme_*` IAP products in Play Console and link the Play app to RevenueCat; verify a sandbox purchase resolves (with `harf-monetization`).
- [ ] 4.3 Set regional price tiers (lifetime ~$1.49-equivalent / ~15–18k UZS, themes ~$0.99-equivalent) for UZ/KZ/RU, not the auto-converted $4.99 default; verify each market shows the local "bus-fare" price.
- [ ] 4.2 Host a privacy policy URL and complete the Data Safety form and content rating; verify listing accepts them.

## 5. Listing (store-side)

- [ ] 5.1 Prepare localized listings (uz-Latn, ru, kk, en): title/subtitle/description with local keywords; verify each locale saved.
- [ ] 5.2 Produce screenshots (board per language, share card, result) and upload; verify listing preview.

## 6. Process gates (store-side)

- [ ] 6.1 Complete developer identity verification and Paid Apps agreement + banking/tax; verify account is payout-ready.
- [ ] 6.2 Run closed testing with ≥12 testers for 14 continuous days (recruit 15–18 for margin); verify the testing requirement is satisfied in Console.
- [ ] 6.3 Apply for production access and use manual/staged release; verify the build can be held approved-but-not-live, then go live before the deadline.

## 7. Pre-submit verification

- [ ] 7.1 Install the signed release AAB on a clean device and run a full offline round + a sandbox purchase; verify no release-only (R8/signing/IAP) regressions before submission.
