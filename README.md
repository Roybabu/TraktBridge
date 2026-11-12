# TraktBridge

Initial opt-in Android media-session diagnostic prototype. Enable notification access, choose apps, enable sampling, play a video and return to refresh diagnostics. Latest sample only; no watch history, OAuth or Trakt uploads yet. Debug APK workflow under Actions. Debug signing is not intended for stable updates.

## Trakt sign-in (0.2.0)
Register the exact development callback `traktbridge://callback`. Connect Trakt in the app, authorize in the browser, then tap Check Trakt connection. OAuth uses PKCE S256 and state validation, with Android Keystore-encrypted token storage. Refresh occurs when checking an expiring token. Disconnect removes local credentials; revoke access separately in Trakt settings. Custom schemes are for development only; production requires verified HTTPS App Links. No watch history uploads yet.

TraktBridge uses Trakt but is not endorsed or certified by Trakt.

## Local viewing history (0.3.0)
Opt-in selected media sessions are sampled every five seconds. SQLite stores active and completed viewing records, title/subtitle, source package, start/end timestamps, sampled playing time and last known position/duration. Pauses do not count; sampling gaps over 12 seconds split records; pauses of two minutes end a record. Sessions under 15 seconds are dropped. On service restart, surviving active records are recovered as pending. Review/edit or delete individual records locally; no history is sent to Trakt. Unknown metadata remains unknown. The latest 100 records appear in the setup screen. Multiple simultaneous sessions may overlap; elapsed time is an estimate, not verified video attention or content completion. No accessibility or usage access in this version.

## Catalogue identification (0.4.0)
Use Identify on Trakt for a completed viewing record. Search movies or TV/anime using a user-editable title. Choose a movie or select a show, season and exact episode (including specials). Trakt IDs, show ID and episode coordinates are saved locally after explicit confirmation; previous history is preserved by the v1-to-v2 SQLite migration. Anime uses the same catalogue as TV/movies; absolute episode numbers and alternate naming may require manual selection. No automatic match or watch upload. Only user-triggered search text is transmitted to Trakt. Catalogue requests use the client ID, not account tokens.

## Review notifications (0.5.0)
Opt in to Viewing review notifications and grant Android notification permission. Completed pending records generate private notifications with detected title and sampled watch time. Review opens the exact record with a prefilled catalogue query; Dismiss reminder leaves the record intact. No background catalogue searches or Trakt writes. A local notified flag prevents repeated alerts; up to three pending records are notified per polling cycle from the latest 100 records. Existing records may generate reminders after opt-in. Android notification settings, listener availability and battery policies affect delivery. Database migration adds the notified flag without dropping records.

## Approved Trakt history uploads (0.6.0)
Match a completed record, tap Mark watched on Trakt and explicitly approve after verifying the account online. Only movies and exact episodes are uploaded with recorded end timestamps (UTC seconds). Partial watches stay local. SQLite stores queued/uncertain/blocked/synced states and approved account identity. WorkManager schedules network-constrained retries and periodic recovery. History is checked by item ID and exact timestamp before POST. Unknown POST outcomes are reconciled without automatically resending, since Trakt history writes are not idempotent; unresolved items require manual verification. Definitive rejected requests may be retried. Different accounts are blocked. Cross-device simultaneous requests can still race. No remote deletion or playback-progress upload. Refresh history to see status. Existing records migrate without data loss.

## Platform fallback (0.7.0)
Choose additional installed launcher apps or built-in video apps. Media-session tracking remains opt-in. Usage access is separately opt-in, and Review recent app activity queries only selected packages, from opt-in time up to the last 24 hours. Completed foreground intervals at least one minute long are offered for manual identification; intervals overlapping recorded sessions are suppressed. App time is never counted as playback time. Manual records retain unknown watch time and app-exit timestamp, require catalogue matching and separate explicit upload approval. Filename cleanup improves user-entered or exposed metadata searches. No AccessibilityService, screen capture, background catalogue queries, or provider-specific UI scraping is included. Media-session coverage remains dependent on each app. Usage events can be incomplete, especially for multi-window or picture-in-picture.

