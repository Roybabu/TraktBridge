# TraktBridge v0.120.0 release candidate

Version code 132. This is an extended pre-stable release candidate, not v1.0.0.

## Required gates for the exact candidate commit

- Android CI: unit tests, lint, APK assembly and permanent debug signing certificate verification.
- Dependency audit: resolved debug/release Maven runtime coordinates and OSV advisory scan.
- Android device tests: API 26 and 35 emulator instrumentation.
- Review manifest permissions, exported components, backup and cleartext network rules.
- Verify upgrade from the existing signed APK and retain viewing history.

## Stable-release blockers

- Full live SQLite encryption and verified non-destructive migration are not implemented.
- Actual physical-device, Android TV, streaming-provider compatibility and endurance validation are outstanding.
- Independent security review and performance measurements remain outstanding.
- Complete Compose migration and multilingual coverage remain outstanding.
- Standalone Wear OS transport, collaborative cross-device synchronization and full provider integrations remain outstanding.
- Native service import compatibility and anime absolute-number mapping need verification.
- Resolve all failed workflows and vulnerability findings; do not bypass checks.
- Record explicit release approval before creating a stable version or production tag.

No completed physical-device testing, security certification or production release is asserted. Existing APK signing configuration remains unchanged.
