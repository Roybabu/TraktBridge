package com.traktbridge.app
object AppearancePolicy {
    fun mode(value:String):String=value.takeIf { it in setOf("dark","amoled","dynamic") } ?: "dark"
    fun columns(width:Int,fontScale:Float,tv:Boolean):Int = when {
        fontScale>=1.5f -> 1
        width>=1200 && !tv -> 3
        tv || width>=720 -> 2
        else -> 1
    }
}
