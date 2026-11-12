package com.traktbridge.app

import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/** Uses platform haptic settings; no vibration permission, no background vibration. */
object BridgeHaptics {
    const val PREF="haptic_feedback"
    fun allowed(enabled:Boolean,tv:Boolean,systemEnabled:Boolean)=enabled && !tv && systemEnabled
    fun tap(view:View) {
        val context=view.context
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val tv=context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        if(allowed(prefs.getBoolean(PREF,true),tv,view.isHapticFeedbackEnabled)) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
@Composable
fun hapticClick(action:()->Unit):()->Unit {
    val view=LocalView.current
    return { BridgeHaptics.tap(view); action() }
}
