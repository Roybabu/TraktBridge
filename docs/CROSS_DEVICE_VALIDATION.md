# Cross-device beta validation (0.130.0)

## Setup
Install the same version on phone, tablet and Android TV. Select supported streaming apps and grant notification access. Start tracking on each device. Exchange signed QR invitations and independently compare the PIN on both devices. Enable cross-device playback sharing; history and incoming controls require separate consent.

## Physical-device acceptance checklist
- Verify discovery on Wi-Fi/Ethernet, then play a known movie or episode and compare title, provider, state and position on all devices.
- Pause, resume, change episode, disconnect Wi-Fi and stop tracking. Confirm observations expire and no metadata is shared when tracking is disabled.
- Test play/pause only for supported MediaSession actions. Confirm disabled controls and revoked devices cannot execute commands.
- Verify handoff on installed Netflix/YouTube apps, including TV package variants; unsupported apps require manual search and seek.
- Enable history on both devices; verify repeated exchange does not duplicate received sessions, overlapping local observations are hidden, separate episodes/rewatches remain distinct, and the local Trakt upload queue is unchanged.
- Revoke a device during active synchronization; verify it disappears and cannot reconnect without a fresh mutually approved invitation.
- Deploy the optional relay per CROSS_DEVICE_RELAY.md; repeat from separate networks, test invalid deployment secret, clock skew and network interruption.
- Test Android API 26 and 35, real Android TV navigation, and upgrades from schema 8 with existing approved uploads.

## Security boundaries and remaining work
Mutual TLS pins approved device keys. Invitations are short-lived, consumed locally once, and must be compared over a trusted channel. Revocation is local; it cannot erase information already received on another device. Remote history is separate, read-only, bounded to 100 recent sampled records per exchange and 90 days/5,000 received records. No remote upload approvals, video, or credentials are transferred. No global exactly-once Trakt guarantee for simultaneous independent approvals on disconnected devices. The relay is optional and self-hosted. Physical multi-device interoperability, independent security audit, live SQLite encryption, relay abuse/load testing and stable release approval remain outstanding.
