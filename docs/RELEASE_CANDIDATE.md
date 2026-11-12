# TraktBridge 0.99.0 release candidate

Version code 111. This is a release candidate, not a stable 1.0 release.

## Automated release gates
- Manifest component export and permission checks, backup and cleartext policy checks.
- Unit tests, Android lint, debug assembly, permanent signing certificate verification.
- Dependency inventory workflow and artifact.

## Required before stable release
- Verify latest CI for the exact candidate commit and run the physical-device beta checklist.
- Complete dependency vulnerability advisory scanning and independent security review.
- Confirm OAuth account switching, interrupted uploads and backup recovery against real services.
- Measure startup, memory, battery and prolonged playback on representative Android and TV devices.
- Resolve known roadmap gaps: standalone Wear OS transport, collaborative cross-device libraries, local SQLite encryption and recovery migration, complete translation, and verified availability integrations.
- Record all outstanding limitations and obtain explicit release approval. No production signing keys are rotated or altered by this candidate.

No claim of completed penetration testing, device validation or production release is made.
