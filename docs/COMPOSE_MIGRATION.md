# Compose migration inventory (0.150.0)

Migrated: header, phone/tablet navigation, home summaries, TV home and quick settings, library/review toolbars, title summary, loading and empty states, live playback, device center, mirrored history, viewing title cards and individual session cards, tablet master-detail, TV library and discovery shelves.

Shared AndroidViewModel and read-only viewing repository provide lifecycle-aware asynchronous loading. Existing local review, account verification, explicit upload approval, cancellation and uncertain-upload reconciliation remain unchanged.

Remaining native Android Views: activity shell, full settings list and switches, analytics/calendar, series and catalogue secondary cards, account and approval dialogs, QR and Android system integrations. These are retained to avoid removing existing features or consent boundaries. Full Compose migration is **not complete**. TV browser login remains dependent on an available browser; QR camera capture uses the system camera thumbnail, not a continuous scanner.

Validation: phone/tablet emulator and physical TV focus, camera, split-screen, upgrade and account approval checks remain required. No stable release approval or signing change.
