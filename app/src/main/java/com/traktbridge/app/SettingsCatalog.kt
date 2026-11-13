package com.traktbridge.app

object SettingsCatalog {
    data class Category(val id:String,val title:String,val description:String)
    val categories=listOf(
        Category("account","Account & Sync","Trakt account, downloads and approved uploads"),
        Category("tracking","Playback & Tracking","YouTube tracking and Android permissions"),
        Category("devices","Connected Devices","Pair devices and choose what to share"),
        Category("appearance","Appearance","Theme, text, haptics and accessibility"),
        Category("library","Library & Personalization","Collections, watchlists, goals and shortcuts"),
        Category("privacy","Privacy & Security","Tracking privacy, app lock and screen protection"),
        Category("data","Backup & Data","Import, export, backup, restore and caches"),
        Category("advanced","Advanced & Support","Diagnostics, updates and feedback")
    )
}
