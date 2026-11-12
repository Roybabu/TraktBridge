package com.traktbridge.app

object TvNavigation {
    fun next(index:Int,direction:Int,count:Int):Int = if(count<=0) 0 else (index+direction).coerceIn(0,count-1)
    fun validCache(saved:Long,now:Long):Boolean = saved>0 && now>=saved && now-saved<=7*86400000L
}
