package com.traktbridge.app
import android.content.Context
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

object DeviceControl {
    @Volatile internal var dispatch: ((String,String)->Boolean)?=null
    private val last=mutableMapOf<String,Long>()
    fun enabled(context:Context)=PlaybackSharing.enabled(context) && context.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("device_controls",false)
    fun mask(actions:Long):Int=(if(actions and PlaybackState.ACTION_PLAY != 0L || actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) 1 else 0) or (if(actions and PlaybackState.ACTION_PAUSE != 0L || actions and PlaybackState.ACTION_PLAY_PAUSE != 0L) 2 else 0)
    fun valid(session:String)=session.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
    fun permits(mask:Int,action:String)=when(action) { "play"->mask and 1 != 0; "pause"->mask and 2 != 0; else->false }
    fun execute(context:Context,peer:String,session:String,action:String):Boolean {
        if(!valid(session) || !permits(3,action) || !enabled(context)) return false
        val deadline=SystemClock.elapsedRealtime()+1200
        val task=FutureTask<Boolean> {
            val now=SystemClock.elapsedRealtime()
            if(now>deadline || !enabled(context) || !PlaybackSharing.localWifi(context) || DevicePairing.peers(context).none { it.id==peer }) false
            else if(now-(last[peer] ?: -10000L)<1000) false
            else { last.keys.retainAll(DevicePairing.peers(context).map { it.id }.toSet()); last[peer]=now; dispatch?.invoke(session,action)==true }
        }
        Handler(Looper.getMainLooper()).post(task)
        return try { task.get(1500,TimeUnit.MILLISECONDS) } catch(_:Exception) { task.cancel(false); false }
    }
}
