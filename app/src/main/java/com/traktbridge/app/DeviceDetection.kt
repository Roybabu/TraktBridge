package com.traktbridge.app

/** Screen and notification evidence on TV is less reliable without explicit player controls. */
object DeviceDetection {
    enum class Kind { PHONE,TABLET,TV }
    fun kind(tv:Boolean,smallestWidth:Int)=when {
        tv->Kind.TV
        smallestWidth>=600->Kind.TABLET
        else->Kind.PHONE
    }
    fun profile(base:DetectionProfile,kind:Kind)=when(kind) {
        Kind.TV->base.copy(notificationWeight=minOf(base.notificationWeight,60),retentionMs=minOf(base.retentionMs,20000),screenTiming=false)
        else->base
    }
    fun notificationAge(profile:DetectionProfile,kind:Kind)=if(kind==Kind.TV) minOf(15000L,profile.retentionMs) else 120000L
}
