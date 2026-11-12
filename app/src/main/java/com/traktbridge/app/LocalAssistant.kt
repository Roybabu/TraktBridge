package com.traktbridge.app
import java.util.Locale

data class AssistantQuery(val action:String,val days:Int=0,val minutes:Int=0)
object LocalAssistant {
    fun parse(input:String):AssistantQuery {
        val q=input.lowercase(Locale.ROOT).trim()
        val minutes=Regex("([0-9]{1,3})\\s*(?:minutes?|mins?)").find(q)?.groupValues?.get(1)?.toIntOrNull()
        val hours=Regex("([0-9]{1,2})\\s*(?:hours?|hrs?)").find(q)?.groupValues?.get(1)?.toIntOrNull()
        val budget=(minutes ?: ((hours ?: 0)*60)).coerceIn(0,720)
        val days=when { "last month" in q || "30 days" in q->30; "last week" in q || "7 days" in q->7; else->0 }
        return when {
            "subscription" in q || "least used" in q -> AssistantQuery("subscriptions")
            "unfinished" in q || "continue" in q || "not finished" in q -> AssistantQuery("unfinished")
            budget>0 -> AssistantQuery("budget",minutes=budget)
            "watch tonight" in q || "recommend" in q -> AssistantQuery("recommend")
            "watched" in q || "history" in q -> AssistantQuery("history",days)
            else -> AssistantQuery("search")
        }
    }
}
