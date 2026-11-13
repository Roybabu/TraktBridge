package com.traktbridge.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView

data class PosterLibraryItem(val title:String,val subtitle:String,val status:String,val type:String,val id:Long,val open:()->Unit,val menu:()->Unit,val progress:Float?=null,val play:(()->Unit)?=null)

@Composable
fun LibraryPoster(item:PosterLibraryItem,modifier:Modifier) {
    AndroidView(factory={ ctx -> ImageView(ctx).apply { scaleType=ImageView.ScaleType.CENTER_CROP } },modifier=modifier,update={ view ->
        view.contentDescription="Artwork for "+item.title
        Artwork.load(view.context,item.type,item.id,view)
    })
}

@Composable
fun PosterLibraryHeader(accent:Int,category:String,onCategory:(String)->Unit,onSearch:()->Unit,onFilter:()->Unit,onMenu:()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Library",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)
                IconButton(onClick=onSearch) { Text("⌕",fontSize=28.sp) }
                IconButton(onClick=onFilter) { Text("≡",fontSize=24.sp) }
                IconButton(onClick=onMenu) { Text("⋯",fontSize=26.sp) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("All","Movies","Series","Anime","Unidentified").forEach { label ->
                    FilterChip(selected=category==label,onClick={onCategory(label)},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
                }
            }
        }
    }
}

/** Shared poster action: playback when available, details otherwise. */
@Composable
fun PlaybackArtwork(item:PosterLibraryItem,modifier:Modifier) {
    val play=item.play
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
        .clickable(role=Role.Button,onClick=play ?: item.open)
        .semantics { contentDescription=if(play!=null) "Open playback app for "+item.title else "Details for "+item.title }) {
        LibraryPoster(item,Modifier.matchParentSize())
        if(play!=null) {
            Box(Modifier.align(Alignment.Center).size(48.dp).background(Color.Black.copy(alpha=0.40f),CircleShape),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(21.dp)) {
                    val triangle=Path().apply { moveTo(size.width*0.24f,size.height*0.10f); lineTo(size.width*0.88f,size.height*0.50f); lineTo(size.width*0.24f,size.height*0.90f); close() }
                    drawPath(triangle,Color.White.copy(alpha=0.92f))
                }
            }
            item.progress?.let { value ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(Color.Black.copy(alpha=0.55f))) {
                    Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
fun PosterLibraryShelf(accent:Int,items:List<PosterLibraryItem>) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Column(Modifier.width(148.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    PlaybackArtwork(item,Modifier.fillMaxWidth().aspectRatio(2f/3f))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(item.title,Modifier.weight(1f).clickable(onClick=item.open),color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick=item.menu,modifier=Modifier.size(40.dp).semantics { contentDescription="More options for "+item.title }) { Text("⋯",color=MaterialTheme.colorScheme.onBackground,fontSize=24.sp) }
                    }
                    Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun PosterLibraryTile(accent:Int,item:PosterLibraryItem) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().padding(bottom=14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            PlaybackArtwork(item,Modifier.fillMaxWidth().aspectRatio(2f/3f))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(item.title,Modifier.weight(1f).clickable(onClick=item.open),color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                IconButton(onClick=item.menu,modifier=Modifier.size(40.dp).semantics { contentDescription="More options for "+item.title }) { Text("⋯",color=MaterialTheme.colorScheme.onBackground,fontSize=24.sp) }
            }
            Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(item.status.isNotBlank()) Text(item.status,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
        }
    }
}

data class PosterGridSection(val title:String,val count:Int,val item:(Int)->PosterLibraryItem)

/** A bounded, independently scrolling grid; only visible cards create artwork views. */
@Composable
fun ModernLazyLibrary(accent:Int,columns:Int,category:String,sections:List<PosterGridSection>,continuing:List<PosterLibraryItem>,syncTitle:String,syncDetail:String,filtered:Boolean,onCategory:(String)->Unit,onSearch:()->Unit,onFilter:()->Unit,onMenu:()->Unit,onSync:()->Unit,onLog:()->Unit,onAnime:()->Unit) {
    BridgeTheme(accent) {
        val state=androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        LaunchedEffect(category) { state.scrollToItem(0) }
        LazyVerticalGrid(columns=GridCells.Fixed(columns),state=state,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item(key="header",span={GridItemSpan(maxLineSpan)}) { PosterLibraryHeader(accent,category,onCategory,onSearch,onFilter,onMenu) }
            item(key="sync",span={GridItemSpan(maxLineSpan)}) { ModernActionRow(accent,syncTitle,syncDetail,onSync) }
            if(filtered) item(key="filter",span={GridItemSpan(maxLineSpan)}) { Text("History filters active",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if(continuing.isNotEmpty()) {
                item(key="continue-header",span={GridItemSpan(maxLineSpan)}) { Text("Continue watching",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground) }
                item(key="continue",span={GridItemSpan(maxLineSpan)}) { PosterLibraryShelf(accent,continuing) }
            }
            sections.forEach { section ->
                item(key="heading:"+section.title,span={GridItemSpan(maxLineSpan)}) { Text(section.title,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onBackground) }
                items(section.count,key={ index -> section.title+":"+index }) { index -> PosterLibraryTile(accent,section.item(index)) }
            }
            if(category=="Anime") item(key="anime-search",span={GridItemSpan(maxLineSpan)}) { TextButton(onClick=onAnime) { Text("Find anime") } }
            if(sections.isEmpty()) item(key="empty",span={GridItemSpan(maxLineSpan)}) { ModernActionRow(accent,"No titles yet","Log a watch",onLog) }
        }
    }
}
