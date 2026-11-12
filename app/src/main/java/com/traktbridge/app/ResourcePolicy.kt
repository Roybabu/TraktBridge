package com.traktbridge.app
object ResourcePolicy {
    const val ARTWORK_BYTES=32L*1024*1024
    const val ARTWORK_FILES=100
    fun evictions(files:List<Pair<String,Long>>):Set<String> {
        var bytes=0L; var count=0
        return files.filter { (_,size) ->
            if(size<0 || size>ARTWORK_BYTES || count>=ARTWORK_FILES || bytes+size>ARTWORK_BYTES) true
            else { bytes+=size; count++; false }
        }.map { it.first }.toSet()
    }
    fun nextPage(current:Int)= (current.coerceIn(100,900)+100)
    fun pollDelay(enabled:Boolean,playing:Boolean,failures:Int,powerSave:Boolean,restricted:Boolean=false):Long {
        val base=BackgroundPolicy.delay(enabled,playing,failures)
        return if(enabled && failures==0) maxOf(base,BatteryCompatibility.idleDelay(playing,powerSave,restricted)) else base
    }
}
