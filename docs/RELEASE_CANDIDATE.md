# TraktBridge v0.170.0 — pre-stable release candidate

Version code 182. Source checkpoint only; no stable tag, release or new signing configuration.

## Exact-commit gates
- Android CI (unit tests, lint, APK and permanent signing verification).
- Dependency advisory audit.
- Phone, tablet and TV emulator instrumentation.
- Physical signed upgrade, playback, remote navigation, pairing, permissions and 24/72-hour soak.
- Security review and measured startup/memory/frame performance.

## Known blockers
- Application-level live SQLite encryption is not implemented. Secure deletion and encrypted backup are not equivalent.
- Complete Compose migration and live camera QR scanning are unfinished.
- TV/streaming provider behavior, background OEM restrictions, network handoff and simultaneous disconnected uploads require real-device verification; no distributed exactly-once guarantee.
- Independent audit and measured performance are outstanding.
- Stable release requires explicit user approval after all required gates.

See PRODUCTION_VALIDATION.md, DATABASE_ENCRYPTION.md, PLAYBACK_RELIABILITY_VALIDATION.md and COMPOSE_MIGRATION.md. Do not describe pending or failed CI as passed.
