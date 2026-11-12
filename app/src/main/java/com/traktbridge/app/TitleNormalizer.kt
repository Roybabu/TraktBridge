package com.traktbridge.app

object TitleNormalizer {
    fun suggestedQuery(title: String): String {
        val prepared = title.replace(Regex("(?i)\\.(mkv|mp4|avi|webm|mov|m4v)$"),"").replace(Regex("(?i)\\b(1080p|720p|2160p|480p|x264|x265|h264|h265|hevc|webrip|bluray|web-dl)\\b.*$"),"").replace('.',' ').replace('_',' ')
        val cleaned = prepared.replace(Regex("(?i)\\b\\d{1,3}x\\d{1,4}\\b.*$"), "").replace(Regex("(?i)\\bS\\d{1,3}\\s*E\\d{1,4}\\b.*$"), "").replace(Regex("(?i)\\bSeason\\s+\\d+.*$"), "").replace(Regex("(?i)\\bEpisode\\s+\\d+.*$"), "").trim(' ', '-', ':', '.', '_')
        return cleaned.ifBlank { title.trim() }
    }
}
