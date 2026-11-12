package com.traktbridge.app

object LibraryOrganization {
    fun tags(raw:String):List<String> = raw.split(',').map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() && it.length<=60 }.distinct().take(30)
    fun folder(raw:String):String? = raw.trim().takeIf { it.isNotBlank() && it.length<=80 && it.none { c -> c.isISOControl() } }
    fun search(groups:List<ViewingTitle>,query:String,tag:String,folderKeys:Set<String>?,tagsByKey:Map<String,List<String>>,sort:String):List<ViewingTitle> {
        val tokens=RecognitionEngine.normalized(query).split(' ').filter { it.isNotBlank() }.take(20)
        val selected=groups.filter { g ->
            val key=g.key
            val text=RecognitionEngine.normalized(g.sessions.joinToString(" ") { it.title+" "+it.subtitle+" "+it.matchLabel }+" "+tagsByKey[key].orEmpty().joinToString(" "))
            tokens.all { it in text } && (tag.isBlank() || tag in tagsByKey[key].orEmpty()) && (folderKeys==null || key in folderKeys)
        }
        return when(sort) {
            "remaining" -> selected.sortedBy { if(it.remainingMs>=0) it.remainingMs else Long.MAX_VALUE }
            "oldest" -> selected.sortedBy { it.latest.ended }
            else -> ViewingAnalytics.sort(selected,sort)
        }
    }
}
