## Purpose

The daily-word calendar decides which word is every language's word of the day: ADMINs pick words for chosen days, the system fills every other day automatically without repeating words, and days players may already hold are never changed.

## ADDED Requirements

### Requirement: One published word per day per calendar
The system SHALL keep one calendar for each of `en`, `ru`, `kk` and `uz`, each counting days in its language's timezone (`en` and `uz` Asia/Tashkent, `kk` Asia/Almaty, `ru` Europe/Moscow). Every day from the first scheduled day through at least 60 days after today SHALL have exactly one daily word, and the system SHALL extend this horizon as days pass without staff action. The `uz` calendar SHALL publish the same lexeme on a given day in both Uzbek scripts.

#### Scenario: Horizon is always filled
- **WHEN** a day passes in a calendar's timezone
- **THEN** every day from today through at least 60 days ahead SHALL still have exactly one daily word

#### Scenario: Uzbek day is one lexeme in both scripts
- **WHEN** the `uz` calendar assigns a pair to a day
- **THEN** the `uz-latn` pack SHALL publish the pair's Latin word and the `uz-cyrl` pack its Cyrillic word for that day

### Requirement: Past, current and next days are locked
Any day before the day after tomorrow in the calendar's timezone SHALL be locked: its daily word SHALL NOT change through a manual pick, an unpick, an automatic re-pick, an answer-pool change or a catalog change. Staff requests to pick or unpick a locked day SHALL be refused. Players and WORDERs SHALL NOT see anything about the lock.

#### Scenario: Picking tomorrow is refused
- **WHEN** an ADMIN picks a word for tomorrow in the calendar's timezone
- **THEN** the system SHALL refuse the request as a locked day and tomorrow's word SHALL be unchanged

#### Scenario: Lock follows the calendar's own midnight
- **WHEN** it is 23:59 in Europe/Moscow and an ADMIN picks a word for the `ru` day two days ahead, and the same request arrives again at 00:01 Moscow time the next day
- **THEN** the first request SHALL be accepted and the second SHALL be refused because that day has become tomorrow

#### Scenario: Catalog removal does not change a locked day
- **WHEN** today's daily word is removed from the catalog
- **THEN** today's and tomorrow's daily words SHALL be unchanged and today's word SHALL still be accepted as a guess by clients

### Requirement: ADMIN picks the word for a future day
An ADMIN SHALL be able to pick an eligible word for any day from the day after tomorrow up to 365 days ahead, replacing that day's automatic or earlier manual word. The system SHALL refuse a pick of a word that was the daily word of a counted past, current or next day, or that is manually picked for another day, and the refusal SHALL name the day or days the word is used on. When the picked word is automatically scheduled on another future day, that other day SHALL receive a new automatic pick.

#### Scenario: Manual pick replaces the automatic word
- **WHEN** an ADMIN picks an eligible, never-used word for a day ten days ahead
- **THEN** that day's daily word SHALL be the picked word, marked as manual with the picking ADMIN

#### Scenario: Already used word is refused with its dates
- **WHEN** an ADMIN picks a word that was the daily word three days ago
- **THEN** the system SHALL refuse the pick and state that the word was used on that date

#### Scenario: Word picked for another day is refused
- **WHEN** an ADMIN picks a word already manually picked for a different future day
- **THEN** the system SHALL refuse the pick and name that day

#### Scenario: Taking an automatic word re-picks its old day
- **WHEN** an ADMIN picks a word that is the automatic pick of another future day
- **THEN** the chosen day SHALL get the word and the other day SHALL get a different automatic pick

#### Scenario: Ineligible word cannot be picked
- **WHEN** an ADMIN picks a word that is not in the calendar's answer pool
- **THEN** the system SHALL refuse the pick

### Requirement: Unpicking returns a day to automatic
An ADMIN SHALL be able to remove the manual pick of an unlocked day, after which the day SHALL receive an automatic pick.

#### Scenario: Unpicked day is filled automatically
- **WHEN** an ADMIN unpicks a manual word for a day a week ahead
- **THEN** that day SHALL show an automatic word and the previously picked word SHALL be free for other days

### Requirement: Automatic picks fill every day without a manual pick
Every unlocked day without a manual pick SHALL receive an automatic pick: a randomly chosen active eligible word that has never been the daily word of a counted day and is not scheduled on any other day. An automatic pick SHALL stay stable across recalculations while it remains valid, and SHALL be replaced only when its word stops being eligible or active, is taken by a manual pick, or is a repeat while a never-used word is available.

#### Scenario: Automatic picks do not collide
- **WHEN** the calendar fills 60 unlocked days from a pool with more than 60 never-used words
- **THEN** no word SHALL be scheduled on two days

#### Scenario: Automatic pick is stable
- **WHEN** the calendar is recalculated without any change affecting a day's automatic word
- **THEN** that day's word SHALL stay the same

### Requirement: Words do not repeat
A word SHALL NOT be the daily word of two counted days of the same calendar while the calendar has any never-used eligible word. When no never-used eligible word remains for an automatic pick, the system SHALL reuse the eligible word whose most recent use is the oldest, and SHALL mark that day as a repeat. Repeats SHALL NOT produce any alert or notification; they SHALL be visible only as a marker in the ADMIN calendar and table views. When a never-used word becomes eligible, the earliest unlocked repeat day SHALL be replaced by a never-used word.

