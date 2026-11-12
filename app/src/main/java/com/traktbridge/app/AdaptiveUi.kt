package com.traktbridge.app

/** Window width, not physical hardware, drives the touch layout. TV is a separate input mode. */
object AdaptiveUi {
    enum class Mode { COMPACT, MEDIUM, EXPANDED, TV }
    data class Layout(val mode:Mode,val sidebar:Boolean,val rail:Int,val padding:Int,val columns:Int,val touch:Int)
    fun layout(width:Int,height:Int,fontScale:Float,tv:Boolean):Layout {
        val mode=when {
            tv -> Mode.TV
            width<600 || height<400 -> Mode.COMPACT
            width<840 -> Mode.MEDIUM
            else -> Mode.EXPANDED
        }
        val columns=if(fontScale>=1.5f) 1 else when(mode) {
            Mode.COMPACT->1; Mode.MEDIUM->2; Mode.EXPANDED->if(width>=1200) 3 else 2; Mode.TV->if(width>=1100) 3 else 2
        }
        return when(mode) {
            Mode.COMPACT->Layout(mode,false,0,16,columns,48)
            Mode.MEDIUM->Layout(mode,true,112,20,columns,48)
            Mode.EXPANDED->Layout(mode,true,176,24,columns,48)
            Mode.TV->Layout(mode,true,208,32,columns,64)
        }
    }
}
