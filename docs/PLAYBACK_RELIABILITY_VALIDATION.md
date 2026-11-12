# Playback reliability validation (0.160.0)

## Automated coverage
Device profile selection, exact provider aliases, foreground recovery consent, idle-only polling, immutable history conflict decisions, handoff identity/freshness, nearby Trakt reconciliation and permission readiness. Instrumentation checks Android permission diagnostics and activity recreation.

CI includes phone API 26/35, resized tablet API 35 and Android TV API 34 system image. TV runner availability and results must be verified. Tests do not simulate commercial streaming app playback.

## Physical matrix (not yet verified)
- Phone and tablet: Netflix, Prime Video, YouTube, VLC, Jellyfin; select each explicitly and compare metadata with actual playback.
- TV: Netflix, Prime Video, YouTube, Jellyfin and Emby installed variants, remote navigation, notification listener availability, permission diagnostics.
- Start, pause, seek, change episodes, stop; ensure ad or stale evidence does not inflate history.
- Stop tracking, privacy lock, revoke permissions, process restart, background restriction, power saver and OEM battery settings.
- Change Wi-Fi, Ethernet and offline/online; verify rediscovery, cached history, stale session expiration and revoked devices.
- Handoff between devices; verify expired and changed sessions are rejected, manual resume where provider links are unavailable.
- Review nearby completion conflicts, uncertain uploads and separate rewatches using a test Trakt account. Confirm no automatic approval and no resend of ambiguous POST.
- Verify upgrade preserves history and signing, run 24/72-hour physical soak and collect privacy-safe timings.

## Limits
Android/OEM permissions and app metadata govern detection. No background restart after force-stop or guaranteed boot launch. Offline history is read-only; conflict policy preserves the first received record. No distributed exactly-once guarantee for concurrent Trakt POSTs. Dedicated TV emulator and actual device acceptance are separate gates.
