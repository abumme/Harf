# Play Games achievements — bulk import

Assets for the Play Console **bulk import** of achievements (Play Console → Play Games Services → your game → Achievements → Import/Export).

## Contents
- `AchievementsMetadata.csv` — one row per achievement. Columns (no header row):
  `Name,Description,Incremental,Steps,State,Points,ListOrder`
- `AchievementsLocalizations.csv` — translations. Columns (no header row):
  `Name,Localized name,Localized description,locale` (base language is the English text in Metadata; `ru` + `uz` added here).
- `AchievementsIconsMappings.csv` — icon per achievement. Columns (no header row):
  `Name,Icon filename`.
- `*.png` — 512×512 badge icons referenced by the mappings.

`Name` is the key that ties the three CSVs together — it must match **exactly** across all of them.

## The set
| Name | Type | Points | Unlock condition (client) |
|---|---|---|---|
| First win | binary, revealed | 5 | first solved daily |
| Week streak | binary, revealed | 25 | best streak ≥ 7 |
| Month streak | binary, revealed | 100 | best streak ≥ 30 |
| Hole in one | binary, hidden | 50 | solved on the first guess |
| Centurion | incremental (100 steps), revealed | 100 | total wins reaches 100 |

Total points: 280 (Play cap is 2000).

## How to zip
Zip the **files flat** (no parent folder), CSV + PNG together:
```
cd play/achievements
zip ../achievements.zip AchievementsMetadata.csv AchievementsLocalizations.csv AchievementsIconsMappings.csv *.png
```
Constraints Google enforces: no CSV header rows, no commas inside any field, each file < 1 MB, < 403 files total, < 800 MB.

## After import
Play Console assigns an **achievement ID** to each. Paste those IDs into the app's
`androidApp` games-ids string resource (`res/values/games_ids.xml`) — see the
`play-games-services` OpenSpec change, task 1.2. Blank IDs make the feature no-op.

Icons are placeholders (generated) — swap for final art anytime, keep the filenames.
