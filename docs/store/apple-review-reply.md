# Apple App Review reply — Guideline 2.1 (Information Needed)

Paste section **A** into the App Store Connect review message, and section **B** into
App Review Information → Notes (it stays there for future submissions).
Section **C** is a shot list for a screen recording — keep it only for a reviewer who asks
for one. The Beta App Review rejection of 2026-09-30 states plainly that "providing a demo
video showing your beta app in use is not enough", so A must not lean on a video.

---

## A. Reply message (App Store Connect → Resolution Center)

```text
Hello App Review team,

Thank you for the review, and sorry for the trouble accessing the app.

1. NO DEMO ACCOUNT EXISTS, AND NONE IS NEEDED

Harf has no username/password system at all, so there are no credentials we can
put in the Sign-In Information fields. There is no paywalled or account-gated
area: every feature, including the full daily puzzle, is reachable on a fresh
install with no sign-up, no login, and no network connection.

The only sign-in in the app is Sign in with Apple, and it is entirely optional.
Its sole purpose is to sync statistics between a player's own devices and to
restore purchases. Nothing is hidden behind it.

2. WHAT WENT WRONG, AND WHAT WE FIXED

We believe the reviewer met two real defects, both now fixed and verified on a
physical device against our production server:

- The Settings screen offered a "Link Google Account" button on iOS. There is no
  Google sign-in in the iOS build, so that button could only ever answer
  "unavailable". It has been removed; iOS now shows Sign in with Apple only.
- A server-side configuration error made our backend reject every Sign in with
  Apple token, so "Link Apple Account" failed with an error message. The
  configuration has been corrected and sign-in, account linking and account
  deletion (including Apple token revocation, per TN3194) have been retested
  end to end.

This build contains those fixes.

3. HOW TO REACH EVERY FEATURE

- Launch the app and pick a puzzle language (Oʻzbekcha Latin/Cyrillic, Русский,
  Қазақша, English). The daily puzzle opens immediately, with no account.
- Play: type a word on the on-screen keyboard, press Enter. Tiles colour green
  (right letter, right place), red (letter present elsewhere) or grey (absent).
  Six attempts. The word length varies by puzzle and is shown by the tile count.
- Statistics: the chart icon on the home screen.
- Settings (gear icon): language and script, themes, legal links, account,
  purchases.
- Optional sign-in: Settings → "Link Apple Account" → Sign in with Apple →
  confirm a display name.
- Account deletion: Settings → Delete Account → confirm. This removes the
  server-side account and all of its data, revokes the Apple token, and clears
  local statistics. No support contact is required.
- Offline: the daily puzzle is fully playable in Airplane Mode.

4. TESTING THE IN-APP PURCHASES

Settings → "Manage purchases" lists what the account owns and holds "Restore
purchases" and "Request a refund". The purchase screen itself is Settings →
the Edition section.

All products are optional non-consumables — cosmetic colour themes and a
"Founder" bundle (puzzle archive, hard mode, supporter badge). The daily puzzle
and all core gameplay are free forever and are never gated.

Every product is submitted together with this version, so the review build can
complete a purchase in the App Review sandbox at no charge. "Restore purchases"
on the same screen restores them afterwards. No sandbox credentials are needed
from us.

5. USER-GENERATED CONTENT

The app has no user-to-user features: no profiles visible to others, no chat, no
comments, no feeds, no uploads, no sharing between players. There is therefore
no in-app content to report or block.

The single thing a player can submit is a word: when a typed word is missing
from our dictionary, a "Suggest to add" button appears and sends that one word
to our editors for private review. Suggested words are never shown to other
players and enter the dictionary only if our editorial team accepts them. A
display name is visible only to its own owner.

6. EXTERNAL SERVICES

- Our own backend (Ktor + PostgreSQL on Oracle Cloud, https://api.lazydevs.uz):
  daily-word calendar, word lists, optional statistics sync, word suggestions.
- Sign in with Apple — optional authentication, iOS only.
- RevenueCat — purchase management on top of Apple's StoreKit. Payment
  processing is Apple's.
- No advertising SDKs, no third-party analytics or attribution, no tracking (no
  IDFA, no ATT prompt), no AI services. All vocabulary is our own editorial
  content.

If any part of the app is still unreachable, please tell us which screen or
action failed and what it showed. We will reproduce and fix it immediately. If
you would still prefer an account despite none being required, we can supply a
sandbox Apple ID on request.

Thank you for your time.

Mekhrojbek Islomov
lazydevscat@gmail.com
```

---

## B. App Review Information → Notes (keep for future submissions)

```text
Harf is a single-player daily word puzzle game. One puzzle per day, same word for
everyone, six tries. Works fully offline; no account required.

Sign-in: NOT required. Optional Sign in with Apple in Settings, used only to sync
stats/streak across the player's own devices and restore purchases. (Google
Sign-In exists in the Android build only.)

Account deletion: Settings -> Delete Account -> confirm. Deletes the server
account and all its data, revokes the Apple token, clears local stats.

In-app purchases: optional non-consumables only - cosmetic themes and a "Founder"
bundle (archive, hard mode, badge). Settings -> Manage purchases; "Restore
purchases" on the same screen. Core daily game is free forever, never gated.

User-generated content: none shown to other users. Only a "Suggest to add" button
that privately sends one unknown word to our editors for review. No chat,
profiles, feeds or uploads, so no reporting/blocking mechanism applies.

External services: our own backend (Ktor/PostgreSQL, api.lazydevs.uz) for the
daily-word calendar, stats sync and suggestions; Sign in with Apple; RevenueCat
over StoreKit for purchases. No ads, no third-party analytics, no tracking (no
IDFA/ATT), no AI services.

Regions: identical everywhere. UI localized en/ru/uz by device language; puzzle
language (Uzbek Latin/Cyrillic, Russian, Kazakh, English) chosen in-app.

Content rights: all words, art and text are our own original content.

Contact: lazydevscat@gmail.com
```

---

## C. Screen recording shot list (one take, physical iPhone, latest iOS)

Only if a reviewer asks for a recording — a video does not satisfy a Beta App Review
request for credentials, which says so in as many words.

Record with iOS Screen Recording (Control Center) or
`xcrun devicectl` / QuickTime; upload the .mp4/.mov in Resolution Center.
Keep it 3-5 minutes, no cuts, no title cards. Delete the app first so the
recording starts from a clean install.

1. Home screen of iOS, tap the Harf icon - the launch must be in frame.
2. Onboarding / language picker: pick a language.
3. Daily puzzle: type 2-3 guesses, show green/red/grey tiles, finish the round
   (win or loss) and show the result screen.
4. Statistics screen: streak and distribution.
5. Settings: switch a free theme.
6. Paid content: Manage purchases -> open the purchase screen -> tap a product ->
   the StoreKit sheet appears (sandbox purchase is fine, or cancel it and show it
   again as "Owned") -> tap "Restore purchases".
7. Account: "Link Apple Account" -> Sign in with Apple sheet -> complete it ->
   display-name dialog -> "Account linked" state.
8. Account deletion: Delete Account -> confirmation dialog -> Delete -> back to
   the signed-out state.
9. Suggestion flow: type a nonsense word of the day's length, show the "Suggest to add"
   button, tap it, show "Sent for review".
10. Optional, strong signal: enable Airplane Mode and play a guess to show the
    offline claim is real.
