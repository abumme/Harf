# Spec Delta

## ADDED Requirements

### Requirement: Founder is a restorable one-time product
The Founder lifetime unlock SHALL be sold as a non-consumable one-time product on each mobile store and SHALL map to the same Founder entitlement. A successful purchase or supported restore SHALL not require buying the product a second time for its owner.

#### Scenario: Founder purchase grants lifetime ownership
- **WHEN** the mobile store confirms purchase of the Founder product
- **THEN** the Founder entitlement becomes active for its verified customer

#### Scenario: Reinstall and restore
- **WHEN** a Founder purchaser reinstalls the mobile app and restores purchases using the owning store account
- **THEN** the existing Founder ownership can be recovered without a second charge

### Requirement: Mobile purchase requires sign-in and follows the Harf account
A mobile Founder purchase SHALL require the player to be signed into a Harf account first, so purchase-provider identity is associated with that account from the start and no anonymous purchase state is created. Verified purchases and restores SHALL be recognized across the player's devices signed into the same account. An account switch SHALL NOT display the previous account's ownership.

#### Scenario: Purchase requires sign-in
- **WHEN** a signed-out player initiates a Founder purchase on mobile
- **THEN** the app requires sign-in to a Harf account before checkout and binds the purchase to that account

#### Scenario: Switch to a different account
- **WHEN** a mobile device switches from a Founder account to another Harf account
- **THEN** purchase-provider state is refreshed for the new account and the former account's access is not shown as its ownership
