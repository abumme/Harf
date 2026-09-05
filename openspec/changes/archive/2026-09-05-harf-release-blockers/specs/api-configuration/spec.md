## Purpose

Defines how the client determines which backend environment (development, staging, production) it talks to, so a shipped build reaches a real API over HTTPS instead of a hardcoded localhost address.

## ADDED Requirements

### Requirement: Client connects to a configured environment base URL
The client SHALL resolve its backend API base URL from configuration rather than a hardcoded host, and a release build SHALL default to a production HTTPS URL.

#### Scenario: Base URL comes from configuration
- **WHEN** the client constructs its network layer
- **THEN** it SHALL use a base URL supplied by build/runtime configuration for the active environment, not a compiled-in `localhost` address

#### Scenario: Release build targets a secure production endpoint
- **WHEN** a release build makes network calls
- **THEN** the resolved base URL SHALL be an HTTPS production endpoint, and SHALL NOT be a `localhost`/loopback address

#### Scenario: Development build can target a local server
- **WHEN** a developer runs a non-release build against a local backend
- **THEN** the configuration SHALL allow pointing at a local/dev URL (including the mapping needed for an Android emulator to reach a developer machine) without editing source

#### Scenario: Missing production configuration is caught, not defaulted to localhost
- **WHEN** a release build has no API base URL configured
- **THEN** the build or startup SHALL fail fast rather than silently falling back to `localhost`
