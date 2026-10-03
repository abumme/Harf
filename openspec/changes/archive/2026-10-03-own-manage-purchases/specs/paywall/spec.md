# paywall Specification

## ADDED Requirements

### Requirement: App-drawn purchase management
The app SHALL provide a purchase-management surface it draws itself, reachable from Settings, listing
every purchase the player owns and offering to restore purchases. It SHALL read ownership from the
same entitlement source as the paywall, so the two never disagree, and SHALL render in the active Harf
edition rather than following the system appearance.

#### Scenario: Owned purchases are listed
- **WHEN** a player who owns the Founder unlock or any theme opens purchase management
- **THEN** each owned purchase is listed, and the screen matches the active edition's colours

#### Scenario: Nothing owned
- **WHEN** a player who owns nothing opens purchase management
- **THEN** the screen says so and still offers to restore purchases, rather than showing an empty area

#### Scenario: Restore reports its outcome
- **WHEN** the player restores purchases
- **THEN** the screen reports whether something was restored, nothing was found, or the store was
  unavailable, and any restored entitlement takes effect immediately

### Requirement: Refund route per platform
Purchase management SHALL point the player at the platform's own refund path — Apple's Report a
Problem on iOS, Google Play's order history on Android — and SHALL omit the entry where the platform
has no store.

#### Scenario: A refund is requested on a store platform
- **WHEN** the player asks for a refund on Android or iOS
- **THEN** the platform's refund page opens

#### Scenario: No store, no dead entry
- **WHEN** purchase management is opened on desktop or web
- **THEN** no refund entry is shown
