# stats-sync Specification

## Purpose
Defines how a player's stats and streak progress are synchronized between the backend and any number of client devices for the same account, so progress is never lost when switching or reinstalling.

## Requirements

### Requirement: Uploading a stats snapshot
The system SHALL allow an authenticated client to upload its current stats/streak snapshot for reconciliation with the server's stored snapshot.

#### Scenario: First-ever upload for an account
- **WHEN** an authenticated client uploads a stats snapshot and no snapshot exists yet for that account
- **THEN** the system SHALL store the uploaded snapshot as the account's current stats

#### Scenario: Newer snapshot overwrites older snapshot
- **WHEN** an authenticated client uploads a stats snapshot whose timestamp is newer than the account's currently stored snapshot
- **THEN** the system SHALL replace the stored snapshot with the uploaded one in full

#### Scenario: Older snapshot is rejected in favor of stored data
- **WHEN** an authenticated client uploads a stats snapshot whose timestamp is older than or equal to the account's currently stored snapshot
- **THEN** the system SHALL discard the uploaded snapshot and SHALL leave the stored snapshot unchanged

#### Scenario: Snapshot with a far-future timestamp is rejected
- **WHEN** an authenticated client uploads a stats snapshot whose timestamp is more than a small tolerance ahead of the server's current time
- **THEN** the system SHALL reject the upload as invalid and SHALL leave the stored snapshot unchanged, so a device with a wrong clock cannot permanently block other devices' writes

### Requirement: Downloading the current stats snapshot
The system SHALL allow an authenticated client to fetch the account's current server-stored stats/streak snapshot.

#### Scenario: Fetching stats on a new or second device
- **WHEN** an authenticated client requests the current stats snapshot for its account
- **THEN** the system SHALL return the most recently accepted snapshot for that account, or an empty/default snapshot if none exists yet

### Requirement: Sync is scoped to the authenticated account
The system SHALL only read or write stats/streak data belonging to the account identified by the caller's access token.

#### Scenario: Unauthenticated sync request is rejected
- **WHEN** a client calls a stats sync endpoint without a valid access token
- **THEN** the system SHALL reject the request as unauthorized and SHALL NOT read or write any stats data

### Requirement: Durable pending stats upload
The client SHALL durably record that a stats snapshot needs uploading when a round completes, and SHALL retry the upload on later connectivity or foreground so an offline result reaches the server without the player having to play another round.

#### Scenario: Offline round is queued for upload
- **WHEN** a round completes while the client cannot reach the server
- **THEN** the client SHALL persist a pending-upload marker (surviving app restart) rather than dropping the update

#### Scenario: Pending upload retries on reconnect or foreground
- **WHEN** connectivity is restored or the app returns to the foreground with a pending upload outstanding
- **THEN** the client SHALL attempt the stats upload without requiring a new round to be played

#### Scenario: Pending upload clears only on confirmed success
- **WHEN** a pending stats upload is accepted by the server
- **THEN** the client SHALL clear the pending marker; if the upload fails, the marker SHALL remain so a later attempt retries

#### Scenario: A queued result appears on a second device after reconnect
- **WHEN** a result recorded offline on one device is later uploaded on reconnect and a second device for the same account fetches stats
- **THEN** the second device SHALL see that result without either device replaying the round
