package com.traktbridge.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View

object TvFocus {
    fun background(context:Context,base:Int,accent:Int,selected:Boolean=false):StateListDrawable {
        val density=context.resources.displayMetrics.density
        fun shape(color:Int,stroke:Int,width:Int)=GradientDrawable().apply {
            setColor(color); cornerRadius=12*density
            if(width>0) setStroke((width*density).toInt().coerceAtLeast(1),stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),shape(base,accent,3))
            addState(intArrayOf(android.R.attr.state_pressed),shape(base,accent,2))
            addState(intArrayOf(),shape(base,accent,if(selected) 1 else 0))
        }
    }
    fun firstContent(root:View):Boolean {
        val candidates=ArrayList<View>()
        root.addFocusables(candidates,View.FOCUS_FORWARD)
        return candidates.firstOrNull { it!==root && it.isShown && it.isEnabled }?.requestFocus() ?: false
    }
}
