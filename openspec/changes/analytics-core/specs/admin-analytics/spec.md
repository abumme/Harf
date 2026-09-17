## Purpose

Gives ADMINs aggregated analytics in the admin panel: accounts, activity, retention, streaks, outcomes, word difficulty, suggestions, content and staff activity. It also defines the data captured behind these numbers and the privacy boundaries that keep analytics aggregate-only.

## ADDED Requirements

### Requirement: Analytics is available to ADMINs only
The system SHALL serve analytics data, dashboards and exports only to authenticated ADMIN staff. A WORDER SHALL be refused and SHALL NOT see any analytics navigation entry. A request without a valid staff session SHALL be refused as unauthorized. Player access tokens SHALL never grant access. For an ADMIN, the analytics dashboard SHALL be the home page after login.

#### Scenario: ADMIN opens the dashboard
- **WHEN** an ADMIN logs in to the admin panel
- **THEN** the panel SHALL open the analytics dashboard as the home page

#### Scenario: WORDER is refused
- **WHEN** a WORDER requests any analytics data or export
- **THEN** the system SHALL respond forbidden and return no analytics data

#### Scenario: WORDER sees no analytics entry
- **WHEN** a WORDER is logged in to the admin panel
- **THEN** the navigation SHALL NOT offer analytics

#### Scenario: Unauthenticated request is refused
- **WHEN** analytics data is requested without a valid staff session, including with a player access token
- **THEN** the system SHALL respond unauthorized and return no analytics data

### Requirement: Game results are recorded from stats uploads
Whenever an authenticated stats upload is received, the system SHALL record each result record it carries as one stored game result per account, language and puzzle day, with the outcome (won or lost) and attempts used.

Recording rules:
- It SHALL be idempotent.
- A result already recorded for an account, language and puzzle day SHALL NOT be changed or duplicated by later uploads.
- A later snapshot that no longer contains a recorded result SHALL NOT remove it.
- Recording SHALL NOT change the upload's response, the stored snapshot reconciliation, or any client-visible behaviour.
- Records from an upload rejected as invalid SHALL NOT be recorded.
- Records with an unknown language, attempts outside 1–6, or a puzzle day later than the current puzzle day of that language SHALL be ignored without failing the upload.

#### Scenario: Uploaded results become game results
- **WHEN** an authenticated client uploads a snapshot containing two result records for different puzzle days
- **THEN** the system SHALL hold one game result for each of those account, language and puzzle day combinations, with the uploaded outcome and attempts

#### Scenario: Re-uploading the same records adds nothing
- **WHEN** the same snapshot is uploaded again, or a newer snapshot repeats records already recorded
- **THEN** the number of stored game results SHALL NOT change

#### Scenario: A later snapshot without a result does not remove it
- **WHEN** an account uploads a snapshot that lacks a result recorded from an earlier upload
- **THEN** the earlier game result SHALL remain stored

#### Scenario: An older snapshot still contributes its results
- **WHEN** an upload is discarded in favour of the stored snapshot because its timestamp is not newer
- **THEN** its result records not yet recorded SHALL still be recorded as game results, and the upload response SHALL be the same as before this change

#### Scenario: An invalid upload contributes nothing
- **WHEN** an upload is rejected because its timestamp is too far in the future
- **THEN** none of its records SHALL be recorded as game results

#### Scenario: An implausible record is ignored
- **WHEN** an upload carries a record with attempts of 0 or 7, an unknown language, or a puzzle day after that language's current puzzle day
- **THEN** that record SHALL NOT be recorded, the other records SHALL be recorded, and the upload SHALL succeed as it would have before this change

### Requirement: Existing synced statistics are backfilled
When analytics is first deployed, the system SHALL record game results for every result record in the stats snapshots already stored, following the same recording rules as uploads. It SHALL do so once, without blocking server startup or the stats sync endpoints.

#### Scenario: Snapshots stored before deployment are counted
- **WHEN** analytics is deployed onto a database that holds stored stats snapshots
- **THEN** each valid record in those snapshots SHALL become a game result for its account

#### Scenario: Backfill is not repeated
- **WHEN** the server restarts after the backfill completed
- **THEN** the backfill SHALL NOT run again and the number of game results SHALL NOT change because of it

### Requirement: Account events are recorded without identity
The system SHALL count each explicit account deletion per day without storing which account was deleted. This covers deletion by the player and deletion by an ADMIN. It SHALL NOT count an anonymous account discarded while linking to an existing account. The system SHALL record when each new Google or Apple link is made. Links made before this change have no known link time and SHALL NOT appear in links per day.

