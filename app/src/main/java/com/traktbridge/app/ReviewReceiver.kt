package com.traktbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ViewingNotifications.IGNORE) return
        val uri = intent.data ?: return
        if (uri.scheme != "traktbridge" || uri.host != "review") return
        val id = uri.lastPathSegment ?: return
        // Dismiss only the reminder; retain the viewing record for manual review.
        WatchHistory(context).use { it.markNotified(id) }
        ViewingNotifications.cancel(context,id)
    }
}
