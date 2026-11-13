package com.traktbridge.app

import android.content.Context

/** One-time preference migration; never deletes viewing records or grants new permissions. */
object PlaybackTrackingMigration {
    fun apply(context:Context) {
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        if(prefs.getBoolean("youtube_only_reset_v1",false)) return
        val edit=prefs.edit().putStringSet("allowed",PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty()))
            .remove("usage_since").remove("sessions").remove("last_nonempty_sessions")
            .putInt("session_count",0).putInt("tracked_session_count",0)
            .putBoolean("youtube_only_reset_v1",true)
        prefs.all.keys.filter { key -> listOf("ocr_","title_hint:","title_detail:","title_time:","screen_position:","screen_duration:","screen_time:","screen_title:","segment:","segment_time:","usage_reminded:").any { key.startsWith(it) } }.forEach { edit.remove(it) }
        edit.apply()
    }
}
