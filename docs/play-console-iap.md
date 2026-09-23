# Play Console — In-app products

Managed **non-consumable** products (bought once, owned forever). No subscriptions.

Product IDs mirror the RevenueCat entitlement ids the client checks
(`billing/BillingModels.kt`: `ENTITLEMENT_LIFETIME = "harf_founder"`,
`ENTITLEMENT_THEME_PREFIX = "theme_"`). Palettes come from
`theme/HarfColors.kt` (`HarfPalettes.all`); `newsprint` is the free default
(`AppSettings.DEFAULT_PALETTE`) and is **not** a product.

Play Console limits: Name ≤ 55 chars, Description ≤ 200 chars.

| # | Product ID | RevenueCat entitlement | Palette id | Base price (US/EU) | UZ/CIS regional |
|---|------------|------------------------|------------|--------------------|-----------------|
| 1 | `harf_founder`     | `harf_founder`     | — (bundle) | $4.99 | ~24,000–29,000 UZS (~$2) |
| 2 | `theme_press`      | `theme_press`      | `press`      | $0.99 | ~9,900–12,900 UZS |
| 3 | `theme_ink`        | `theme_ink`        | `ink`        | $0.99 | ~9,900–12,900 UZS |
| 4 | `theme_blueprint`  | `theme_blueprint`  | `blueprint`  | $0.99 | ~9,900–12,900 UZS |
| 5 | `theme_schoolbook` | `theme_schoolbook` | `schoolbook` | $0.99 | ~9,900–12,900 UZS |

Pricing plan: set the base price, then use Play Console **regional pricing** to
override UZ/CIS lower — a flat $4.99 prices out the core Uzbek audience. Founder
sits ~5× a theme to read as "the real unlock"; themes at the $0.99 impulse floor.

---

## Listings

### 1. `harf_founder`

**EN**
- Name: `Founder Lifetime Unlock`
- Description: `One-time lifetime unlock: the full published puzzle archive, hard mode, and the Founder badge. Cosmetic themes sold separately. No subscription.`

**RU**
- Name: `Founder — разблокировка навсегда`
- Description: `Разовая покупка навсегда: полный архив опубликованных головоломок, сложный режим и значок Founder. Косметические темы продаются отдельно. Без подписки.`

### 2. `theme_press`

**EN**
- Name: `Press Red Theme`
- Description: `A bold newsroom palette — red headline accents on clean newsprint. Cosmetic board theme, unlocked forever.`

**RU**
- Name: `Тема Press Red`
- Description: `Смелая газетная палитра — красные акценты заголовков на чистой бумаге. Косметическая тема доски, навсегда.`

### 3. `theme_ink`

**EN**
- Name: `Ink Theme`
- Description: `Quiet monochrome ink on paper with a soft blue accent. Cosmetic board theme, unlocked forever.`

**RU**
- Name: `Тема Ink`
- Description: `Спокойная монохромная тушь на бумаге с мягким синим акцентом. Косметическая тема доски, навсегда.`

### 4. `theme_blueprint`

**EN**
- Name: `Blueprint Theme`
- Description: `Cool architect's-blueprint blues across the board. Cosmetic board theme, unlocked forever.`

**RU**
- Name: `Тема Blueprint`
- Description: `Холодные синие тона архитектурного чертежа по всей доске. Косметическая тема доски, навсегда.`

### 5. `theme_schoolbook`

**EN**
- Name: `Schoolbook Theme`
- Description: `Warm green-and-amber classroom palette. Cosmetic board theme, unlocked forever.`

**RU**
- Name: `Тема Schoolbook`
- Description: `Тёплая зелёно-янтарная «школьная» палитра. Косметическая тема доски, навсегда.`

---

## Founder note

The `harf_founder` listing promises three extras: **puzzle archive + hard
mode + Founder badge**. All three now ship: the `deliver-founder-edition`
OpenSpec change is **archived** (`openspec/changes/archive/2026-09-22-deliver-founder-edition/`),
and the code is live — `HardModeValidator`, `ArchiveScreen`/`ArchiveServerService`,
and the Founder badge. So the listing accurately states the bundle across in-app,
hosted paywall, legal offer, and store listing; the description is cleared to
publish. Founder product is non-consumable and maps to the same `harf_founder`
entitlement across stores (purchases spec, `deliver-founder-edition`).
