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
