package com.traktbridge.app

/** Experimental detector. Do not infer watching from the foreground app or browsing. */
object HotstarPlayback {
    const val PACKAGE="in.startv.hotstar"
    fun clean(value:String):String? {
        val text=value.trim()
        if(!DetectionProfiles.validMetadata(text) || text.contains('\n') || text.contains('\r')) return null
        val normalized=text.lowercase(java.util.Locale.ROOT)
        if(normalized in setOf("hotstar","jio hotstar","disney+ hotstar","live","sports","watch live","player","home","search","my space","premium","subscribe","continue watching")) return null
        if(Regex("(?i)\\b(advertisement|commercial break|skip ad|live stream|live cricket|highlights|trailer|teaser)\\b").containsMatchIn(text)) return null
        return text
    }
    fun recognize(title:String,display:String,detail:String,duration:Long,position:Long):Recognition? {
        if(duration !in 120000L..21600000L || !PlaybackPosition.valid(position,duration)) return null
        if(SegmentDetection.metadata(title)==PlaybackSegment.ADVERTISEMENT || SegmentDetection.metadata(display)==PlaybackSegment.ADVERTISEMENT) return null
        val primary=clean(title) ?: clean(display) ?: return null
        val secondary=clean(display)
        // Conflicting title fields are diagnostic evidence, not a verified viewing identity.
        if(clean(title)!=null && secondary!=null && RecognitionEngine.normalized(primary)!=RecognitionEngine.normalized(secondary)) return null
        val subtitle=clean(detail).orEmpty()
        if(RecognitionEngine.ambiguousEpisode(primary+" "+subtitle)) return null
        return Recognition(primary,subtitle,85,listOf("media"),false)
    }
}
