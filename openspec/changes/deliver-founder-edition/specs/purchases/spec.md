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

### Requirement: Mobile purchase identity follows the Harf account
When a player signs into a linked Harf account on mobile, purchase-provider identity SHALL be associated with that account so verified purchases and restores can be recognized across the player's devices. A purchase made before account linking SHALL remain recoverable and become available to that account after a valid association; an account switch SHALL NOT display the previous account's ownership.

#### Scenario: Link after an anonymous purchase
- **WHEN** a player buys Founder before linking an account and then links a verified provider identity
- **THEN** the existing purchase is associated with the resulting Harf account without charging again

#### Scenario: Switch to a different account
- **WHEN** a mobile device switches from a Founder account to another Harf account
- **THEN** purchase-provider state is refreshed for the new account and the former account's access is not shown as its ownership
