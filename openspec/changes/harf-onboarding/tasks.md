## 1. Shared legend

- [ ] 1.1 Add a `MarkLegend` composable that renders correct/present/absent via `LocalMarkStyle` with labels (on the spot / in the word / not in the word); verify a preview shows all three shape-distinct in grayscale.

## 2. First-run intro

- [ ] 2.1 Add an `onboarded` flag to `AppSettings` (KSafe), default false; verify a test: false by default, true after set, persists across relaunch.
- [ ] 2.2 Build the intro overlay (goal text + `MarkLegend`, Skip/Start actions); verify a preview renders it.
- [ ] 2.3 Gate it in `App()`: show intro while `!onboarded`, else the NavHost; dismiss/skip sets the flag; verify (UI/logic test) it shows first launch and not after the flag is set.

## 3. In-game help

- [ ] 3.1 Add the compact `MarkLegend` under the board on the Game screen; verify it renders and tracks the active mark style.
- [ ] 3.2 Add a "?" help affordance in the Game top bar that opens the intro content as a dismissible sheet and returns to the round in progress; verify open/dismiss.

## 4. Content

- [ ] 4.1 Add intro/legend strings to resources for the launch locales (uz-Latn, uz-Cyrl, ru, en, kk); verify they resolve.

## 5. Integration

- [ ] 5.1 First-launch walkthrough: intro shows once → skip/continue → Home → Game shows legend + working "?"; relaunch shows no intro. Verify (as far as the headless harness allows) via a Home/settings-flag test + Roborazzi golden of the intro and the in-game legend.
