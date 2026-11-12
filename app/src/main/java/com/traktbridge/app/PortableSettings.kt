package com.traktbridge.app
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
object PortableSettings {
    // Deliberately exclude credentials, permissions, tracking state, sync approvals and remote caches.
    private val allowed=mapOf("tracker" to setOf("accent","reading_size","high_contrast","hide_spoilers","monthly_goal","show_monthly_goal","secure_screen"),
        "favorites" to emptySet(),"collections" to emptySet(),"title_notes" to emptySet(),"library_status" to emptySet(),"subscriptions" to emptySet())
    fun snapshot(context:Context):String {
        val root=JSONObject()
        for((name,keys) in allowed) {
            val values=JSONObject()
            context.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach { (key,value) ->
                if((keys.isEmpty() || key in keys) && (value is String || value is Boolean || value is Int)) values.put(key,value)
            }
            root.put(name,values)
        }
        return JSONObject().put("format","traktbridge-settings-v1").put("stores",root).toString().also { require(it.length<=2_000_000) }
    }
    fun parse(text:String):Map<String,Map<String,Any>> {
        require(text.length<=2_000_000)
        val root=JSONObject(text); require(root.getString("format")=="traktbridge-settings-v1")
        val stores=root.getJSONObject("stores")
        val result=mutableMapOf<String,Map<String,Any>>()
        for((name,keys) in allowed) {
            val values=stores.optJSONObject(name) ?: continue
            val entries=mutableMapOf<String,Any>()
            val iterator=values.keys()
            while(iterator.hasNext()) {
                val key=iterator.next(); if(keys.isNotEmpty() && key !in keys) continue
                val v=values.get(key)
                require(key.length<=256 && entries.size<5000)
                if(name=="tracker") {
                    val valid=when(key) {
                        "accent" -> v is String && v in setOf("violet","blue","green")
                        "reading_size" -> v is Int && v in 0..2
                        "monthly_goal" -> v is Int && v in 0..744
                        else -> v is Boolean
                    }
                    require(valid)
                } else require(v is String && v.length<=1_000_000)
                entries[key]=v
            }
            result[name]=entries
        }
        return result
    }
    fun merge(context:Context,stores:Map<String,Map<String,Any>>):Int {
        var count=0
        for((name,entries) in stores) {
            val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE); val editor=prefs.edit()
            for((key,value) in entries) {
                if(prefs.contains(key)) continue
                when(value) { is String->editor.putString(key,value); is Boolean->editor.putBoolean(key,value); is Int->editor.putInt(key,value); else->continue }
                count++
            }
            check(editor.commit())
        }
        return count
    }
}
