## ADDED Requirements

### Requirement: Blocked players cannot suggest
The system SHALL refuse a suggestion from a player account whose suggestions an ADMIN has blocked. The refusal SHALL happen before validation, and the suggestion SHALL NOT be stored, verified against a dictionary, counted against the daily cap, or shown to editors. The response SHALL be a forbidden error that identifies the block, so the current app reports it with its existing failure message. Blocking SHALL NOT change suggestions the account made before the block.

#### Scenario: Blocked account's suggestion is refused
- **WHEN** a blocked account submits a well-formed word that would otherwise be accepted for review
- **THEN** the system SHALL respond forbidden, SHALL NOT store the suggestion, and SHALL NOT notify editors or look the word up

#### Scenario: Current app shows its failure message
- **WHEN** the current app receives the refusal for a blocked account
- **THEN** it SHALL show its existing "couldn't send" message and SHALL NOT claim the word was sent for review

#### Scenario: Earlier suggestions stay reviewable
- **WHEN** an account with pending suggestions is blocked
- **THEN** those suggestions SHALL remain pending and editors SHALL still be able to decide them

#### Scenario: Unblocked account suggests again
- **WHEN** a previously blocked account is unblocked and submits a valid word
- **THEN** the system SHALL accept the suggestion for review as for any other account
