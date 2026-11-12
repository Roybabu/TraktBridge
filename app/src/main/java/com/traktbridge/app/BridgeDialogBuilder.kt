package com.traktbridge.app

import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Platform dialogs have a separate window tree, unlike views in ComponentActivity.
 * Install owners before Dialog.show() attaches any ComposeView (including nested
 * views in ScrollViews or content added after the dialog has opened).
 * Compose views in dialogs use DisposeOnDetachedFromWindow for dismissal cleanup.
 */
class BridgeDialogBuilder(private val host:ComponentActivity):AlertDialog.Builder(host) {
    override fun create():AlertDialog = super.create().also { dialog ->
        val decor=requireNotNull(dialog.window).decorView
        decor.setViewTreeLifecycleOwner(host)
        decor.setViewTreeViewModelStoreOwner(host)
        decor.setViewTreeSavedStateRegistryOwner(host)
    }

    override fun show():AlertDialog = create().also { it.show() }
}
