# v0.173.0 dialog lifecycle hotfix

Physical-device crash report: IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.AlertDialogLayout when a ComposeView attaches to the platform dialog window.

All MainActivity platform AlertDialog builders now use BridgeDialogBuilder, which installs the activity lifecycle, ViewModel store, and saved-state owners on the dialog decor before attachment. Both create-then-show and direct show paths are covered; nested ComposeViews inherit the window owners. Existing consent callbacks, dialog content, and detach disposal are unchanged.

Instrumentation exercises direct and nested composition, dismissal disposal, and real Now Playing, Connected devices, and Cross-device history dialogs. CI and physical device confirmation are required before declaring the crash resolved. No database schema, application ID, signing, tracking or upload approval changes.
