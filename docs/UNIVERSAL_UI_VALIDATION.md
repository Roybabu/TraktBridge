# Universal UI validation (0.140.0)

One APK, shared tracking/history/Trakt authorization, adaptive UI based on available window width and TV input mode.

| Mode | Width | Navigation |
|---|---|---|
| Compact | <600dp or height <400dp | Bottom bar |
| Medium | 600–839dp | Compact rail |
| Expanded | ≥840dp | Sidebar and two-pane home |
| TV | Android television UI mode | D-pad sidebar and TV home |

## Automated
Unit tests cover window breakpoints, short landscape windows, large fonts, reading scale and reduced motion. Instrumentation covers shell launch/recreation and focused drawable state. Device workflows cover API 26 and 35 plus a wide-screen configuration. CI completion is required before claiming a passing build.

## Physical acceptance
- Phone: portrait, landscape, large fonts, touch targets and QR image import.
- Tablet: portrait/landscape, split-screen resize, grid and two-pane home, device center.
- Android TV: launcher, remote-only focus path through every screen and dialog, keyboard/PIN, playback metadata and handoff.
- Cross-device: stale sessions, offline/online devices, history opt-in, revocation, approved-only Trakt uploads.
- Measure startup and long-list scrolling on real hardware; no measured performance benchmark claimed.

## Remaining
Full Compose migration (legacy catalogue/history cards, settings, analytics and dialogs), camera-based QR scanning, complete tablet master/detail, TV-specific versions of every secondary screen, localization, physical device accessibility and performance validation. No stable release approval or signing change.
