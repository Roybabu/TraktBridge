package com.traktbridge.app

object UiAccessibility {
    fun readingScale(setting:Int,tv:Boolean):Float=(when(setting) { 1->1.15f; 2->1.3f; else->1f })*(if(tv) 1.2f else 1f)
    fun animate(reduceMotion:Boolean,systemScale:Float)=!reduceMotion && systemScale>0f
}
