# Tasks

Visual authority for every UI task: the "After" column of `docs/ui-review/before-after.html`. Radii come from the shape scale in `design.md` — do not substitute a single default radius.

## 1. Tokens & shape scale (foundation)

- [ ] 1.1 Add `danger`/`onDanger`/`success`/`onSuccess` roles to `HarfColors` and give every palette values (Newsprint: `danger=#B3282D`, `success=#2E7D46`, both `on*=#FFFFFF`); verify all 5 editions compile with the new roles.
- [ ] 1.2 Map the new roles into `HarfTheme`'s Material scheme (`error`/`onError` → `danger`/`onDanger`); verify the scheme no longer maps `error` to `present`.
- [ ] 1.3 Add a `HarfShapes` token set (`button=10.dp`, `container=12.dp`, `row=11.dp`, `pill=RoundedCornerShape(50)`, `swatch=8.dp`) and provide it in the theme; verify a composable can read it.

## 2. Shared controls

- [ ] 2.1 Add shared button composables (`PrimaryButton`, `GhostButton`, `DangerButton`) using the color + shape tokens, min height 44.dp; verify all three render with the 10.dp button radius and correct fill/border/text per `design.md`.
- [ ] 2.2 Add a shared `ScreenTopBar(title, onBack)` with a labeled, accessible back control + serif title; verify the back control has a `contentDescription` and dismisses on desktop/web.

## 3. Home

- [ ] 3.1 Rework `HomeScreen` to the mockup hierarchy: primary play button(s) under a "play today" label, secondary destinations as a ghost row; verify one primary per language and ghost secondaries.
- [ ] 3.2 Replace the blind theme-cycle button with a swatch row (all editions visible, active indicated, locked→paywall via `EntitlementGate.canApplyTheme`); verify selection applies and locked editions route to the paywall.
- [ ] 3.3 Constrain Home content to a bounded `widthIn(max=...)` centered on wide windows; verify no stranded narrow column at desktop width.

## 4. Settings

- [ ] 4.1 Group Settings into sections (Account / Play Games / Legal) as bordered `container`-radius groups; verify grouping matches the mockup.
- [ ] 4.2 Move Delete account into an isolated danger zone using `DangerButton` + the `danger` token; remove all three `Color(0xFFD32F2F)` literals; verify no hardcoded destructive color remains (grep).
- [ ] 4.3 Add the shared `ScreenTopBar` to Settings; verify back works on desktop/web.

## 5. Copy (RU primary)

- [ ] 5.1 `home_tagline`: RU `Ежедневная игра в слова`, EN `A daily word puzzle` (drop "дуэль/duel"); update all 3 locales.
- [ ] 5.2 Delete confirm button names its object: `settings_delete` RU `Удалить аккаунт` / EN `Delete account`; verify the dialog button and label read as destructive-with-object.
- [ ] 5.3 `signin_failed` / `link_failed` / `delete_failed`: drop the raw `%2$s` code from the visible string (RU e.g. `Не удалось войти через %1$s. Попробуйте ещё раз.`) and log the code instead; verify no raw provider error reaches the UI string.
- [ ] 5.4 Normalize the Uzbek apostrophe across `values-uz` (pick one tutuq belgisi) and unify `Qiyin rejim` (3 variants today); verify a single glyph and term throughout the file.

## 6. Verification

- [ ] 6.1 Compile-check JVM → Android → Wasm/JS; run `./gradlew :sharedUI:jvmTest`.
- [ ] 6.2 Re-record Home/Settings screenshot goldens from the CI `roborazzi-goldens` artifact and commit; verify `verifyRoborazziJvm` passes on CI.
- [ ] 6.3 Manual pass on a real device at narrow phone width + a wide desktop window: primary/ghost/danger radii, danger zone, swatches, top-bar back all match the "After" mockup.
