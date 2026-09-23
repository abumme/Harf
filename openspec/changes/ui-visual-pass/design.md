# Design — ui-visual-pass

## Visual authority

The **"After" column** of the before/after mockup is the canonical reference:
- In-repo: `docs/ui-review/before-after.html`
- Published: https://claude.ai/artifact/4ZkU31gB1xvWx7xViVhu7H

Where this document and the mockup agree, the mockup wins on look; this document exists to pin the values that must survive translation from HTML into Compose — chiefly the **corner radii**, so the "after" shapes are not silently flattened to one default radius.

## Shape scale (pin these radii)

Taken from the mockup's After components. These are the standard radii; nothing in scope should introduce a new one.

| Role | Radius | Mockup source |
|---|---|---|
| Buttons — primary / ghost / danger | **10.dp** | `.btn`, `.btn.ghost`, `.btn.danger` |
| Containers — cards, setting groups, danger zone | **12.dp** | `.grp`, `.danger-zone` |
| List rows (standalone row cards) | **11.dp** | `.arow` (used by `archive-history`) |
| Chips / badges / streak pill | **fully rounded** (pill) | `.badge`, `.streak` |
| Theme swatches | **8.dp** | `.sw` |

Existing tile/key radii (5.dp keys, per-style tile shapes) are **out of scope and unchanged**.

Expose these as named shape tokens (e.g. `HarfShapes.button = RoundedCornerShape(10.dp)`, `.container = 12.dp`, `.row = 11.dp`, `.pill = 50%`, `.swatch = 8.dp`) so every screen references the token, not a literal — the same discipline the color tokens already use, and the mechanism that keeps the radii from drifting.

## Control system

Three button styles, one implementation, driven by tokens:

| Style | Fill | Border | Text | Use |
|---|---|---|---|---|
| Primary | `accent` | none | `onAccent` (white) | the one action per screen |
| Ghost | transparent | `accent` 1.5dp | `accent` | secondary navigation |
| Danger | transparent | `danger` 1.5dp | `danger` | destructive, isolated |

- Min height 44.dp (raise toward 48.dp where layout allows — accessibility).
- One primary per screen. If a screen has none, it has only ghost/danger.

Shared top bar: `‹ <label>` back (real label, not a bare `←` glyph — and with an accessible name) + serif title, right-padded to keep the title optically centered. Used by Settings here; reused by Stats/Archive in their changes.

## Token additions

`danger`, `onDanger`, `success`, `onSuccess` added to `HarfColors` and to every palette. Suggested Newsprint values (tune per edition): `danger = #B3282D` (the existing `present` red reads as the brand's alert red), `onDanger = #FFFFFF`, `success = #2E7D46`, `onSuccess = #FFFFFF`. Map into the Material scheme's `error`/`onError` (currently mis-mapped to `present`).

## Home hierarchy

```
Harf (masthead)
"Ежедневная игра в слова"
── ИГРАТЬ СЕГОДНЯ ─────────
[ O‘zbek · сегодня   ]  primary
[ Русский · сегодня  ]  primary
[ Архив ][ Статистика ][ Настройки ]  ghost row
── ИЗДАНИЕ ────────────────
[◻][◻][◻][◻][◻]  swatch row (selected = ink border)
```

Locked editions still route to the paywall via the existing `EntitlementGate.canApplyTheme`; the swatch row shows all editions but marks unowned ones (lock affordance) rather than hiding them.

## Decisions / trade-offs

- **Swatches vs. carousel:** swatches (recognition) chosen over the current blind cycle; on a narrow phone the 5 editions still fit one row at 30.dp. If editions grow past ~7, wrap to two rows rather than reintroduce a cycle.
- **Where the shared components live:** a small `core/ui` (or `theme/components`) package, not a new module — avoids build-graph churn before release.
- **Copy in this change vs. its own:** the Home/Settings/error strings ride here because they are the same screens being restyled; Archive-specific strings move with `archive-history`.