#### Scenario: Player deletion is counted
- **WHEN** a player deletes their account
- **THEN** that day's deletion count SHALL increase by one and no identifier of the deleted account SHALL be stored for analytics

#### Scenario: ADMIN deletion is counted
- **WHEN** an ADMIN deletes a player account from the admin panel
- **THEN** that day's deletion count SHALL increase by one

#### Scenario: Merge-link cleanup is not a deletion
- **WHEN** an anonymous account is discarded because its provider identity already belongs to another account
- **THEN** the deletion count SHALL NOT change

#### Scenario: New link time is recorded
- **WHEN** an account links a Google or Apple identity after this change
- **THEN** the link SHALL count toward links per day on the day it was made

#### Scenario: Older links have no link date
- **WHEN** links per day is shown for days before this change was deployed
- **THEN** the system SHALL show those days as having no link data rather than zero, and those links SHALL still count in linked-account totals

### Requirement: Days in analytics
Results SHALL be attributed to their puzzle day: the day of the language's fixed rollover timezone in which the round was played. This applies to per-language and cross-language result metrics. All other metrics (accounts, links, deletions, suggestions, content and staff activity) SHALL be attributed to the calendar date in Asia/Tashkent of the moment the event happened.

#### Scenario: Result attributed to its puzzle day
- **WHEN** a Russian result for a given puzzle day is uploaded two days later
- **THEN** it SHALL count toward that puzzle day, not the upload day

#### Scenario: Event attributed to its Tashkent date
- **WHEN** an account is created at 23:30 Europe/Moscow, which is 01:30 the next day in Asia/Tashkent
- **THEN** it SHALL count as a new account on the Asia/Tashkent date

### Requirement: Daily aggregates for closed days
The system SHALL compute each metric's daily aggregates for days that have closed, and keep them current as follows:
- **Closed day:** a result metric's day closes when that puzzle day ends in its language's timezone (for cross-language result metrics, when it has ended in every language). An event metric's day closes when its Asia/Tashkent date ends.
- **Freshness:** aggregates for a newly closed day SHALL be available within one hour after it closes.
- **Catch-up:** if the server was down, every day missed SHALL be computed once it runs again, without manual action.
- **Settle window:** because offline players sync late, each day's aggregates SHALL be recomputed on later runs until 7 days after it closed and SHALL be final afterwards. Days still within that window SHALL be marked provisional.
- **Retention:** a cohort's D1, D7 or D30 value SHALL become available only once the target day has closed, and final once that day's settle window has passed.

#### Scenario: A closed day appears promptly
- **WHEN** a language's puzzle day ends
- **THEN** its daily players, games and outcome aggregates SHALL be available within one hour

#### Scenario: Downtime is caught up
- **WHEN** the server was stopped for three days and starts again
- **THEN** aggregates for all three missed days SHALL be computed without manual action

#### Scenario: A late-synced result is included while provisional
- **WHEN** a result for a closed day is uploaded three days after that day closed
- **THEN** that day's aggregates SHALL include it, and the day SHALL still be marked provisional

#### Scenario: Final days stop changing
- **WHEN** a result for a day that closed more than 7 days ago is uploaded
- **THEN** that day's final aggregates SHALL NOT change

#### Scenario: Recomputation does not double count
- **WHEN** a provisional day is recomputed several times
- **THEN** its aggregates SHALL equal a single computation over the game results stored at that time

### Requirement: Aggregates only, surviving account deletion
Analytics data, dashboards and exports SHALL contain aggregates only, with no player account identifiers, display names or provider identifiers. Staff activity may name staff members. When a player account is deleted, its stored game results SHALL be deleted with it. Final aggregates SHALL NOT change because of the deletion.

#### Scenario: No player identifiers in analytics
- **WHEN** an ADMIN views or exports any analytics table
- **THEN** the data SHALL contain no player account id, display name or provider identifier

#### Scenario: Deletion removes raw results but keeps final aggregates
- **WHEN** a player whose results contributed to a final day deletes their account
- **THEN** the player's game results SHALL no longer be stored, and that final day's aggregates SHALL remain unchanged

### Requirement: Account metrics
For the selected date range the dashboard SHALL show the following per day:
- new accounts
- links made (Google and Apple separately)
- explicit account deletions
- totals at the end of the day: all accounts, linked accounts, Google-linked, Apple-linked, and anonymous (never linked)

An account linked to both providers SHALL count once in linked accounts and once under each provider. End-of-day totals SHALL be available from the first day the rollup ran; earlier days SHALL show new accounts only.

#### Scenario: New accounts per day
- **WHEN** three accounts were created on one Asia/Tashkent date
- **THEN** new accounts for that date SHALL be 3

