package com.traktbridge.app

import androidx.compose.foundation.Image
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

data class PosterLibraryItem(val title:String,val subtitle:String,val status:String,val type:String,val id:Long,val open:()->Unit,val menu:()->Unit,val progress:Float?=null)

@Composable
private fun LibraryPoster(item:PosterLibraryItem,modifier:Modifier) {
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

@Composable
fun PosterLibraryShelf(accent:Int,items:List<PosterLibraryItem>) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Column(Modifier.width(190.dp).clickable(onClick=item.open),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    LibraryPoster(item,Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface))
                    item.progress?.let { LinearProgressIndicator(progress={it.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp)) }
                    Text(item.title,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
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
            LibraryPoster(item,Modifier.fillMaxWidth().aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=item.open))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(item.title,Modifier.weight(1f).clickable(onClick=item.open),fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                IconButton(onClick=item.menu,modifier=Modifier.size(40.dp)) { Text("⋯",fontSize=24.sp) }
            }
            Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(item.status.isNotBlank()) Text(item.status,color=MaterialTheme.colorScheme.primary,fontSize=12.sp)
        }
    }
}
