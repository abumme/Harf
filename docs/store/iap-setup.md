# IAP: создание продуктов в App Store Connect + привязка RevenueCat

Контракт задан кодом, не документацией. Источник истины:

| Что | Где в коде | Значение |
|---|---|---|
| Entitlement «Founder» | `billing/BillingModels.kt` `ENTITLEMENT_LIFETIME` | `harf_founder` |
| Префикс entitlement темы | `billing/BillingModels.kt` `ENTITLEMENT_THEME_PREFIX` | `theme_` |
| id тем (палитры) | `theme/HarfColors.kt` `HarfPalettes` | `newsprint` (бесплатная), `press`, `ink`, `blueprint`, `schoolbook` |
| Продукты читаются из | `RevenueCatPurchaseController.offerings()` | `Offerings.current.availablePackages` |
| Классификация продукта | там же | Founder: `id == "harf_founder"` (либо содержит `lifetime`); тема: id начинается с `theme_` |
| iOS ключ RC | `sharedUI/build.gradle.kts` → `local.properties` `revenuecat.iosKey` | префикс `appl_` |

Значит нужны **ровно эти** product id:

- `harf_founder` — Non-Consumable (архив, hard mode, бейдж)
- `theme_press`, `theme_ink`, `theme_blueprint`, `theme_schoolbook` — Non-Consumable, косметика

И entitlement id в RevenueCat: `harf_founder`, `theme_press`, `theme_ink`, `theme_blueprint`, `theme_schoolbook`
(entitlement id = product id, так короче и совпадает с `themeEntitlementId(paletteId)`).

> `app-store-listing.md` раньше перечислял `harf_lifetime`, `theme_dusk`, `theme_sepia`, `theme_forest`,
> `theme_nord`, `theme_matrix`, `theme_solar` — таких палитр в приложении нет. Список исправлен.

---

## 0. Предварительно (иначе продукты не создать)

1. App Store Connect → **Business** (Agreements, Tax, and Banking): подписать **Paid Applications Agreement**,
   заполнить банк и налоги. Без этого раздел In-App Purchases пустой/заблокирован.
2. Приложение `uz.abumme.harfgame` уже создано в ASC — да.

## 1. Создать продукты в App Store Connect

App Store Connect → **Apps → Harf → Monetization → In-App Purchases** → **＋**.

Для каждого из 5 продуктов:

1. Type: **Non-Consumable**.
2. Reference Name: внутреннее имя (`Founder Edition`, `Theme — Press Red`, …). Не видно покупателю.
3. Product ID: точно `harf_founder` / `theme_press` / `theme_ink` / `theme_blueprint` / `theme_schoolbook`.
   **Изменить потом нельзя.**
4. **Pricing** → выбрать цену (например Founder ~ $4.99, тема ~ $0.99). Обязательно.
5. **Localizations** → минимум English (U.S.); желательно Russian и Uzbek.
   Display Name (≤30) + Description (≤45 символов в поле ASC — короткая строка).
   Примеры:
   - `harf_founder` — en: `Founder Edition` / `Puzzle archive, hard mode, badge`
   - `theme_press` — en: `Press Red Theme` / `Cosmetic colour theme`
6. **Review Information**: обязательный **Screenshot** (PNG/JPEG, ≥ 640×920; публично не показывается)
   + Review Notes («Cosmetic theme unlock, non-consumable, accessible from Settings → Manage purchases»).
   Один и тот же снимок годится для всех 5 продуктов. Как его получить — см. «Скриншот для ревью» ниже.
7. Save. Статус станет **Missing Metadata → Ready to Submit**.

Затем в разделе версии приложения (**Distribution → версия 1.0**) в блоке **In-App Purchases and Subscriptions**
нажать **＋** и добавить все 5 продуктов к сабмиту. Apple в письме прямо на это намекает (3.1.1):
продукты должны идти вместе с билдом.

Для тестирования: **Users and Access → Sandbox → Test Accounts** → создать sandbox Apple ID
(email, которого нет в Apple; пароль). Sandbox-покупка бесплатна и нужна для видео-записи.

## 2. Ключи Apple для RevenueCat

RevenueCat должен валидировать покупки на сервере Apple. Нужны:

1. **In-App Purchase Key** (StoreKit 2, рекомендуется):
   ASC → **Users and Access → Integrations → In-App Purchase** → **＋** → имя `RevenueCat` → Generate.
   Скачать `.p8` (**один раз!**), записать **Key ID** и **Issuer ID**.
2. **App-Specific Shared Secret** (для старых StoreKit 1 receipt-валидаций, лишним не будет):
   ASC → Apps → Harf → **App Information → App-Specific Shared Secret** → Manage → сгенерировать, скопировать.

## 3. Настроить RevenueCat

app.revenuecat.com:

1. **Project** (создать, если ещё нет) → **Apps → ＋ App Store**.
   - App name: Harf
   - **Bundle ID:** `uz.abumme.harfgame`
   - Загрузить **In-App Purchase Key** (`.p8` + Key ID + Issuer ID)
   - Вставить **App-Specific Shared Secret**
