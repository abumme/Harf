# Proposal

## Why

The Founder edition (`deliver-founder-edition`) shipped Google sign-in on all four platforms and Apple sign-in natively on iOS, but deferred Apple browser sign-in on Android, JVM desktop, and web pending an Apple Services ID and store configuration. Until it ships, an Apple-only owner cannot reach their Founder account off iOS. This change completes that deferred phase so every Apple owner recovers access on every supported client.

## What Changes

- Add Apple browser sign-in to Android and JVM desktop with server-validated callback handling, so an Apple-linked iOS account signs into both clients.
- Add Apple sign-in to web with the configured Services ID and return URL, reaching the same backend account as iOS.
- Reject invalid or cancelled callback data (audience, state, nonce) without changing the current session.

## Capabilities

### Modified Capabilities

- `account-auth`: Offer Apple sign-in on Android, JVM desktop, and web in addition to iOS, via a supported browser-based flow with server-validated callbacks.

## Impact

- Backend Apple provider callback handling and audience/state/nonce validation for the browser flow.
- Android, JVM desktop, and web client Apple sign-in glue in `sharedUI`.
- Apple Services ID and return-URL configuration; no database schema change.