#### Scenario: Linked and anonymous totals
- **WHEN** at the end of a day there are 10 accounts, of which 3 are linked to Google, 1 to Apple and 1 to both
- **THEN** that day SHALL show 10 total, 5 linked, 4 Google-linked, 2 Apple-linked and 5 anonymous

### Requirement: Activity metrics
For the selected range and language filter the dashboard SHALL show, per puzzle day:
- **Daily players per language:** distinct accounts with a game result for that language and day.
- **Games per language:** the number of game results.
- **DAU:** distinct accounts with a result for that puzzle day in any language.
- **WAU:** distinct accounts with a result in the 7 days ending that day.
- **MAU:** distinct accounts with a result in the 30 days ending that day.

The dashboard SHALL also show players and games for each language's current puzzle day computed live, clearly marked as partial.

#### Scenario: Players and games per language
- **WHEN** on one English puzzle day four accounts have a result and one of them also has a Russian result that day
- **THEN** English SHALL show 4 players and 4 games, Russian 1 player and 1 game, and DAU for that day SHALL be 4

#### Scenario: WAU and MAU windows
- **WHEN** an account has results only on day d−6 and another only on day d−7
- **THEN** WAU for day d SHALL count the first account but not the second, and MAU for day d SHALL count both

#### Scenario: Today so far is partial
- **WHEN** an ADMIN views the dashboard during a language's current puzzle day
- **THEN** that language's players and games for today SHALL be shown, reflecting results stored so far and marked partial

### Requirement: Retention metrics
The dashboard SHALL show D1, D7 and D30 retention per cohort day over the selected range:
- **Cohort:** the accounts whose earliest game result, in any language, is for that puzzle day.
- **DN retention:** the share of the cohort with a game result in any language exactly N puzzle days later.
- The cohort size SHALL be shown next to each rate.
- A value whose target day has not closed SHALL be shown as not yet available.

#### Scenario: Retention by cohort
- **WHEN** 10 accounts have their earliest result on day c, 4 of them have a result on day c+1, 2 on day c+7 and 1 on day c+30
- **THEN** cohort c SHALL show size 10, D1 40%, D7 20% and D30 10%

#### Scenario: Future target is not yet available
- **WHEN** day c+30 has not closed
- **THEN** cohort c's D30 SHALL be shown as not yet available rather than 0%

### Requirement: Streak distribution
For each language and each closed puzzle day in the range, the dashboard SHALL show how many accounts had a current streak in each bucket: 1, 2–6, 7–29 and 30 or more. A current streak SHALL be computed from the account's game results using the app's rule, with that day as "today": the run of consecutive won puzzle days ending at the most recent won day, counted only when that day is today or yesterday. Accounts with no current streak SHALL NOT be counted.

#### Scenario: Buckets follow the app's streak rule
- **WHEN** on English day d one account won days d−2, d−1 and d, another won d−9 through d−1 but has not played d, and a third last won on day d−2
- **THEN** day d SHALL count the first account in 2–6, the second in 7–29, and SHALL NOT count the third

### Requirement: Outcome metrics
For each language and closed puzzle day in the range, and totalled over the range, the dashboard SHALL show:
- **Win rate:** wins divided by games.
- **Guess distribution:** wins solved in 1, 2, 3, 4, 5 and 6 attempts, plus the number of losses.
- **Average attempts:** the mean attempts over won games.

#### Scenario: Outcomes from recorded results
- **WHEN** a language-day has 5 games: wins in 2, 3, 3 and 4 attempts and one loss
- **THEN** it SHALL show win rate 80%, distribution 2:1, 3:2, 4:1, losses 1, and average attempts 3.0

### Requirement: Word difficulty per daily word
For each past daily word in the range, per calendar (English, Russian, Kazakh, Uzbek), the dashboard SHALL show:
- the day and the word; for Uzbek, both scripts
- the number of players, the win rate and the average attempts over won games
- whether the calendar recorded the word as a manual pick, an automatic pick or a repeat

Uzbek results in both scripts SHALL be combined, because both scripts share the day's lexeme. Days with no recorded calendar entry SHALL show the published word with no marker. The table SHALL be sortable by players, win rate and average attempts.

#### Scenario: Difficulty for a manual pick
- **WHEN** an English daily word picked manually was played by 20 accounts with 15 wins
- **THEN** its row SHALL show 20 players, 75% win rate, the average attempts of the 15 wins, and the manual marker

