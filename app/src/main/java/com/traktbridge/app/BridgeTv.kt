package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

@Composable
fun BridgeTvHome(accent:Int,live:Boolean,pending:Int,weekly:String,onDevices:()->Unit,onReview:()->Unit,onDiscover:()->Unit,onTracking:()->Unit,onPlanner:()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(accent),background=Color(0xff0d0f16),surface=Color(0xff1b1e2a),onSurface=Color(0xfff1f0f6))) {
        Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Your viewing companion",fontSize=32.sp)
            Text((if(live) "Tracking active" else "Tracking inactive")+"  •  "+weekly+" this week  •  "+pending+" awaiting review",fontSize=20.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                TvAction("Now Playing","All connected devices",Modifier.weight(1f),onDevices)
                TvAction("Review","Pending viewing records",Modifier.weight(1f),onReview)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                TvAction("Discover","Find your next watch",Modifier.weight(1f),onDiscover)
                TvAction("Tracking","Permissions and devices",Modifier.weight(1f),onTracking)
                TvAction("Planner","Plan a watch",Modifier.weight(1f),onPlanner)
            }
        }
    }
}
@Composable
internal fun TvAction(title:String,detail:String,modifier:Modifier,onClick:()->Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape=RoundedCornerShape(16.dp)
    androidx.compose.foundation.layout.Box(modifier.heightIn(min=112.dp)
        .onFocusChanged { focused=it.isFocused }
        .border(if(focused) 3.dp else 1.dp,if(focused) MaterialTheme.colorScheme.primary else Color(0xff424457),shape)
        .clickable(onClick=onClick).padding(18.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,fontSize=24.sp,color=MaterialTheme.colorScheme.onSurface)
            Text(detail,fontSize=16.sp,color=Color(0xffc3c2d1))
        }
    }
}
