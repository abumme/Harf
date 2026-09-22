# Tasks

## 1. Apple browser sign-in

- [ ] 1.1 Add Apple browser sign-in to Android and JVM desktop with server-validated callback handling; verify an Apple-linked iOS account signs into both clients and a cancelled callback leaves the session unchanged. (Gated on Apple Services ID and store configuration.)
- [ ] 1.2 Add Apple sign-in to web with the configured Services ID and return URL; verify it reaches the same backend account as iOS and rejects invalid audience/state/nonce data.
- [ ] 1.3 After deploy, verify an Apple-only owner recovers Founder access on Android, JVM desktop, and web, and that a cancelled or forged callback grants nothing.
