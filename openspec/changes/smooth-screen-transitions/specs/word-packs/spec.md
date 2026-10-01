## ADDED Requirements

### Requirement: Pack resolution never blocks the UI
Resolving a language's word pack SHALL NOT run on the UI thread on platforms that have a separate background thread pool (Android, iOS, desktop). This covers reading the bundled files, decoding a cached or snapshot pack, tokenizing its entries and checking its integrity. The UI SHALL keep rendering frames while a pack is being resolved. Once a language's pack is resolved, later requests in the same app session SHALL return it without resolving it again, until a newer valid pack is adopted.

#### Scenario: Opening a game while its pack is unresolved
- **WHEN** the player opens a language's game and that language's pack has not been resolved yet in this app session
- **THEN** the pack is resolved off the UI thread, and the navigation transition into the game keeps animating without stalling while it is resolved

#### Scenario: A resolved pack is reused
- **WHEN** a language's pack has been resolved and the player opens that language's game again in the same session
- **THEN** the same pack is used without reading or tokenizing the pack again

#### Scenario: Played languages are ready before the player asks
- **WHEN** the app starts and the player has finished at least one round in some languages
- **THEN** the packs of those languages are resolved in the background while the player is still on Home. Languages the player has never played are not resolved until they are opened.

### Requirement: An adopted fetched pack is not rebuilt
When a fetched pack passes the integrity check, the app SHALL adopt the pack it built during that check. Neither the check nor the adoption SHALL run on the UI thread on platforms with a background thread pool. The next game in that language SHALL use the adopted pack without resolving it again. A fetched pack that fails the check SHALL leave the current pack in use, as before.

#### Scenario: Adopted pack is used without a rebuild
- **WHEN** a background sync fetches a newer pack for a language and it passes integrity
- **THEN** the pack is cached for later launches, and the next game in that language uses it immediately without tokenizing it again

#### Scenario: Rejected pack changes nothing
- **WHEN** a background sync fetches a pack that fails integrity
- **THEN** the language keeps its current resolved pack, and nothing is cached
