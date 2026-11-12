package com.traktbridge.app

object MemoryPolicy {
    fun artworkKiB(memoryClass:Int,lowRam:Boolean):Int=if(lowRam) 2048 else (memoryClass*1024/16).coerceIn(2048,16384)
    fun pageSize(lowRam:Boolean)=if(lowRam) 40 else 100
    fun nextPage(current:Int,step:Int)= (current+step.coerceIn(20,100)).coerceAtMost(1000)
}
