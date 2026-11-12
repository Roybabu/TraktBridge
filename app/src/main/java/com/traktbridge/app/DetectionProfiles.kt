package com.traktbridge.app

data class DetectionProfile(val label:String,val mediaWeight:Int=85,val notificationWeight:Int=70,val screenWeight:Int=65,val retentionMs:Long=30000,val screenTiming:Boolean=true)
object DetectionProfiles {
    private val fallback=DetectionProfile("Generic",85,65,65,20000,false)
    private val profiles=mapOf(
        "com.netflix.mediaclient" to DetectionProfile("Netflix",85,70,65,30000,true),
        "com.amazon.avod.thirdpartyclient" to DetectionProfile("Prime Video",85,70,65,30000,true),
        "in.startv.hotstar" to DetectionProfile("JioHotstar",85,65,65,20000,true),
        "com.crunchyroll.crunchyroid" to DetectionProfile("Crunchyroll",85,70,65,20000,true),
        "com.google.android.youtube" to DetectionProfile("YouTube",85,65,65,10000,false),
        "org.videolan.vlc" to DetectionProfile("VLC",90,65,65,15000,true),
        "com.mxtech.videoplayer.ad" to DetectionProfile("MX Player",90,65,65,15000,true),
        "com.mxtech.videoplayer.pro" to DetectionProfile("MX Player Pro",90,65,65,15000,true),
        "com.plexapp.android" to DetectionProfile("Plex",90,70,65,20000,true),
        "org.jellyfin.mobile" to DetectionProfile("Jellyfin",90,70,65,20000,true)
    )
    fun forPackage(pkg:String)=profiles[ProviderPackages.canonical(pkg)] ?: fallback
    fun validMetadata(value:String):Boolean=PlayerRules.validTitle(value) && value.trim().lowercase(java.util.Locale.ROOT) !in setOf("advertisement","ad","commercial break","sponsored","video player","netflix","prime video","youtube","crunchyroll","jiohotstar","unknown","now playing")
}