## Stable signed releases (0.8.0)
CI now runs JVM unit tests, Android lint and debug compilation. Signed releases require one permanent signing key, four GitHub Actions secrets, and a pinned public certificate fingerprint. See [release setup](docs/RELEASE.md). A version tag matching version.properties triggers validation, release signing, certificate verification and a GitHub Release with APK and SHA256SUMS.txt. Debug APKs are not suitable for in-place upgrades to the signed release. No key is generated or committed by CI.

## Playback diagnostics and fallback reminders (0.8.1)
Diagnostics now show listener access, last connection status, sample age, errors, current session count and last nonempty selected metadata. Reconnect playback listener requests a system rebind. Separately opt in to Usage Access and review notifications to receive a local possible-viewing reminder for completed selected-app foreground intervals of at least 30 seconds when no overlapping sampled playback exists. The notification leads to manual review, never creates a watch automatically, and does not infer titles or watch duration. This fallback depends on a connected listener, available usage events and notification permissions. Netflix metadata and device compatibility remain unverified.

## Diagnostic recorder (0.8.2)
Open diagnostic recorder and explicitly enable recording before reproducing a problem. Local bounded trace records listener events, sample counts, selected package names, metadata presence, playback state, timing, notification outcomes and HTTP status only. No titles, OAuth secrets, account identifiers, raw URLs or response bodies are recorded by the trace. Export text through Android document picker or share manually. App logcat is limited to the current process and diagnostic tag; full system logcat requires a separate user-generated Android bug report. Review any report before sharing. Disable recording and clear the trace after troubleshooting.

## Persistent tracking and experimental title suggestions (0.8.3)
Start persistent tracking from the app to keep a visible ongoing notification and request listener recovery when sampling stalls. Stop from the notification or app. OEM process policies can still interrupt tracking. Optional Accessibility title suggestions require separate informed consent and Android Settings approval; only selected video packages and recognized player title controls are inspected, with no screenshot or input collection. Suggestions are unverified, remain local and may be unavailable on protected or unsupported player interfaces. App activity is never treated as verified watch time; exact episode matching and upload remain explicit. Check diagnostic logs for sampling gaps and title candidate counts. Android 14+ special-use foreground service is intended for sideloaded testing; Play distribution would require policy review.

## Visual redesign (0.9.0)
Dark dashboard with Home, History, Review, Stats and Settings navigation. Matched movies and shows display cached artwork from Trakt extended metadata (no extra TMDB credential). Unmatched records have placeholders; artwork requests use confirmed Trakt IDs only and cache downloaded images locally. Stats reflect the latest 100 local records and exclude unverified usage time. Existing review and explicit upload workflows remain intact. Native Android views retained to minimize migration risk.

## Consolidated title history (0.9.1)
History, dashboard and statistics group all stored sessions by confirmed movie or exact episode Trakt ID across platforms. Unmatched sessions remain separate until identified to avoid mixing different episodes or remakes. Original session records are retained. Total sampled watch time sums observations; remaining time uses the latest known valid playback position and duration, never summed positions. Completion is confirmed by approved/synced history, not inferred from foreground duration. Duplicate completion uploads for the same confirmed title/account are rejected transactionally. Existing records are not deleted or rewritten. Rewatches remain visible within session details.

## Expanded player detection (0.9.2)
Shared player-control rules and app catalog cover major streaming and local players; any installed video app can be selected, including regional variants. Candidate IDs are heuristics, not verified adapters for every app version. MediaStyle notifications supplement missing MediaSession titles. Optional Player Inspector logs recognized resource IDs and counts only, not screen text or credentials. Protected or inaccessible titles remain unidentified; no screenshots or DRM bypass. Enable title detection and inspector explicitly, show playback controls during testing, and export diagnostics.

## Pre-playback identification (0.9.3)
Optional accessibility title suggestions recognize a unique video detail heading near a play action and retain a candidate within an active media session. Inspector reports structural resource IDs, node classes and counts without screen text. Invalid positions without credible runtime are excluded. Confirmed Trakt matches store catalogue runtime separately from player progress; remaining time stays unknown when position is unavailable. All suggestions require review.