2. Скопировать **публичный SDK-ключ** iOS: RC → **API Keys** → ключ с префиксом **`appl_`**.
   (Android-ключ `goog_` — отдельный, уже настроен.)
3. **Products** → ＋ → App Store → ввести те же 5 product id (`harf_founder`, `theme_press`, `theme_ink`,
   `theme_blueprint`, `theme_schoolbook`). RC подтянет их из ASC (продукты должны быть уже созданы, иначе «not found»).
4. **Entitlements** → создать 5 entitlement с id ровно как в таблице выше, и приложить к каждому
   соответствующий продукт (`harf_founder` → продукт `harf_founder`, и т.д.).
   Код читает `customerInfo.entitlements.active` — без entitlement покупка не разблокирует ничего.
5. **Offerings** → offering `default`, отметить его **Current** (код берёт только `offerings.current`) →
   добавить 5 **packages**. Тип package неважен (можно Lifetime/Custom), важно что внутри лежит нужный продукт:
   код ищет по `pkg.storeProduct.id`.
6. **Paywalls** (нужно для `HostedPaywall` / `CustomerCenter` из `HostedBillingUi.mobile.kt`): в offering `default`
   собрать Paywall в RC Paywall Editor, иначе hosted-экран покажет пустоту. Свой `PaywallScreen` работает без этого.
7. Необязательно, но полезно: **App Store Server Notifications V2** — ASC → App Information →
   App Store Server Notifications → Production/Sandbox URL из RC (RC → App Store app → Apple Server Notifications).

## 4. Прописать ключ в проект

Ключи лежат в `revenuecat.properties` (gitignored; шаблон — `revenuecat.properties.example`):

```properties
revenuecat.iosKey=appl_XXXXXXXXXXXXXXXXXXXXXXXX
revenuecat.androidKey=goog_XXXXXXXXXXXXXXXXXXXXXXXX
revenuecat.testKey=test_XXXXXXXXXXXXXXXXXXXXXXXX
```

Порядок поиска каждого ключа: `-P<имя>` → `revenuecat.properties` → `local.properties`.
`revenuecat.testKey` (Test Store) подставляется вместо отсутствующего ключа стора **только в
не-релизных сборках**; релиз на него никогда не падает — забытый ключ уедет пустым, а не тестовым.

Что за ключ подставится, видно без сборки приложения:

```bash
./gradlew -q :sharedUI:rcKeyReport            # dev: test_… если ключа стора нет
CONFIGURATION=Release ./gradlew -q :sharedUI:rcKeyReport   # ios как в релизе
```

Пустой ключ → `BuildConfig.REVENUECAT_KEY` пустой → `RevenueCatPurchaseController.isAvailable = false`
→ paywall пишет «Store unavailable», а темы становятся бесплатными (`EntitlementGate.canApplyTheme` при
`purchasesAvailable = false` пропускает всё). **В таком билде покупок для Apple не показать.**

Затем собрать Release-архив, поставить на устройство, зайти под sandbox Apple ID
(Settings → App Store → Sandbox Account) и пройти покупку — это же и есть пункт 6 сценария записи
в `apple-review-reply.md`.

## 4.1. Скриншот для ревью (Review Information → Screenshot)

Нужен paywall с реальными названиями и ценами. В симуляторе и без `appl_`-ключа экран покажет
«Store unavailable», поэтому порядок такой:

1. Продукты в ASC созданы, цена и локализации заполнены → статус **Ready to Submit**.
   Sandbox отдаёт продукты уже в этом статусе, ждать одобрения не нужно.
2. `revenuecat.iosKey=appl_…` в `local.properties`, RC настроен (products + entitlements + offering Current).
3. Release-билд на физическом устройстве (TestFlight или архив из Xcode).
4. На устройстве: Settings → App Store → **Sandbox Account** → войти тестовым Apple ID.
5. В игре: Settings → Manage purchases → paywall со списком продуктов → снимок (Power + Volume Up).
6. Загрузить файл в Review Information каждого из 5 продуктов.

Тот же кадр закрывает пункт 6 сценария видеозаписи в `apple-review-reply.md`.

## 5. Чек-лист перед повторным сабмитом

- [ ] Paid Applications Agreement активен
- [ ] 5 продуктов в ASC, статус Ready to Submit: цена + локализации (en-US минимум) + Review Screenshot ≥640×920
- [ ] Продукты прикреплены к версии 1.0 вместе с билдом
- [ ] In-App Purchase Key и Shared Secret отданы RevenueCat
- [ ] 5 entitlement в RC, id совпадают с кодом
- [ ] Offering `default` помечен **Current**, в нём 5 packages
- [ ] `revenuecat.iosKey=appl_…` в `revenuecat.properties`, `rcKeyReport` показывает `appl_…`, архив собран с ним
- [ ] Sandbox-аккаунт создан, покупка и Restore проверены на железе
