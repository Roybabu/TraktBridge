package com.traktbridge.app
object DashboardPreferences {
    val sections=listOf("metrics","goal","now","continue","review","discover","recent")
    fun order(raw:String):List<String> = (raw.split(',').filter { it in sections }.distinct()+sections).distinct()
    fun visible(raw:String):Set<String> = raw.split(',').filter { it in sections }.toSet()
    fun startTab(raw:Int):Int=raw.coerceIn(0,3)
    fun widgetMode(raw:String):String=raw.takeIf { it in setOf("continue","review","private") } ?: "private"
}
