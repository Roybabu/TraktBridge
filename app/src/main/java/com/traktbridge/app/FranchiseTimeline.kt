package com.traktbridge.app

data class FranchiseEntry(val title:String,val year:Int)
data class FranchiseGuide(val name:String,val entries:List<FranchiseEntry>)
object FranchiseTimeline {
    private fun entry(title:String,year:Int)=FranchiseEntry(title,year)
    val guides=listOf(
        FranchiseGuide("Middle-earth • story order",listOf(entry("The Hobbit: An Unexpected Journey",2012),entry("The Hobbit: The Desolation of Smaug",2013),entry("The Hobbit: The Battle of the Five Armies",2014),entry("The Lord of the Rings: The Fellowship of the Ring",2001),entry("The Lord of the Rings: The Two Towers",2002),entry("The Lord of the Rings: The Return of the King",2003))),
        FranchiseGuide("Middle-earth • release order",listOf(entry("The Lord of the Rings: The Fellowship of the Ring",2001),entry("The Lord of the Rings: The Two Towers",2002),entry("The Lord of the Rings: The Return of the King",2003),entry("The Hobbit: An Unexpected Journey",2012),entry("The Hobbit: The Desolation of Smaug",2013),entry("The Hobbit: The Battle of the Five Armies",2014))),
        FranchiseGuide("The Dark Knight • trilogy",listOf(entry("Batman Begins",2005),entry("The Dark Knight",2008),entry("The Dark Knight Rises",2012))),
        FranchiseGuide("The Matrix • main films",listOf(entry("The Matrix",1999),entry("The Matrix Reloaded",2003),entry("The Matrix Revolutions",2003),entry("The Matrix Resurrections",2021))),
        FranchiseGuide("Back to the Future • release order",listOf(entry("Back to the Future",1985),entry("Back to the Future Part II",1989),entry("Back to the Future Part III",1990)))
    )
    fun exact(entry:FranchiseEntry,items:List<CatalogueItem>):CatalogueItem? = items.filter { it.type=="movie" && it.id>0 && it.year==entry.year && RecognitionEngine.normalized(it.title)==RecognitionEngine.normalized(entry.title) }.distinctBy { it.id }.singleOrNull()
    fun missing(showId:Long,season:Int,catalogue:List<CatalogueItem>,records:List<WatchRecord>):List<CatalogueItem> {
        val known=records.filter { it.matchType=="episode" && it.showId==showId && it.season==season && it.traktId>0 }.map { it.traktId }.toSet()
        return catalogue.filter { it.type=="episode" && it.id>0 && it.showId==showId && it.season==season && it.episode>0 && it.id !in known }.distinctBy { it.id }.sortedBy { it.episode }
    }
}
