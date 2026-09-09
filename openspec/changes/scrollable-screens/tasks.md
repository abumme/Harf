## 1. Top-aligned screens

- [x] 1.1 Add `Modifier.verticalScroll(rememberScrollState())` to the root `Column` of `SettingsScreen` (after `fillMaxSize()` + inset padding); verify the Delete-account button is reachable on a short viewport.
- [x] 1.2 Same for `StatsScreen` root `Column`.
- [x] 1.3 Same for `PaywallScreen` root `Column`.

## 2. Home (center-when-fits)

- [x] 2.1 Wrap `HomeScreen`'s root in `BoxWithConstraints` and give the `Column` `Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight)` with `verticalArrangement = Arrangement.Center`, keeping the inset padding inside; verify centered look when it fits and full scroll (Настройки/Тема reachable) in landscape.

## 3. Verification

- [x] 3.1 Compile JVM + Android; `:sharedUI:jvmTest` green.
- [x] 3.2 Roborazzi: run `verifyRoborazziJvm`; if a screen's golden changed intentionally, `recordRoborazziJvm` and commit.
- [x] 3.3 Device/emulator check in landscape: Home (Настройки/Статистика/Тема), Settings (Удалить аккаунт), Stats, Paywall all reachable by scrolling; Game unchanged.
- [x] 3.4 `openspec validate scrollable-screens --strict` passes.
