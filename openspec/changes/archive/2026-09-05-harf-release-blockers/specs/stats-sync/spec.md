## ADDED Requirements

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
