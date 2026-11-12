# Pre-1.0 device validation

Run on a physical Android 16 tablet with the permanently signed APK. Do not declare these manual cases passed until observed.

- Install over v0.14.0 without uninstalling; verify existing history, preferences and connection.
- Rotate the tablet on all tabs, open settings, search and filter history, browse a matched series.
- Play a local VLC file for 60 seconds, pause, seek forward and backward, resume and stop. Verify sampled time and position remain separate.
- Play Netflix and Prime Video; compare metadata, accessibility hints and optional OCR. Secure-window rejection must be reported rather than bypassed.
- Review a wrong title and correct it; verify learned reuse is provider and episode scoped.
- Verify a near-end notification does not upload without account verification and confirmation.
- Import a duplicate CSV; export JSON and restore twice; verify no duplicate IDs and no restored upload approval.
- Test widget, Quick Settings start/stop, guided setup, favorites and update checker.
- Connect only a controlled media server and test disconnect, pause, buffering and client filtering.
- Test Trakt details, discovery, upcoming calendar, account history, watchlist and ratings with a test account.
- Test denied permissions, network interruption, background restriction and device restart.
- Confirm CI unit tests, lint, stable signing fingerprint and actual APK artifact.

Known gaps: no provider availability feed, exact streaming deep links, release reminder scheduler, independent profiles, personalized recommendation engine or automatic APK installer. Device test results are pending.

## Extended milestones
- Set and clear library status; verify no Trakt mutation.
- Test related recommendations, title links, AniList search, collections and manual logs.
- Verify spoiler toggle, journal share, encrypted backup round-trip, wrong password and corrupt file rejection.
- Test offline title details after caching, account refresh and switching accounts.
- Inspect remote media session output and picture-in-picture continuity; unsupported casting is not tracked.
- Test large text, contrast, screen reader and tablet rotation.

## Milestones 40–57
- Search locally, then explicitly search external catalogues and people.
- Compare IMDb/TMDB ID results against known titles and episodes.
- Verify subscription totals, notes, collection import collision behavior, trash restoration and spoiler settings.
- Import Letterboxd diary twice and confirm watched dates and no inferred playback time.
- Verify daily local backup and account-bound refresh under network restrictions, account switching and disconnect.
- Exercise shortcut URIs from Android, TV launcher and D-pad navigation on a physical TV or emulator.
- Confirm no remote write without user approval. Verify source version, CI artifact and permanent signing.
- Device tests not yet executed; do not represent this checklist as passed.

## v0.58–0.77 validation and scope
- Hybrid Compose header and Home/Library summaries; existing screens retain Android Views. Full Compose migration is not complete.
- Now Playing shows reported sessions only; no player control or inferred title certainty.
- Local assistant uses rule matching; rewatch uploads require explicit approval and event/account validation.
- Collection profiles separate organization only, not playback history or Trakt account. No new external account integrations or cloud backend.
- Episode reminders are approximate WorkManager tasks. Privacy mode stops monitoring; screenshot protection is optional, not biometric authentication.
- Settings export includes selected default-profile preferences. No credentials, permissions, approvals, learned mappings, or all-profile export.
- Test upgrade from 0.57 with existing history, Trakt authorization, title mappings and signed APK.
- Verify Compose header and home/library layout with rotation, large text, TalkBack and Android TV navigation.
- Test rewatch approval twice, same timestamp duplicates, uncertain uploads, stale match edits and account switching; no automatic upload.
- Test reminder permission denial, cancel, time change, delayed delivery and disconnect.
- Test privacy pause during one-shot OCR, rearm/clear during processing, process death during active session, backward wall clock.
- Validate password backup authentication, restore synced/uncertain events as non-uploadable audit records, corrupted settings import, manual transfer and unmetered artwork behavior.
- Physical-device, 24/72-hour soak, startup and memory benchmarks and security audit remain pending; not a stable release candidate until verified.
