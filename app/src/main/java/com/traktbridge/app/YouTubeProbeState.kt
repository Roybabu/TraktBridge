package com.traktbridge.app

/** Local, explicitly armed, bounded diagnostic. Titles are kept only in memory. */
object YouTubeProbeState {
    private const val WINDOW=120000L
    private var deadline=0L
    private var started=0L
    private var media:List<String> = emptyList()
    private var notifications:List<String> = emptyList()
    private var screen:String?=null
    private var screenAt=0L
    private var mediaAt=0L
    private var mediaCount=0
    private var screenSeen=false
    private var ids:List<String> = emptyList()
    @Synchronized fun start(now:Long) {
        deadline=now+WINDOW; started=now; media=emptyList(); notifications=emptyList(); screen=null; screenAt=0; mediaAt=0; mediaCount=0; screenSeen=false; ids=emptyList()
    }
    @Synchronized fun active(now:Long)=now in started..deadline && deadline>0
    @Synchronized fun clear() { deadline=0; started=0; media=emptyList(); notifications=emptyList(); screen=null; screenAt=0; mediaAt=0; mediaCount=0; screenSeen=false; ids=emptyList() }
    @Synchronized fun observeMedia(now:Long,sessionTitles:List<String>,notificationTitles:List<String>,sessions:Int) {
        if(!active(now)) return
        media=sessionTitles.mapNotNull(YouTubeTitleProbe::clean).distinct()
        notifications=notificationTitles.mapNotNull(YouTubeTitleProbe::clean).distinct()
        mediaCount=sessions; mediaAt=now
    }
    @Synchronized fun observeScreen(now:Long,labels:List<ScreenLabel>,player:Boolean) {
        if(!active(now)) return
        screenSeen=true
        screen=YouTubeTitleProbe.screen(labels,player)
        screenAt=now
        ids=labels.map { it.id }.filter { it.isNotBlank() }.distinct().take(60)
    }
    @Synchronized fun report(now:Long):String {
        if(started==0L) return "Start a test, then play a video in YouTube."
        val a=screen?.takeIf { screenAt>0 && now-screenAt in 0..30000 }
        val fresh=mediaAt>0 && now-mediaAt in 0..30000
        val m=if(fresh) media else emptyList()
        val n=if(fresh) notifications else emptyList()
        val result=YouTubeTitleProbe.compare(m,n,a)
        fun age(at:Long)=if(at==0L) "not observed" else ((now-at).coerceAtLeast(0)/1000).toString()+"s ago"
        return listOf(
            if(active(now)) "Test running" else "Test ended",
            "MediaSession: "+m.joinToString(" | ").ifBlank { "No fresh title" },
            "YouTube media sessions: "+mediaCount+" (sample "+age(mediaAt)+")",
            "Media notification: "+n.joinToString(" | ").ifBlank { "No fresh title" },
            "Accessibility: "+(a ?: if(screenSeen) "No fresh, unambiguous player title" else "No YouTube screen observed")+" ("+age(screenAt)+")",
            "Candidate: "+(result.title ?: "Not identified"),
            "Source: "+result.source,
            "Visible control IDs: "+ids.joinToString(", ").ifBlank { "None" },
            "Diagnostic only. Confirm the candidate against the actual playing video."
        ).joinToString("\n\n")
    }
}
