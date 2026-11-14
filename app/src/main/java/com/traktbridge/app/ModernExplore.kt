package com.traktbridge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ExploreEntry(val item:CatalogueItem,val detail:String)

@Composable
private fun ExploreLink(title:String,detail:String,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.6f)) {
        Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,fontWeight=FontWeight.SemiBold)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
        }
    }
}
@Composable
fun ModernExploreLinks(accent:Int,onForYou:()->Unit,onDiscover:()->Unit,onCalendar:()->Unit,onAnime:()->Unit,onSettings:()->Unit) {
    BridgeTheme(accent) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ExploreLink("For you","Recommendations based on your viewing",onForYou)
            ExploreLink("Discover","Trending, popular and mood-based titles",onDiscover)
            ExploreLink("Upcoming episodes","Your personal Trakt calendar",onCalendar)
            ExploreLink("Anime","Search AniList and track your progress",onAnime)
            TextButton(onClick=onSettings,modifier=Modifier.align(Alignment.End)) { Text("Explore settings") }
        }
    }
}
@Composable
fun ModernForYou(accent:Int,hasHistory:Boolean,enabled:Boolean,onLoad:()->Unit,onDiscover:()->Unit,onSettings:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"For you",onClose) {
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Find your next favorite",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            Text(if(hasHistory) "Find related titles based on your completed viewing history." else "Add some completed watches to get personalized recommendations.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Up to five catalogue title IDs are sent to Trakt. Your viewing times and feedback stay on this device.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(hasHistory) Button(onClick=onLoad,modifier=Modifier.fillMaxWidth()) { Text(if(enabled) "Find recommendations" else "Enable & find recommendations") }
            ExploreLink("Browse Discover","Trending and popular titles",onDiscover)
            ExploreLink("Explore settings","Manage preferences and hidden titles",onSettings)
        }
    }
}
@Composable
fun ModernDiscover(accent:Int,initialType:String,initialMode:String,onBrowse:(String,String)->Unit,onMood:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Discover",onClose) {
        var type by remember { mutableStateOf(if(initialType=="show") "show" else "movie") }
        var mode by remember { mutableStateOf(if(initialMode=="popular") "popular" else "trending") }
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("What would you like to watch?",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("movie" to "Movies","show" to "TV shows").forEach { (key,label) -> FilterChip(selected=type==key,onClick={type=key},label={Text(label)}) }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("trending" to "Trending","popular" to "Popular").forEach { (key,label) -> FilterChip(selected=mode==key,onClick={mode=key},label={Text(label)}) }
            }
            Button(onClick={onBrowse(type,mode)},modifier=Modifier.fillMaxWidth()) { Text("Browse titles") }
            ExploreLink("Find by mood","Choose genre, runtime and rating",onMood)
        }
    }
}
@Composable
fun ModernExploreResults(accent:Int,title:String,entries:List<ExploreEntry>,detail:String,onOpen:(CatalogueItem)->Unit,onHide:((CatalogueItem)->Boolean)?,onClose:()->Unit) {
    ModernToolPage(accent,title,onClose) {
        var query by remember { mutableStateOf("") }
        var hidden by remember { mutableStateOf(emptySet<String>()) }
        val rows=entries.distinctBy { it.item.type+":"+it.item.id }.filter { it.item.type+":"+it.item.id !in hidden && it.item.title.contains(query,true) }
        Column(Modifier.fillMaxSize()) {
            Text(detail,Modifier.padding(horizontal=16.dp,vertical=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(query,{query=it},label={Text("Filter titles")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp))
            LazyVerticalGrid(columns=GridCells.Adaptive(140.dp),modifier=Modifier.weight(1f),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(rows.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { Text("No matching titles. Try a different filter.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                items(rows,key={it.item.type+":"+it.item.id}) { entry ->
                    val row=entry.item
                    val poster=PosterLibraryItem(row.title,listOf(row.year.takeIf { it>0 }?.toString().orEmpty(),entry.detail).filter { it.isNotBlank() }.joinToString(" · "),"",row.type,row.id,{onOpen(row)},{onOpen(row)})
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        PlaybackArtwork(poster,Modifier.fillMaxWidth().aspectRatio(2f/3f))
                        Text(row.title,fontWeight=FontWeight.SemiBold,maxLines=2,modifier=Modifier.clickable { onOpen(row) })
                        if(poster.subtitle.isNotBlank()) Text(poster.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(onHide!=null) TextButton(onClick={if(onHide(row)) hidden=hidden+(row.type+":"+row.id)},contentPadding=PaddingValues(0.dp)) { Text("Not interested",style=MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}
@Composable
fun ModernMoodDiscovery(accent:Int,onFind:(Int,String,Int,Double)->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Find by mood",onClose) {
        var mood by remember { mutableIntStateOf(0) }; var type by remember { mutableStateOf("movie") }; var runtime by remember { mutableIntStateOf(0) }; var rating by remember { mutableStateOf(0.0) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Mood",style=MaterialTheme.typography.titleMedium)
            DiscoveryEngine.moods.forEachIndexed { index,item -> Row(Modifier.fillMaxWidth().clickable { mood=index },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=mood==index,onClick={mood=index}); Text(item.label) } }
            Text("Content type",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("movie" to "Movies","show" to "Shows").forEach { (key,label) -> FilterChip(selected=type==key,onClick={type=key},label={Text(label)}) } }
            Text("Maximum runtime per title or episode")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(0,30,60,90,120).forEach { n -> FilterChip(selected=runtime==n,onClick={runtime=n},label={Text(if(n==0) "Any" else "$n min")}) } }
            Text("Minimum Trakt rating")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(0.0,7.0,8.0).forEach { n -> FilterChip(selected=rating==n,onClick={rating=n},label={Text(if(n==0.0) "Any" else n.toInt().toString()+"/10")}) } }
            Button(onClick={onFind(mood,type,runtime,rating)},modifier=Modifier.fillMaxWidth()) { Text("Find titles") }
        }
    }
}
@Composable
fun ModernEpisodeCalendar(accent:Int,rows:List<UpcomingEpisode>,days:Int,spoilers:Boolean,enabled:Boolean,onRemind:(UpcomingEpisode)->Boolean,onSettings:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Upcoming episodes",onClose) {
        var scheduled by remember { mutableStateOf(emptySet<String>()) }
        val groups=rows.groupBy { java.time.Instant.parse(it.airs).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.toSortedMap()
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Next $days days · Personal Trakt calendar",color=MaterialTheme.colorScheme.onSurfaceVariant); Text("Air dates are not streaming availability.",style=MaterialTheme.typography.bodySmall); TextButton(onClick=onSettings) { Text("Calendar settings") } }
            if(rows.isEmpty()) item { Text("No upcoming episodes returned.") }
            groups.forEach { (date,episodes) ->
                item { Text(date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM")),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary) }
                items(episodes) { e ->
                    val at=java.time.Instant.parse(e.airs)
                    val key=e.show+"|"+e.season+"|"+e.episode+"|"+e.airs
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(e.show,fontWeight=FontWeight.SemiBold)
                            Text("S"+e.season+" E"+e.episode+if(spoilers) "" else " · "+e.title,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(at.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),style=MaterialTheme.typography.bodySmall)
                            if(at.toEpochMilli()>System.currentTimeMillis()) TextButton(enabled=enabled && key !in scheduled,onClick={if(onRemind(e)) scheduled=scheduled+key}) { Text(if(key in scheduled) "Reminder scheduled" else if(enabled) "Remind me" else "Reminders disabled") }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ModernAnimeSearch(accent:Int,onSearch:(String)->Unit,onLocal:()->Unit,onPublic:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Anime",onClose) {
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query,{query=it.take(200)},label={Text("Anime title")},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("Search English, Japanese or romaji titles. Search text is sent to AniList.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={onSearch(query.trim())},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Search AniList") }
            ExploreLink("My anime progress","Track episodes locally",onLocal)
            ExploreLink("Public AniList library","Browse a public account",onPublic)
        }
    }
}
@Composable
fun ModernAnimeResults(accent:Int,rows:List<AnimeResult>,spoilers:Boolean,onTrack:(AnimeResult)->Unit,onDetails:(AnimeResult)->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Anime results",onClose) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(rows.isEmpty()) item { Text("No anime found.") }
            items(rows,key={it.id}) { row ->
                var expanded by remember { mutableStateOf(!spoilers) }
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            LibraryPoster(PosterLibraryItem(row.english.ifBlank { row.romaji },"","","anime",row.id.toLong(),{},{}),Modifier.width(76.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f)) { Text(row.english.ifBlank { row.romaji },fontWeight=FontWeight.SemiBold); Text(row.romaji,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant); Text(if(row.episodes>0) row.episodes.toString()+" episodes" else "Episode count unknown") }
                        }
                        if(expanded && row.description.isNotBlank()) Text(row.description.take(2000),style=MaterialTheme.typography.bodySmall)
                        if(spoilers && row.description.isNotBlank()) TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide synopsis" else "Reveal synopsis") }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={onTrack(row)},modifier=Modifier.weight(1f)) { Text("Track locally") }; TextButton(onClick={onDetails(row)}) { Text("AniList") } }
                    }
                }
            }
        }
    }
}
@Composable
private fun ExploreToggle(title:String,detail:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.Medium); Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked=value,onCheckedChange=onChange)
    }
}
@Composable
fun ModernExploreSettings(accent:Int,initialPersonalized:Boolean,initialExclude:Boolean,initialSpoilers:Boolean,initialDays:Int,initialReminders:Boolean,initialHidden:Int,onSave:(Boolean,Boolean,Boolean,Int,Boolean)->Unit,onReset:()->Unit,onCancelReminders:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Explore settings",onClose) {
        var personalized by remember { mutableStateOf(initialPersonalized) }; var exclude by remember { mutableStateOf(initialExclude) }; var spoilers by remember { mutableStateOf(initialSpoilers) }; var days by remember { mutableIntStateOf(initialDays) }; var reminders by remember { mutableStateOf(initialReminders) }; var hidden by remember { mutableIntStateOf(initialHidden) }; var confirm by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Recommendations",style=MaterialTheme.typography.titleLarge)
                ExploreToggle("Personalized recommendations","Allow related-title requests using your completed viewing history",personalized){personalized=it}
                ExploreToggle("Hide already watched titles","Exclude previously watched movies and shows from discovery",exclude){exclude=it}
                ExploreLink("Reset hidden titles","$hidden titles hidden",{confirm="hidden"})
                HorizontalDivider()
                Text("Content",style=MaterialTheme.typography.titleLarge)
                ExploreToggle("Hide spoilers","Hide episode titles and synopses until revealed",spoilers){spoilers=it}
                HorizontalDivider()
                Text("Calendar & reminders",style=MaterialTheme.typography.titleLarge)
                Text("Calendar range")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(7,14,30).forEach { n -> FilterChip(selected=days==n,onClick={days=n},label={Text("$n days")}) } }
                ExploreToggle("Episode reminders","Allow manually scheduled air-time notifications",reminders){reminders=it}
                ExploreLink("Cancel scheduled reminders","Remove all episode reminders from this device",{confirm="reminders"})
            }
            Button(onClick={onSave(personalized,exclude,spoilers,days,reminders)},modifier=Modifier.fillMaxWidth()) { Text("Save preferences") }
        }
        if(confirm.isNotBlank()) AlertDialog(onDismissRequest={confirm=""},title={Text(if(confirm=="hidden") "Reset hidden titles?" else "Cancel all reminders?")},confirmButton={TextButton(onClick={if(confirm=="hidden"){onReset();hidden=0}else onCancelReminders();confirm=""}){Text("Confirm")}},dismissButton={TextButton(onClick={confirm=""}){Text("Cancel")}})
    }
}
