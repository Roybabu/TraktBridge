package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BridgeTheme(accent:Int,content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(accent),onPrimary=Color(0xff171022),background=Color(0xff0d0f16),surface=Color(0xff1b1e2a),onSurface=Color(0xfff1f0f6)),content=content)
}

@Composable
fun BridgeHeader(accent:Int,onSearch:()->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRAKTBRIDGE",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("Your viewing companion",color=MaterialTheme.colorScheme.onSurface,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            }
            TextButton(onClick=onSearch) { Text("Search") }
        }
    }
}

@Composable
fun BridgeHomeSummary(accent:Int,live:Boolean,pending:Int,weekly:String,onTracking:()->Unit,onReview:()->Unit,onDiscover:()->Unit,onPlanner:()->Unit) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(live) "Tracking active" else "Tracking inactive",color=if(live) Color(0xff6ee7b7) else Color(0xfffbbf24),fontWeight=FontWeight.SemiBold)
                Text(weekly+" sampled this week",style=MaterialTheme.typography.headlineSmall)
                Text(pending.toString()+" titles awaiting review or sync",style=MaterialTheme.typography.bodyMedium)
                Button(onClick=onTracking,modifier=Modifier.fillMaxWidth()) { Text("Tracking settings") }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=onReview,modifier=Modifier.weight(1f)) { Text("Review") }
                    OutlinedButton(onClick=onDiscover,modifier=Modifier.weight(1f)) { Text("Discover") }
                }
                TextButton(onClick=onPlanner) { Text("Plan a watch") }
            }
        }
    }
}

@Composable
fun BridgeLibraryToolbar(accent:Int,summary:String,onSearch:()->Unit,onFilter:()->Unit,onManual:()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Your library",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurface)
            Text(summary,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(onClick=onSearch,modifier=Modifier.weight(1f)) { Text("Search") }
                OutlinedButton(onClick=onFilter,modifier=Modifier.weight(1f)) { Text("Filters") }
            }
            TextButton(onClick=onManual) { Text("Log a watch") }
        }
    }
}
