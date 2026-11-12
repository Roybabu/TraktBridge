package com.traktbridge.app

object ConcurrentPlayback {
    private fun identity(s:WatchSample):String? {
        if(!DetectionProfiles.validMetadata(s.title) || RecognitionEngine.ambiguousEpisode(s.title+" "+s.subtitle)) return null
        val title=RecognitionEngine.normalized(RecognitionEngine.query(s.title))
        if(title.isBlank()) return null
        val ep=RecognitionEngine.episodeOf(s.title+" "+s.subtitle)
        return title+"|"+(ep?.let { "S"+it.season+"E"+it.episode } ?: s.subtitle.trim().lowercase(java.util.Locale.ROOT))
    }
    fun select(samples:List<WatchSample>):List<WatchSample> {
        return samples.groupBy { it.source }.values.flatMap { group ->
            val playing=group.filter { it.playing }
            if(playing.size<=1) group else {
                val identities=playing.map { identity(it) }
                if(identities.any { it==null } || identities.distinct().size!=playing.size || playing.map { it.key }.distinct().size!=playing.size) emptyList()
                else playing.filter { PlaybackPosition.valid(it.position,it.duration) && it.positionSource=="media" }
            }
        }
    }
    fun outputLabel(remote:Boolean?,server:Boolean=false):String=if(server) "Server-reported device" else when(remote) {
        true -> "Remote / cast output"
        false -> "Local Android output"
        null -> "Output unknown"
    }
}
