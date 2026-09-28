# Apple App Review reply — Guideline 2.1 (Information Needed)

Paste section **A** into the App Store Connect review message, and section **B** into
App Review Information → Notes (it stays there for future submissions).
Section **C** is the shot list for the required screen recording.

---

## A. Reply message (App Store Connect → Resolution Center)

```text
Hello App Review team,

Thank you for the review. Below is all requested information. A screen recording
captured on a physical iPhone running the latest iOS is attached, and the same
information has been added to the Notes field of App Review Information.

1. SCREEN RECORDING

Attached: a single recording made on a physical iPhone (iOS 26), starting from app
launch. It shows, in order:
- Cold launch and onboarding / language selection
- The daily puzzle being played: typed guesses, tile colouring (green = correct
  letter in the correct position, red = letter present elsewhere, grey = absent),
  and the win screen
- The statistics screen (streak, distribution)
- Settings: theme selection, then the optional paid content flow — opening the
  purchase screen, the StoreKit purchase sheet for a non-consumable, and
  "Restore purchases"
- Account registration and login: "Link Apple Account" (Sign in with Apple) in
  Settings, and the display-name step
- Account deletion: Settings -> Delete Account -> confirmation dialog -> account
  and all of its data removed (the app returns to the signed-out state)
- The word-suggestion control, which is the only user input the app transmits
  (see item 4 below on why no reporting/blocking mechanism applies)

2. PURPOSE AND TARGET AUDIENCE

Harf is a daily word puzzle game. Every day there is one puzzle, the same word
for every player worldwide, to be guessed in six tries. It is a single-player
game with no ads, no timers and no forced sign-up.

Target audience: general audience, age 4+, people who enjoy a short daily word
puzzle. The specific problem it solves: daily word games of this kind exist in
English but not for the languages of Central Asia. Harf is playable in Uzbek
(both Latin and Cyrillic script), Russian, Kazakh and English, and it counts
letters the way a native speaker does - Uzbek digraphs such as "sh" and "oʻ" are
scored as one letter, not two. The value it provides is a correct, calm,
fully offline daily puzzle in the player's own language.

3. SETTING UP AND ACCESSING THE MAIN FEATURES

No account and no credentials are required. Everything below works on a fresh
install, including with the device in Airplane Mode:

- Launch the app, pick a language (Oʻzbekcha Latin/Cyrillic, Русский, Қазақша,
  English). The daily puzzle opens immediately.
- Type a word with the on-screen keyboard and press Enter. Word length varies by
  puzzle (4 to 7 letters, shown by the number of tiles). Tiles colour
  as described above. Six attempts per day. The puzzle changes at local midnight
  in each language's fixed time zone.
- Statistics: the chart icon on the home screen (games played, win rate, current
  and best streak, guess distribution).
- Settings (gear icon): language and script, theme, legal links, account, and
  account deletion.
- Optional sign-in: Settings -> "Link Apple Account" (Sign in with Apple). On iOS
  this is the only sign-in method. Its sole purpose is syncing statistics and the
  streak between the player's own devices, and restoring purchases. Nothing in
  the game is gated behind it.
- Account deletion: Settings -> Delete Account -> confirm. This deletes the
  server-side account and all its data, revokes the Apple sign-in token on our
  server, and clears local statistics. It is available to any signed-in user
  inside the app, with no support contact required.
- Paid content: Settings -> Manage purchases. The daily puzzle and all core
  gameplay are free forever. The in-app purchases are optional non-consumables:
  cosmetic colour themes, and a "Founder" bundle (puzzle archive, hard mode,
  supporter badge). "Restore purchases" is on the same screen. All products are
  configured in App Store Connect and submitted with this build.

No demo account, credentials or sample files are needed. If the review team
prefers one anyway, we can provide a test Apple ID on request.

4. USER-GENERATED CONTENT

The app has no user-to-user features: no profiles visible to others, no chat, no
comments, no feeds, no photo or file uploads, no sharing of content between
players. Consequently there is no in-app content to report or block.

There is exactly one thing a player can submit: when a typed word is not in our
dictionary, a "Suggest to add" button appears. It sends that single word to our
own server for our editors to review privately. Suggested words are never shown
to other players; they only enter the dictionary if our editorial team accepts
them. A player's display name (used for sign-in) is shown only to that player.

5. EXTERNAL SERVICES, TOOLS AND PLATFORMS

- Our own backend server (Ktor + PostgreSQL, hosted on Oracle Cloud,
  https://api.lazydevs.uz): publishes the daily-word calendar and word lists,
  stores the optional statistics sync, receives word suggestions. Operated by us.
- Sign in with Apple (Apple) - optional authentication on iOS.
- Google Sign-In - used on the Android version only; it is not available in the
  iOS build.
- RevenueCat - in-app purchase management on top of Apple's StoreKit / In-App
  Purchase. Payment processing is Apple's.
- No advertising SDKs, no third-party analytics or attribution SDKs, no tracking
  (no IDFA, no ATT prompt), no AI services, no external data providers. All
  vocabulary and word lists are our own editorial content.

6. REGIONAL DIFFERENCES

The app functions consistently in all regions. There is no geofencing, no
region-locked content and no regional pricing logic beyond Apple's standard
App Store price tiers. The only differences a player can see are chosen by the
player, not by region: the interface is localized in English, Russian and Uzbek
and follows the device language, and the playable puzzle language (Uzbek Latin,
Uzbek Cyrillic, Russian, Kazakh, English) is selected in-app and can be changed
at any time. Each puzzle language rolls over at midnight in its own fixed time
zone, so all players of that language share the same word on the same day.

7. REGULATED INDUSTRY / THIRD-PARTY MATERIAL

The app is not in a regulated industry. It contains no protected third-party
material: the word lists, puzzles, artwork, icon and all text are created and
owned by us. Fonts in use are licensed for this purpose. The game is an original
word puzzle and is not affiliated with, or licensed from, any other word game.

Thank you for your time.

Umid Olimzhanov
uolimzhanov@gmail.com
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

Contact: uolimzhanov@gmail.com
```

---

## C. Screen recording shot list (one take, physical iPhone, latest iOS)

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
