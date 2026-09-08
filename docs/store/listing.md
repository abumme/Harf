# Play Store listing — Harf

Draft copy and console values for the first release. App is localized **en / ru / uz**; the
Play listing should carry at least those three (kk/tr optional later). Character limits noted.

Playable languages in-game: **Oʻzbekcha (Latin + Cyrillic), Русский, English, Қазақша.**

---

## App name  (≤ 30 chars)

- **en:** `Harf: Daily Word Game`  (21)
- **ru:** `Harf: игра в слова`  (18)
- **uz:** `Harf: kunlik soʻz oʻyini`  (24)

## Short description  (≤ 80 chars)

- **en:** `One word a day. Six tries. Play in Uzbek, Russian, Kazakh or English.`  (69)
- **ru:** `Одно слово в день, шесть попыток. Узбекский, русский, казахский, англ.`  (69)
- **uz:** `Har kuni bitta soʻz, olti urinish. Oʻzbek, rus, qozoq va ingliz tilida.`  (71)

## Full description  (≤ 4000 chars)

### en
```
Harf is a daily word game. Every day, one new puzzle — the same word for
everyone. Guess it in six tries. Each guess colours the tiles: green means the
letter is right and in place, red means it's in the word but somewhere else,
grey means it's not there at all.

• A fresh puzzle every day, identical for every player
• Play in Uzbek (Latin and Cyrillic), Russian, Kazakh, or English
• Fully offline — no connection needed to play the daily word
• Keep your streak and see your stats
• Editorial, calm design with several themes
• Sign in with Google to sync your streak across devices (optional)

Harf understands letters the way a speaker does — Uzbek "sh" and "oʻ" count as
one letter, not two — so scoring feels natural in every language.

One word a day. Come back tomorrow.
```

### ru
```
Harf — ежедневная игра в слова. Каждый день одна новая головоломка, и слово
одно для всех. Угадайте его за шесть попыток. Плитки подсказывают: зелёная —
буква на своём месте, красная — буква есть в слове, но в другом месте, серая —
буквы в слове нет.

• Новая головоломка каждый день, одна для всех игроков
• Игра на узбекском (латиница и кириллица), русском, казахском и английском
• Полностью офлайн — для ежедневного слова не нужен интернет
• Серия дней подряд и статистика
• Спокойный дизайн и несколько тем оформления
• Вход через Google для синхронизации серии между устройствами (по желанию)

Harf считает буквы так же, как носитель языка: узбекские «sh» и «oʻ» — это одна
буква, а не две, поэтому подсчёт ощущается естественно на любом языке.

Одно слово в день. Возвращайтесь завтра.
```

### uz
```
Harf — har kunlik soʻz oʻyini. Har kuni bitta yangi topishmoq va soʻz hamma
uchun bir xil. Uni olti urinishda toping. Katakchalar rang bilan yordam beradi:
yashil — harf oʻz oʻrnida, qizil — harf soʻzda bor, lekin boshqa joyda, kulrang
— bunday harf yoʻq.

• Har kuni yangi topishmoq, barcha oʻyinchilar uchun bir xil
• Oʻzbek (lotin va kirill), rus, qozoq va ingliz tillarida
• Toʻliq oflayn — kunlik soʻz uchun internet shart emas
• Ketma-ket kunlar seriyasi va statistika
• Tinch, ozoda dizayn va bir nechta mavzular
• Google orqali kirish — seriyani qurilmalar oʻrtasida sinxronlash (ixtiyoriy)

Harf harflarni til egasi kabi sanaydi: oʻzbekcha "sh" va "oʻ" — bu ikki emas,
bitta harf, shu bois hisob har qanday tilda tabiiy koʻrinadi.

Har kuni bitta soʻz. Ertaga yana keling.
```

---

## Graphic assets (upload in Play Console → Store listing)

| Asset | Spec | Status |
|---|---|---|
| App icon | 512×512 PNG, 32-bit | ✅ `docs/store/harf_play_icon_512.png` |
| Feature graphic | 1024×500 PNG/JPG (no alpha) | ⬜ TODO — needed to publish |
| Phone screenshots | 2–8, PNG/JPG, 16:9 or 9:16, min 320px | ⬜ TODO — capture home, game mid-round, win, stats |
| 7" / 10" tablet shots | optional | ⬜ optional |

Screenshots can be captured from the debug app on a device/emulator (`android` CLI screenshot).

---

## Console fields / declarations

- **Category:** Games → Word
- **Tags:** word game, puzzle, daily
- **Contact email (public):** `<your support email>`  — required, shown on listing
- **Website:** `https://lazydevs.uz/harf`  (optional)
- **Privacy Policy URL:** `https://lazydevs.uz/harf/privacy`  — REQUIRED, must be live before publish
  (source text: `docs/legal/privacy-*.md` — host it there)
- **Content rating:** complete the questionnaire → expected **Everyone / PEGI 3** (word game, no
  violence, no user-generated content). Do not skip; unrated apps can't go to production.
- **Target audience & content:** 13+ (has optional account sign-in); not directed at children.
- **Ads:** No ads → declare "No".
- **Data safety form:**
  - Collected: **Account info** (a user id / OAuth identity when signing in with Google) and
    **App activity** (your game stats), for **app functionality** and cross-device sync.
  - Data is **encrypted in transit**.
  - Users **can request deletion** (in-app account deletion exists).
  - Google sign-in is **optional** — the daily game works fully offline without an account.
  - **No data shared** with third parties. **No ads.**
- **App access:** the game is playable without login; if a reviewer needs to test sync/sign-in,
  note that Google sign-in is optional and provide a test path.

---

## Release track

Personal account created in 2026 ⇒ **Closed testing with 12+ opted-in testers for 14 continuous
days** is required before applying for production. Start the closed-testing track first with the
signed AAB, gather testers, then request production access.

Signed AAB: `androidApp/build/outputs/bundle/release/androidApp-release.aab` (built & verified).
