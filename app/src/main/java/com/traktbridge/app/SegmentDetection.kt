package com.traktbridge.app

enum class PlaybackSegment { CONTENT, INTRO, RECAP, CREDITS, ADVERTISEMENT, UNKNOWN }
object SegmentDetection {
    private fun normalize(value:String)=value.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
    fun metadata(title:String):PlaybackSegment=when(normalize(title)) {
        "advertisement","ad","commercial break","sponsored" -> PlaybackSegment.ADVERTISEMENT
        else -> PlaybackSegment.UNKNOWN
    }
    fun screen(labels:List<ScreenLabel>,player:Boolean):PlaybackSegment {
        if(!player) return PlaybackSegment.UNKNOWN
        val cues=labels.filter { it.button || it.id in setOf("ad_label","ad_badge","skip_ad_button") }.map { normalize(it.text) }.mapNotNull {
            when(it) {
                "ad","advertisement","skip ad","skip ads","commercial break" -> PlaybackSegment.ADVERTISEMENT
                "skip intro","skip opening" -> PlaybackSegment.INTRO
                "skip recap","skip previously on" -> PlaybackSegment.RECAP
                "skip credits","skip ending" -> PlaybackSegment.CREDITS
                else -> null
            }
        }.distinct()
        return cues.singleOrNull() ?: PlaybackSegment.UNKNOWN
    }
    fun excluded(segment:PlaybackSegment)=segment in setOf(PlaybackSegment.ADVERTISEMENT,PlaybackSegment.INTRO,PlaybackSegment.RECAP,PlaybackSegment.CREDITS)
}
