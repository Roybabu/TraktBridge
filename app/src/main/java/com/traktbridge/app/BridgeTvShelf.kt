package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import android.widget.ImageView

data class TvLibraryItem(val key:String,val title:String,val subtitle:String,val type:String,val id:Long,val open:()->Unit)
@Composable
fun BridgeTvShelf(accent:Int,heading:String,entries:List<TvLibraryItem>) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(accent))) {
        Column(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(heading,fontSize=26.sp)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(4.dp)) {
                items(entries,key={it.key}) { item ->
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.width(176.dp).onFocusChanged { focused=it.isFocused }.border(if(focused) 3.dp else 1.dp,if(focused) Color(accent) else Color(0xff424457),RoundedCornerShape(12.dp)).clickable(onClick=item.open).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        AndroidView(factory={ context -> ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play) } },update={ image -> image.contentDescription="Poster for "+item.title; if(item.id>0) Artwork.load(image.context,item.type,item.id,image) },modifier=Modifier.fillMaxWidth().height(190.dp))
                        Text(item.title,fontSize=18.sp,maxLines=2)
                        Text(item.subtitle,fontSize=14.sp,maxLines=2)
                    }
                }
            }
        }
    }
}
