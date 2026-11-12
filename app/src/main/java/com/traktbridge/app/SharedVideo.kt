package com.traktbridge.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever

object SharedVideo {
    fun read(context:Context,uri:Uri):SharedCandidate? {
        if(uri.scheme!="content") return null
        val name=runCatching {
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0).orEmpty() else "" }.orEmpty()
        }.getOrDefault("")
        val retriever=MediaMetadataRetriever()
        var title=""
        var runtime=0L
        try {
            retriever.setDataSource(context,uri)
            if(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)!="yes") return null
            title=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            runtime=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        } catch(_:Exception) {
            if(!name.matches(Regex("(?i).+\\.(mkv|mp4|avi|webm|mov|m4v|ts|3gp)$"))) return null
        } finally { runCatching { retriever.release() } }
        return SharedContent.fromFile(name,title,runtime)
    }
}
