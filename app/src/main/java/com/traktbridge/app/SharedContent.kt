package com.traktbridge.app

import java.net.URI
import java.util.Locale

data class SharedCandidate(val title:String,val detail:String="",val source:String="",val runtime:Long=0L,val providerId:String="")
object SharedContent {
    private val providers=mapOf("netflix.com" to "com.netflix.mediaclient","primevideo.com" to "com.amazon.avod.thirdpartyclient","amazon.com" to "com.amazon.avod.thirdpartyclient","amazon.in" to "com.amazon.avod.thirdpartyclient","hotstar.com" to "in.startv.hotstar","jiohotstar.com" to "in.startv.hotstar","sonyliv.com" to "com.sonyliv","zee5.com" to "com.graymatrix.did","mxplayer.in" to "com.mxtech.videoplayer.ad","crunchyroll.com" to "com.crunchyroll.crunchyroid","max.com" to "com.wbd.stream","hbomax.com" to "com.wbd.stream","discoveryplus.com" to "com.discovery.discoveryplus.mobile","disneyplus.com" to "com.disney.disneyplus","tv.apple.com" to "com.apple.atve.androidtv.appletv","youtube.com" to "com.google.android.youtube","youtu.be" to "com.google.android.youtube")
    private val urls=Regex("https?://[^\\s<>]+",RegexOption.IGNORE_CASE)
    private val episode=Regex("(?i)\\bS(\\d{1,3})[ ._-]*E(\\d{1,4})\\b")
    private val boilerplate=Regex("(?i)^(watch|stream|check out|i(?:'m| am) watching)\\s+|\\s+(on|with)\\s+(netflix|prime video|amazon prime video|disney\\+|jiohotstar|hotstar|sonyliv|zee5|mx player|crunchyroll|hbo max|discovery\\+|youtube)[.!]?$")
    fun fromText(text:String,subject:String=""):SharedCandidate? {
        val limited=text.take(12000)
        val url=urls.find(limited)?.value?.trimEnd('.',',',')',']')
        val uri=url?.let { runCatching { URI(it) }.getOrNull() }
        val host=uri?.host?.lowercase(Locale.ROOT).orEmpty()
        val provider=providers.entries.firstOrNull { host==it.key || host.endsWith("."+it.key) }
        val path=uri?.path.orEmpty().split('/').filter { it.isNotBlank() }
        val id=when(provider?.key) {
            "netflix.com" -> path.zipWithNext().firstOrNull { it.first in setOf("title","watch") && it.second.matches(Regex("[0-9]{5,12}")) }?.second.orEmpty()
            "primevideo.com","amazon.com","amazon.in" -> path.firstOrNull { it.matches(Regex("(?i)(amzn1\\.dv\\.gti\\.[a-z0-9-]+|B[0-9A-Z]{9})")) }.orEmpty()
            else -> ""
        }
        val lines=(listOf(subject)+urls.replace(limited,"").lines()).map { boilerplate.replace(it.trim().trim('"','“','”'),"").trim() }
        val title=lines.firstOrNull { ContentRecognition.usable(it) && !it.contains("http",true) && !it.startsWith("https",true) && it.length<=100 }.orEmpty()
        // Never treat opaque provider identifiers as actual movie titles.
        if(title.isBlank() && provider==null) return null
        val detail=episode.find(title)?.value.orEmpty()
        return SharedCandidate(title,detail,provider?.value.orEmpty(),providerId=id)
    }
    fun fromFile(name:String,metadataTitle:String="",runtime:Long=0):SharedCandidate? {
        val leaf=name.substringAfterLast('/').substringAfterLast('\\').take(200)
        val candidate=metadataTitle.takeIf { ContentRecognition.usable(it) } ?: leaf
        if(candidate.isBlank()) return null
        val match=episode.find(leaf) ?: episode.find(candidate)
        val detail=match?.let { "S"+it.groupValues[1]+" E"+it.groupValues[2] }.orEmpty()
        val title=TitleNormalizer.suggestedQuery(candidate).take(160)
        if(!ContentRecognition.usable(title)) return null
        return SharedCandidate(title,detail,"local.file",runtime.takeIf { it in 1000L..86400000L } ?: 0)
    }
}