#### Scenario: Uzbek scripts are combined
- **WHEN** 6 accounts played a day's Uzbek word in Latin and 4 in Cyrillic
- **THEN** that day's Uzbek row SHALL show 10 players and both spellings of the word

### Requirement: Suggestion metrics
For each Asia/Tashkent date in the range, per language, the dashboard SHALL show:
- **Submitted:** suggestions stored that day.
- **Decisions made that day:** auto-accepted, editor-accepted and rejected. Decisions from the panel and from Telegram count as editor decisions; legacy decisions with no recorded source count as editor-accepted when accepted.
- **Pending backlog:** suggestions pending at the end of the day.
- **Median time from submission to decision:** shown separately for automatic and editor decisions made that day.

#### Scenario: Daily suggestion counts
- **WHEN** on one date 6 suggestions were submitted for Russian and that day 3 were auto-accepted, 1 accepted by an editor and 1 rejected
- **THEN** Russian for that date SHALL show 6 submitted, 3 auto-accepted, 1 editor-accepted and 1 rejected

#### Scenario: Backlog at end of day
- **WHEN** a suggestion was submitted on day d and decided on day d+2
- **THEN** it SHALL count in the pending backlog at the end of days d and d+1, and not at the end of day d+2

#### Scenario: Median decision time
- **WHEN** editors decided three suggestions on one date after 10 minutes, 2 hours and 1 day
- **THEN** that date's editor median time to decision SHALL be 2 hours

### Requirement: Content metrics
Per language and per Asia/Tashkent date in the range, the dashboard SHALL show:
- active catalog words at the end of the day
- words added that day, by source (bundled dictionary, suggestion, automatic acceptance, staff)
- words removed and restored that day

Per calendar it SHALL show:
- the answer pool size (eligible words; Uzbek counts eligible pairs)
- the number of eligible words never used as a daily word since the daily-word history start
- the number of repeat days in the range
- the number of scheduled upcoming repeat days

End-of-day values SHALL be available from the first day the rollup ran. Current values SHALL also be shown.

#### Scenario: Words added by source
- **WHEN** on one date staff added 12 English words and 3 English suggestions were accepted automatically
- **THEN** English for that date SHALL show 12 added by staff and 3 added by automatic acceptance

#### Scenario: Unused eligible words
- **WHEN** Kazakh has 30 eligible words and 23 of them have been used as daily words since the history start
- **THEN** Kazakh SHALL show an answer pool of 30 and 7 never-used eligible words

### Requirement: Staff activity metrics
For each staff member and Asia/Tashkent date in the range, the dashboard SHALL show:
- words added (restores count as added)
- words edited
- words removed
- suggestions decided, whether in the panel or through a Telegram account linked to that staff member

Telegram decisions not linked to any staff member SHALL be grouped as unlinked Telegram editors. Automatic acceptances SHALL NOT count toward any staff member. The table SHALL be filterable by language.

#### Scenario: A WORDER's day
- **WHEN** a WORDER added 40 words in one bulk add, edited 2, removed 1 and decided 5 suggestions in Telegram on one date
- **THEN** that WORDER's row for the date SHALL show 40 added, 2 edited, 1 removed and 5 decided

### Requirement: Dashboard filters
The dashboard SHALL let an ADMIN choose a date range and a language filter; every metric shown SHALL reflect both. The default range SHALL be the last 30 closed days. A range longer than 366 days SHALL be refused with a validation message. The language filter SHALL offer all languages or a single language. Metrics that are not per language SHALL ignore it and say so.

#### Scenario: Default range
- **WHEN** an ADMIN opens the dashboard
- **THEN** metrics SHALL cover the last 30 closed days across all languages

#### Scenario: Range too long
- **WHEN** an ADMIN selects a range of 400 days
- **THEN** the system SHALL refuse the range with a validation message and SHALL NOT run the query

#### Scenario: Language filter
- **WHEN** an ADMIN selects Kazakh
- **THEN** per-language metrics SHALL show Kazakh only, and cross-language metrics such as DAU SHALL be labelled as not filtered by language

### Requirement: CSV export
Every analytics table SHALL be exportable as a CSV file containing the rows and columns shown for the current filters. The file SHALL be UTF-8 with a header row, SHALL use the same values and provisional or partial markers as the dashboard, and SHALL be available to ADMINs only.

#### Scenario: Exporting a table
- **WHEN** an ADMIN exports the word difficulty table for Russian over the last 30 days
- **THEN** the downloaded CSV SHALL contain a header row and exactly the rows shown, with Cyrillic text intact

#### Scenario: WORDER cannot export
- **WHEN** a WORDER requests an analytics CSV export
- **THEN** the system SHALL respond forbidden and return no file
