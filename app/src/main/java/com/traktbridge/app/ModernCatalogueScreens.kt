package com.traktbridge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun actionDescription(label:String):String=when {
    label=="Customize dashboard"->"Choose sections, order and startup preferences"
    label=="Refresh library"->"Reload saved viewing information"
    label=="Viewing planner"->"Find something that fits your available time"
    label.startsWith("Title details")->"Information, rating and related titles"
    label.contains("duplicate",true)->"Review possible matches"
    label.contains("More title",true)->"Status, favorites, notes and collections"
    label.contains("sessions",true)->"Recorded viewing history"
    label.startsWith("Delete")->"Remove this local viewing record"
    label.startsWith("Personal status")->"Set your own viewing status"
    label.contains("favorite",true)->"Manage your favorites"
    label.contains("Notes",true)->"Add personal notes and tags"
    label.contains("collection",true)->"Organize this title"
    else->""
}

@Composable
fun ModernDetailAction(label:String,onClick:()->Unit) {
    val destructive=label.startsWith("Delete") || label.startsWith("Remove")
    val color=if(destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(shape=RoundedCornerShape(12.dp),color=if(destructive) MaterialTheme.colorScheme.error.copy(alpha=0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f)) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label,color=color,fontWeight=FontWeight.Medium)
                val detail=actionDescription(label)
                if(detail.isNotBlank()) Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›",color=if(destructive) color else MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
internal fun DetailHeader(title:String,onClose:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick=onClose,modifier=Modifier.size(48.dp),contentPadding=PaddingValues(0.dp)) { Text("‹",style=MaterialTheme.typography.headlineMedium) }
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ModernCatalogueDetails(accent:Int,d:TitleDetails,type:String,id:Long,hideSpoilers:Boolean,history:String,actions:List<UiAction>,onClose:()->Unit) {
    BridgeTheme(accent) {
        var revealed by remember { mutableStateOf(!hideSpoilers) }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DetailHeader("Title details",onClose)
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {
                    val poster=PosterLibraryItem(d.title,"","",type,id,{},{})
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        LibraryPoster(poster,Modifier.width(116.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(d.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                            Text(listOf(d.year.takeIf { it>0 }?.toString().orEmpty(),d.runtime.takeIf { it>0 }?.let { "$it min" }.orEmpty(),d.genres).filter { it.isNotBlank() }.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(d.rating>0) Text("★ Trakt "+String.format(java.util.Locale.US,"%.1f",d.rating)+"/10",color=MaterialTheme.colorScheme.primary)
                            if(d.released.isNotBlank()) Text("Released: "+d.released.take(10),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(d.overview.isNotBlank()) item {
                    if(revealed) Text(d.overview,color=MaterialTheme.colorScheme.onBackground)
                    if(hideSpoilers) TextButton(onClick={revealed=!revealed}) { Text(if(revealed) "Hide synopsis" else "Reveal synopsis") }
                }
                if(history.isNotBlank()) item {
                    Text("Your viewing history",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onBackground)
                    Text(history,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(actions) { action -> ModernDetailAction(action.label,action.run) }
            }
        }
    }
}

@Composable
fun ModernRelatedTitles(accent:Int,rows:List<CatalogueItem>,onSelect:(CatalogueItem)->Unit,onClose:()->Unit) {
    BridgeTheme(accent) {
        var query by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf("All") }
        val visible=remember(rows,query,filter) { rows.filter { it.title.contains(query,true) && (filter=="All" || filter=="Shows" && it.type=="show" || filter=="Movies" && it.type=="movie") } }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DetailHeader("Related titles",onClose)
            OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),label={Text("Search related titles")},singleLine=true)
            Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All","Shows","Movies").forEach { label -> FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)}) }
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(visible.isEmpty()) item { Text("No matching titles",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                items(visible) { row ->
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().clickable { onSelect(row) }.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            LibraryPoster(PosterLibraryItem(row.title,"","",row.type,row.id,{},{ }),Modifier.width(50.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(6.dp)))
                            Column(Modifier.weight(1f)) {
                                Text(row.title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                                Text(listOf(row.year.takeIf { it>0 }?.toString().orEmpty(),if(row.type=="show") "Show" else "Movie").filter { it.isNotBlank() }.joinToString(" · "),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                            }
                            Text("›",color=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernSeriesDetails(accent:Int,series:SeriesGroup,onProgress:()->Unit,onCatalogue:()->Unit,onSeason:(Int)->Unit,onEpisode:(ViewingTitle)->Unit,onClose:()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DetailHeader(series.title,onClose)
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        LibraryPoster(PosterLibraryItem(series.title,"","", "show",series.id,{},{ }),Modifier.width(92.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(series.title,style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onBackground)
                            Text(series.completed.toString()+" episodes synced · "+series.episodes.size+" identified",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tracked history, not the full catalogue",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { ModernDetailAction("Check Trakt series progress",onProgress) }
                item { ModernDetailAction("Browse all seasons",onCatalogue) }
                series.seasons.forEach { season ->
                    item {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if(season.number==0) "Specials" else "Season "+season.number,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground)
                                Text(season.completed.toString()+" synced / "+season.episodes.size+" identified",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick={onSeason(season.number)}) { Text("All episodes") }
                        }
                    }
                    items(season.episodes) { episode ->
                        val r=episode.representative
                        ModernDetailAction("E"+r.episode+" · "+r.matchLabel.substringAfter(" — ",r.title)+" · "+when { episode.confirmed->"Watched";episode.queued->"Awaiting sync";else->"In progress" }) { onEpisode(episode) }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernContinueEpisodes(accent:Int,title:String,episodes:List<PosterLibraryItem>,onSelect:(Int)->Unit,onClose:()->Unit) {
    ModernToolPage(accent,title,onClose) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item { Text("Episodes in progress",style=MaterialTheme.typography.titleLarge) }
            items(episodes.size) { index ->
                val episode=episodes[index]
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        LibraryPoster(episode,Modifier.width(56.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(episode.subtitle,style=MaterialTheme.typography.titleMedium)
                            Text(episode.status,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            episode.progress?.let { value -> LinearProgressIndicator(progress={value.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth()) }
                        }
                        Text("›",color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
