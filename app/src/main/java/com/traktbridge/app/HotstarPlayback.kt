package com.traktbridge.app

/** Catalogue-backed detector. Do not infer watching from the foreground app or browsing. */
object HotstarPlayback {
    const val PACKAGE="in.startv.hotstar"
    data class Identity(val series:String,val episode:String)
    private fun hash(value:String)=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun catalogueIdentity(entry:HotstarPublicCatalogue.Entry?,title:String):Identity? {
        entry ?: return null
        val ep=RecognitionEngine.episodeOf(title)
        if(ep==null && entry.kind!="movie" || ep!=null && entry.kind!="tv") return null
        return Identity("hotstar-series:"+hash(entry.id),"hotstar-episode:"+hash(entry.id+":"+(ep?.season ?: -1)+":"+(ep?.episode ?: -1)))
    }
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
