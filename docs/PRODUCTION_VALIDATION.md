# Production reliability validation

## Automated
- Phone API 26/35, wide-screen tablet API 35 and TV API 34 instrumentation profiles.
- Simulated 72-hour policy loop exercises active polling cadence, bounded artwork budget and stale observation expiry. This is a unit test, not a physical-device endurance test.
- Security regression checks cover command allowlists, duplicate reconciliation and database migration readiness.

## Required real-device evidence (not yet measured)
Record device, OS build, application commit and signing fingerprint. Test cold/warm launch with adb am start -W; use dumpsys meminfo during long scrolling and background playback; collect frame timing with dumpsys gfxinfo. Measure 24/72 hours of actual playback and idle transitions, Wi-Fi changes, power saving and restart. Test TV remote-only navigation and commercial providers individually. Do not include titles, credentials or account identifiers in shared diagnostics.

## Release gates
Zero unauthorized Trakt writes, no known critical defects, correct signed upgrade preserving history, all workflows passing, physical compatibility evidence, independent security review and live encryption migration assessment. No performance threshold or certification is claimed until measured.
