package com.traktbridge.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernPageHeader(accent:Int,title:String,detail:String="",onFilter:(()->Unit)?=null,onMenu:()->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title,color=MaterialTheme.colorScheme.onBackground,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                if(detail.isNotBlank()) Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
            if(onFilter!=null) TextButton(onClick=onFilter) { Text("Filter") }
            IconButton(onClick=onMenu,modifier=Modifier.semantics { contentDescription="More "+title+" options" }) { Text("⋯",fontSize=26.sp) }
        }
    }
}

@Composable
fun ModernCategories(accent:Int,labels:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            labels.forEach { (key,label) ->
                FilterChip(selected=key==selected,onClick={onSelect(key)},label={Text(label)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
}

@Composable
fun ModernActionRow(accent:Int,title:String,detail:String,onClick:()->Unit) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable(onClick=onClick)) {
            Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold); Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("›",fontSize=24.sp,color=MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ModernMetricTiles(accent:Int,metrics:List<Pair<String,String>>) {
    BridgeTheme(accent) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            metrics.chunked(if(metrics.size==3) 3 else 2).forEach { batch ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    batch.forEach { (label,value) ->
                        Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(value,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            Text(label,style=MaterialTheme.typography.bodySmall)
                        } }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernWatchChart(accent:Int,total:String,labels:List<String>,values:List<Long>,onBar:(Int)->Unit) {
    BridgeTheme(accent) {
        var selected by remember(values) { mutableStateOf(-1) }
        val primary=MaterialTheme.colorScheme.primary
        val track=MaterialTheme.colorScheme.onSurface.copy(alpha=0.12f)
        val maximum=(values.maxOrNull() ?: 0L).coerceAtLeast(1)
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Sampled watch time",style=MaterialTheme.typography.bodyMedium)
                Text(total,style=MaterialTheme.typography.headlineLarge,color=primary,fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().height(116.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom) {
                    values.forEachIndexed { index,value ->
                        Canvas(Modifier.weight(1f).fillMaxHeight().clickable { selected=index; onBar(index) }.semantics { contentDescription=labels.getOrElse(index){""}+", "+value/1000+" seconds sampled" }) {
                            val h=(size.height*(value.toDouble()/maximum).toFloat()).coerceIn(0f,size.height)
                            drawRoundRect(track,Offset(0f,size.height-2f),Size(size.width,2f),CornerRadius(2f,2f))
                            if(h>0) drawRoundRect(if(index==selected) primary else primary.copy(alpha=0.8f),Offset(0f,size.height-h),Size(size.width,h),CornerRadius(4f,4f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(labels.firstOrNull().orEmpty(),style=MaterialTheme.typography.labelSmall)
                    Text(labels.lastOrNull().orEmpty(),style=MaterialTheme.typography.labelSmall)
                }
                if(selected in values.indices) Text(labels[selected]+" · "+values[selected]/1000+" seconds sampled",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ModernReviewCard(accent:Int,item:PosterLibraryItem,evidence:String,primary:UiAction?,secondary:UiAction?,manual:UiAction?,onMenu:()->Unit) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    PlaybackArtwork(item,Modifier.width(88.dp).aspectRatio(2f/3f))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(item.status,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
                        Text(item.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis)
                        Text(item.subtitle,style=MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick=onMenu,modifier=Modifier.size(40.dp).semantics { contentDescription="Viewing record options" }) { Text("⋯",fontSize=24.sp) }
                }
                if(item.play==null) item.progress?.let { LinearProgressIndicator(progress={it.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp)) }
                if(evidence.isNotBlank()) Text(evidence,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(primary!=null || secondary!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(primary!=null) Button(onClick=primary.run,modifier=Modifier.weight(1f)) { Text(primary.label) }
                    if(secondary!=null) OutlinedButton(onClick=secondary.run,modifier=Modifier.weight(1f)) { Text(secondary.label) }
                }
                if(manual!=null) TextButton(onClick=manual.run) { Text(manual.label) }
            }
        }
    }
}
