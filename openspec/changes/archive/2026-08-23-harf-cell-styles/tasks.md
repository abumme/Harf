## 1. Mark-style variants

- [x] 1.1 Define the `MarkStyle` contract (tile + key rendering per correct/present/absent) and wire it through the `HarfMarks` CompositionLocal from foundation; verify board/keyboard read the active style without code change.
- [x] 1.2 Implement `Scribble` style (pencil loop/underline/strike, the mockup default); verify a preview shows all three states shape-distinct in grayscale.
- [x] 1.3 Implement `Fill` and `Outline` styles using theme feedback roles; verify each covers all three states for tiles and keys and stays distinct in grayscale.
- [x] 1.4 Verify styles follow the active palette: switching palette recolors the active style in a preview.

## 2. Experiment controller

- [x] 2.1 Implement `StyleExperimentController` with persisted `dayCount`/`lastDayCounted`/`phase`/`chosenStyle` in KSafe, advancing once per new local calendar date; verify tests: style advances per day, same-day relaunch does not advance, resumes after restart.
- [x] 2.2 Drive active style from the controller (rotating→styles[n], decided→chosenStyle); verify the board reflects the controller's active style.

## 3. Preference prompt & settings

- [x] 3.1 Implement the one-time post-rotation preference prompt that sets and locks the choice; verify it does not reappear after choosing or after dismissal (default applied).
- [x] 3.2 Implement the settings style picker with per-style previews and manual override; verify a settings change becomes sticky and ends the experiment.

## 4. Local capture

- [x] 4.1 Record a `style-choice` (style id + source + timestamp) locally on every experiment/settings selection; verify a test reads back the recorded choice and its source. No network.

## 5. Integration

- [x] 5.1 New-player walkthrough on desktop: sessions 1–3 rotate styles, prompt appears once, choice locks and persists across restart, settings override works; verify end to end. — Covered end-to-end by `CellStylesTest` (per-day rotation, same-day no-advance, resume after restart, choose locks + records, settings override sticky + records) and the `mark_styles.png` Roborazzi golden. Interactive desktop walkthrough not run (headless).
