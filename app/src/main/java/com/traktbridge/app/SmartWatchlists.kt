package com.traktbridge.app

data class SmartWatchlist(val name:String,val rule:String,val type:String="",val source:String="")
object SmartWatchlists {
    val rules=listOf("unfinished","short","review","rewatch","recent","completed")
    fun select(groups:List<ViewingTitle>,list:SmartWatchlist,now:Long):List<ViewingTitle> {
        if(list.rule !in rules) return emptyList()
        return groups.filter { g ->
            (list.type.isBlank() || g.representative.matchType==list.type) &&
            (list.source.isBlank() || list.source in g.platforms) &&
            when(list.rule) {
                "unfinished" -> !g.confirmed && !g.queued && g.positionMs>=0 && g.remainingMs>0
                "short" -> !g.confirmed && g.remainingMs in 1..5400000L
                "review" -> g.pending && !g.active
                "rewatch" -> g.possibleRewatchIds.isNotEmpty() || g.sessions.count { it.status=="synced" }>1
                "recent" -> now-g.latest.ended in 0..604800000L
                "completed" -> g.confirmed
                else -> false
            }
        }.sortedByDescending { it.latest.ended }
    }
}
