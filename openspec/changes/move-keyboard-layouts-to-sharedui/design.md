## Context

See proposal.md (Why) for the motivation. The relevant current state:

- `:sharedUI` already has a client model of a layout: `KeyboardShape(letterRows, actionRowKeys)` in `feature/game/GameLayout.kt`, built by `KeyboardShape.of(config)` from `config.keyboard` and `config.actionRowKeys`.
- Every UI reader goes through `KeyboardShape.of(config)` (`GameContent`, `KeyboardView`, `GameLayoutTest`, `GameContentTest`, `BoardScreenshotTest`), with two exceptions that read the config directly: the physical-keyboard `keyLookup` in `GameScreen.kt` (`config.keyboard.flatten() + config.actionRowKeys`) and `KeyboardViewTest` (`LaunchLanguages.uzCyrl.keyboard.flatten()` for the expected labels).
- `LanguageConfig` is `@Serializable`, but nothing serializes it except the round-trip test in `LanguageConfigTest`; only `LaunchLanguages` constructs it. Dropping two fields changes no wire or stored format.
- `LanguageRegistry` defaults to `LaunchLanguages.all`, and the app never registers other configs.

## Goals / Non-Goals

**Goals:**
- A commit that edits only a keyboard layout touches no `BACKEND_PATHS` input.
- The layouts are byte-for-byte the current ones; readers see the same rows, keys and order.

**Non-Goals:**
- Changing any layout, the `KeyboardView` rendering, `GameLayout` sizing or the physical-keyboard behavior.
- Moving anything else out of `LanguageConfig` (graphemes, normalization, exceptions and display stay shared), or dropping its `@Serializable`.
- Changing CI path filters. The fix is to move the data, not to filter paths.

## Decisions

**Reuse `KeyboardShape` as the layout type.** It already is the client's layout model (letter rows plus action-row keys). A new `KeyboardLayout` type, or a pair of raw lists, would duplicate it.

**Data in `feature/game/KeyboardLayouts.kt`: `object KeyboardLayouts { val all: Map<String, KeyboardShape> }`**, keyed by `LaunchLanguages.<lang>.id` rather than string literals, so an id typo cannot compile. Shaped like `LaunchLanguages.all`. The comments that explain the layouts (Uzbek-Latin's `oʻ` `gʻ` in the action row; "first-pass, flagged for native review") move with them.
- *Alternative: `sharedUI/.../lang/KeyboardLayouts.kt`.* Rejected: `lang` would depend on `feature.game.KeyboardShape`, or need its own type that duplicates `KeyboardShape`.
- *Alternative: a private map in `KeyboardShape`'s companion.* Rejected: a separate data file keeps a layout edit a one-file diff, away from the sizing code in `GameLayout.kt`.

**`KeyboardShape.of(config)` keeps its signature** and becomes `KeyboardLayouts.all.getValue(config.id)`. Every existing reader keeps working unchanged. A config without a layout fails fast with `getValue` instead of falling back: a fallback would draw a keyboard whose keys are not the language's graphemes. A commonTest asserting `KeyboardLayouts.all.keys == LaunchLanguages.all.keys` makes that failure unreachable for shipped languages.

**`KeyboardShape.keys` (letter rows flattened, then action-row keys)** replaces the two direct reads: `GameScreen`'s `keyLookup` becomes `KeyboardShape.of(config).keys.associateBy { it.lowercase() }` (same list, same order, so the same map), and `KeyboardViewTest` takes its labels from `KeyboardShape.of(LaunchLanguages.uzCyrl).letterRows`. The new invariants test uses `keys` too.

**The test split.** `sharedData`'s `LanguageConfigTest` keeps what is about shared data: the serialization round trip, a non-empty inventory per language, the Uzbek-Latin digraph set, and no digraphs in the other scripts. A new `sharedUI/src/commonTest/.../feature/game/KeyboardLayoutsTest.kt` takes the keyboard checks against `LaunchLanguages` graphemes: one layout per launch language, keys ⊆ graphemes, action-row keys not in letter rows, Uzbek-Latin has three letter rows and `[oʻ, gʻ]` in the action row, the other languages have no action-row keys, and every Uzbek-Latin digraph has a key. `GameLayoutTest.every_launch_language_has_four_keyboard_rows` already covers the row count and stays.

**Commit order, each buildable:**
1. `sharedUI`: add `KeyboardLayouts.kt`, point `KeyboardShape.of` at it and add `keys` (`GameLayout.kt`), add `KeyboardLayoutsTest.kt`.
2. `sharedUI`: `GameScreen` `keyLookup` and `KeyboardViewTest` read the shape.
3. `sharedData`: drop `keyboard`/`actionRowKeys` from `LanguageConfig`, the layouts from `LaunchLanguages`, the keyboard checks from `LanguageConfigTest`.
4. Unscoped: one line in `CLAUDE.md` saying where keyboard layouts live.

Before commit 3, while both copies exist, a throwaway check (not committed) asserts `KeyboardLayouts.all.getValue(id) == KeyboardShape(config.keyboard, config.actionRowKeys)` for all five ids. That catches a transcription slip in any language, including kk, which has no golden frame.

## Risks / Trade-offs

- [A layout is mistyped in the move] → The throwaway equality check above, then `GameLayoutTest`, `KeyboardViewTest` and the Roborazzi goldens. Move the lists by cut and paste, not by retyping.
- [A language added to `LaunchLanguages` without a layout crashes the game screen] → `KeyboardLayoutsTest` fails on the mismatched id sets in `:sharedUI:jvmTest`, which CI runs on every PR.
- [The language definition now spans two modules, so adding a language means editing both] → Accepted; that is the point of the split. The `CLAUDE.md` line and the new spec text say where each part lives.
- [Merging this change counts as a backend input once, because it edits `sharedData` main sources] → Expected and harmless: the image rebuilds against the slimmer `LanguageConfig`. Only later layout edits skip the backend.
- [Local `verifyRoborazziJvm` is only advisory] → Expect no golden diffs at all, locally or on CI. Any diff means the data changed; do not re-record.

## Migration Plan

No data migration: `LanguageConfig` has no persisted or wire form. Rollback is a plain revert of the commits, in reverse order.