#### Scenario: Exhausted pool reuses the least recently used word
- **WHEN** an automatic pick is needed and every eligible word has already been used or scheduled
- **THEN** the system SHALL pick the eligible word whose last use is the oldest and mark the day as a repeat

#### Scenario: Repeat is silent
- **WHEN** a day is filled with a repeat
- **THEN** no Telegram message, email or panel alert SHALL be sent, and the ADMIN calendar SHALL show the repeat marker with the date the word was last used

#### Scenario: New eligible word replaces a repeat
- **WHEN** an unlocked future day is a repeat and an ADMIN marks a never-used word as eligible
- **THEN** a repeat day SHALL receive a never-used word and lose its repeat marker

### Requirement: History counts from the public launch
Only days on or after the configured history start date SHALL count as used for the no-repeat rule. Words that were daily words before that date SHALL be treated as never used.

#### Scenario: Test-period words are free at launch
- **WHEN** the history start date is set to the public launch date and a word was the daily word during the test period before it
- **THEN** that word SHALL count as never used and SHALL be available for manual and automatic picks after the launch

### Requirement: Catalog changes never break the calendar
When a word is removed from the catalog, loses daily eligibility, or has its text edited — by an ADMIN or a WORDER — locked days SHALL keep the word exactly as it was played or published. Unlocked automatic days using that word SHALL silently receive a new automatic pick. An unlocked manual pick of a word that was removed or lost eligibility SHALL be replaced by an automatic pick and the system SHALL show ADMINs a notice naming the day, the word and the reason. An unlocked manual pick of a word whose text was edited SHALL keep that word with its new text. Nothing about these effects SHALL be shown to the WORDER who made the change.

#### Scenario: WORDER removes an automatically scheduled word
- **WHEN** a WORDER removes a word that is the automatic pick of a day a week ahead
- **THEN** that day SHALL get a new automatic pick and the WORDER's response SHALL NOT mention the calendar

#### Scenario: Manual pick of a removed word raises a notice
- **WHEN** a word that an ADMIN manually picked for a future unlocked day is removed from the catalog
- **THEN** the day SHALL get an automatic pick and ADMINs SHALL see a notice naming the day, the removed word and that it was removed

#### Scenario: Edited manual pick follows the edit
- **WHEN** the text of a manually picked word for an unlocked day is corrected in the catalog
- **THEN** the day SHALL keep the manual pick with the corrected text

#### Scenario: Edit does not rewrite a played day
- **WHEN** the text of yesterday's daily word is edited
- **THEN** yesterday's daily word SHALL remain the text that was played

### Requirement: Calendar and table views
The panel SHALL give ADMINs, per calendar, a month view and a table view of daily words. Each day SHALL show its word (both scripts for `uz`), whether it was picked manually or automatically, the picking ADMIN for manual picks, a repeat marker with the previous use date for repeats, and whether the day is locked. The table view SHALL filter by date range, manual or automatic, and repeats. When picking, the panel SHALL let the ADMIN search eligible words and show for each candidate whether it was never used, when it was last used, or which day it is scheduled on.

#### Scenario: Month view shows day details
- **WHEN** an ADMIN opens the `kk` calendar for a month
- **THEN** every day of the month SHALL show its word, manual or automatic marker, picking ADMIN when manual, repeat marker when a repeat, and lock state

#### Scenario: Table filters repeats
- **WHEN** an ADMIN filters the table view to repeats within a date range
- **THEN** only repeat days within that range SHALL be listed

#### Scenario: Candidate search shows usage
- **WHEN** an ADMIN searches for a word to pick
- **THEN** each candidate SHALL show whether it was never used, its last use date, or the day it is currently scheduled on

### Requirement: Only ADMINs access the calendar
Every calendar request from a WORDER SHALL be refused as forbidden and change nothing, and the panel SHALL NOT show WORDERs any calendar navigation, page, notice or audit entry.

#### Scenario: WORDER is refused
- **WHEN** a WORDER requests any calendar view or tries to pick or unpick a day
- **THEN** the system SHALL respond forbidden and SHALL NOT change the calendar

### Requirement: Calendar changes publish instantly and are audited
Every change to a calendar's future days — a manual pick, an unpick, or an automatic re-pick caused by another change — SHALL publish immediately: the affected packs' schedules SHALL reflect the calendar and their versions SHALL advance. A recalculation that changes no day and does not extend the horizon SHALL NOT advance any version. Every manual pick, unpick and notice dismissal SHALL be recorded in the audit log with the acting ADMIN, the calendar, the day, the word and the time; a replacement of a manual pick by the system SHALL be recorded as a system action.

#### Scenario: Pick is published
- **WHEN** an ADMIN picks a word for a future day in the `en` calendar
- **THEN** the `en` pack's schedule SHALL contain the word on that day and its version SHALL advance

#### Scenario: No-op recalculation keeps the version
- **WHEN** the calendar is recalculated and no day changes and the horizon is already filled
- **THEN** no pack version SHALL advance

#### Scenario: Pick is audited
- **WHEN** an ADMIN picks or unpicks a day
- **THEN** an audit entry SHALL record the ADMIN, the calendar, the day, the word and the time