## Full-player layout recognition (0.9.4)
Accessibility labels use normalized window coordinates across portrait and landscape top, bottom and side regions. Playback controls and seek bars establish player context, ambiguous titles are rejected, and explicit elapsed/negative remaining timer pairs can supply progress when media metadata lacks it. Timer observations expire and are tied to the candidate title. No screen capture or raw text logging.

## Shared content and local files (0.9.5)
Receive Android text/plain and video/* shares, paste title links or choose a local video through the system document picker. Parse supported provider IDs and shared titles locally; opaque links require manual title input. Read user-granted video metadata and normalize filenames with episode hints. Attach suggestions to a selected recent pending session or create an unverified identification record. No URL fetching, broad storage permission, playback inference from sharing, or automatic Trakt upload.

## Streaming history import (0.9.6)
Import Netflix viewing activity or generic CSV with Title,Date (ISO or US date) or Title,Start Time (ISO timestamp). Select the provider, preview valid rows and confirm import. Stable IDs prevent repeat-file duplicates while preserving repeated activity rows. Imported dates are not playback evidence; sampled time and completion remain unknown. Exact provider/title/day local candidates can be reconciled explicitly without double-counting. No account scraping or automatic upload.

## One-shot player OCR (0.9.7)
On Android 14+, explicitly arm one scan for a selected app. After an 8-second delay, a qualifying accessibility event within 45 seconds requests a window screenshot. Protected windows are rejected by Android. Bundled on-device Latin OCR processes edge regions in memory, then releases images. Review title candidates and optional timer estimates manually; no automatic history changes or upload. Clear results in settings. No continuous capture or DRM bypass.

## Direct media server integration (0.9.8)
Configure Plex, Jellyfin, Emby or Kodi using a trusted HTTPS server and explicit playback client selection. Poll session metadata using provider APIs; credentials are encrypted with Android Keystore, redirects are disabled, and server responses are bounded. Server sessions contribute title, episode, position, duration and sampled playback to existing local review. No automatic Trakt upload. Kodi requires HTTPS access to JSON-RPC, typically through a configured reverse proxy. Server playback may be remote; select only a client you own/use.

## Intelligent recognition and learned matches (0.9.9)
Fuse media-session, notification and accessibility title observations with heuristic evidence scores and conflict reporting. Rank Trakt search candidates by title, year, runtime and episode evidence. Confirmed matches teach provider-scoped exact title/episode and shared provider-ID mappings; optional automatic local reuse never approves a completed watch. Clear learned mappings independently of history. Scores are heuristic, not calibrated probabilities. No automatic network catalogue search.

## Playback accuracy (0.10.0)
Classify playing, paused, buffering, stopped and unknown observations. Exclude detected seeks, rewinds, stagnant media positions and sampling gaps from sampled viewing time; preserve last valid position with source. Enrich unidentified active sessions without splitting on missing labels, while distinct explicit episodes split. Display pause, buffering, uncertain intervals and seek counts. Near-end position is a suggestion, not completion approval. No ad identification or inference of exact remaining time from sampled watch time.

## Series library (0.11.0)
Group exact Trakt episode identities into parent shows and seasons while retaining original sessions. History separates shows, movies and unidentified viewing. Home includes unfinished sampled viewing; series cards show locally identified and synced episode counts. Explicit catalogue season lookup shows full episode lists, local completion and next unconfirmed regular episode. Specials remain separate. No assumptions about unaired episodes or remote history. Anime uses Trakt season/episode identity; absolute numbering conversion is not included.

## Trakt account library (0.12.0)
Browse paginated authenticated history, watchlist, ratings and saved playback progress. Download history pages into a separate account-scoped cache keyed by remote history event ID; local sampled playback and completion queues are unchanged. Display matching local sessions and account-level show progress. Explicitly confirm watchlist and rating mutations, and verify the connected account before sending. Existing approved history upload worker remains unchanged. Clear remote cache on disconnect; cached view requires current account verification. No automatic full-history refresh or remote-history deletion.

## Dashboard and analytics (0.13.0)
Adaptive sidebar navigation and two-column content cards on wide screens, compact navigation on phones. Home metrics, searchable history with provider/type/status/date filters and sorting, selectable analytics periods, playback-state metrics and interactive monthly activity calendar. Calendar and date filters attribute aggregate session time to the session end date; remote account history is excluded from measured time. Filter and calendar state survive activity recreation.

## Pre-1.0 validation (0.25.0)
Bound rendered history cards to increments of 100, retain filters and add release-readiness regression tests. See [device validation checklist](docs/DEVICE_VALIDATION.md) for physical-device cases and known limitations.

## Extended pre-1.0 milestones (0.26–0.39)
Local viewing statuses, history-seeded related recommendations, external Trakt lookup, public AniList title search, live inspector, manual logs, collections, spoiler controls, yearly journal, encrypted history backup, cached detail fallback, bounded account refresh, remote media output diagnostics, contrast and reading size. Version 0.39 bounds catalogue responses and cache growth, validates restored identities and positions, throttles learning, and tests encrypted backup authentication. No independent profiles, anime account sync, licensed availability feed, exact provider deep links, scheduled release reminders or full multilingual support. No physical-device validation completed.

## Milestones 0.40–0.57
Unified local and opt-in remote search; IMDb/TMDB identity lookup via Trakt; service analytics; manually entered INR subscription costs; four curated franchise guides; local notes and tags; time-budget planner and random first-page watchlist movie; actor/director movie credits; recoverable local deletion; health center; optional daily device-encrypted backup; public AniList lists; Letterboxd diary CSV; confirmation-safe Android shortcut URIs; Android TV launcher and focus support; text collection exchange; account-bound scheduled remote history refresh; bounded AniList response and safer restoration and match ranking. Physical-device and live account tests are still required.

Not implemented: licensed streaming availability, exact provider deep links, authenticated AniList/MAL writes, SIMKL sync, real-time collaborative lists, independent profiles, biometric lock, automatic franchise graphs, full TV companion or Home Assistant backend, release notification scheduler, rewatch uploads, general duplicate-record merge and multilingual UI. These milestones do not constitute v1.0 readiness.

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

## v0.82.0 development scope
Unified local library search groups Trakt movies and TV series with separately identified AniList local anime progress and unidentified sessions. IDs are never equated across catalogues. Browse results are capped at 200 per category. SQLite corruption handling fails closed rather than invoking default destructive deletion. Backup generation and merge require integrity checks; a failed database is preserved for manual recovery. Automatic reconstruction of a corrupt database, live device recovery and signed release validation remain outstanding.

## v0.83.0 development scope
Personalized recommendations combine up to five confirmed-history seeds using measured viewing time and recency, merge related catalogue results, exclude previously watched and hidden titles, and display recommendation reasons. Explicit consent precedes sending title IDs; feedback stays local. Partial network results remain usable and catalogue UI exceptions are handled. This is heuristic ranking, not a trained ML model. Live API and device validation remain pending.

## v0.84.0 development scope
Mood discovery offers six mood presets, movie/show selection, runtime and rating constraints, and excludes known watched or hidden titles. Genre associations are heuristic; runtime for TV is per episode when provided. Poster decoding validates dimensions and downsamples cached and downloaded images; background artwork work retains application context rather than activity context and skips detached targets. Physical-device memory profiling and live catalogue validation remain outstanding.

## v0.85.0 development scope
Interactive platform filters, monthly annual recap bars and calendar drill-downs are available. History dashboard queries and grouping run off the UI thread with cancellation and stale-result checks; database failures show a retry and integrity action. Annual totals exclude active sessions and duplicate IDs. Journal rendering is bounded; original history is unchanged. Physical-device startup benchmarks and end-to-end recovery testing remain outstanding. Version 0.85.0 is a release candidate subject to CI and device validation.

## v0.86.0 development scope
Regional where-to-watch lookup opens exact Trakt IDs or a user-selected country/title search on JustWatch; it does not assert live availability or subscription entitlement. Foreground startup failures and listener lifecycle cleanup are handled without deleting history. Device background-service soak testing and live provider validation remain pending.

## v0.87.0
Validated internal navigation shortcuts, exact Netflix/YouTube ID launch links and adaptive polling when inactive. Provider links require user action; no autoplay or availability guarantee.
