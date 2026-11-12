package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

@Composable
fun BridgeNavigation(accent:Int,labels:List<String>,selected:Int,wide:Boolean,onSelect:(Int)->Unit) {
    BridgeTheme(accent) {
        if(wide) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index,label ->
                    if(index==selected) Button(onClick={onSelect(index)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(label) }
                    else OutlinedButton(onClick={onSelect(index)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(label) }
                }
            }
        } else {
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                labels.forEachIndexed { index,label ->
                    NavigationBarItem(selected=index==selected,onClick={onSelect(index)},icon={ Text(listOf("⌂","▤","✓","▥","⚙")[index],fontSize=20.sp) },label={ Text(label,fontSize=11.sp,maxLines=2) },alwaysShowLabel=true)
                }
            }
        }
    }
}

@Composable
fun BridgeSection(accent:Int,title:String) {
    BridgeTheme(accent) { Text(title,modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=10.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun BridgeReviewSummary(accent:Int,pending:Int,onLog:()->Unit,onSync:()->Unit) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Review queue",style=MaterialTheme.typography.headlineSmall)
                Text("$pending titles awaiting review or synchronization")
                Button(onClick=onLog,modifier=Modifier.fillMaxWidth()) { Text("Log a watch manually") }
                OutlinedButton(onClick=onSync,modifier=Modifier.fillMaxWidth()) { Text("Retry approved uploads") }
            }
        }
    }
}

@Composable
fun BridgeTitleSummary(accent:Int,title:String,metadata:String,rating:Double,release:String,overview:String,hideSpoilers:Boolean) {
    BridgeTheme(accent) {
        var reveal by androidx.compose.runtime.remember(title) { androidx.compose.runtime.mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)
            if(metadata.isNotBlank()) Text(metadata,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(rating in 0.1..10.0) AssistChip(onClick={},label={ Text("Trakt "+String.format(java.util.Locale.US,"%.1f",rating)+"/10") },enabled=false)
            if(release.isNotBlank()) Text("Release: "+release,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(hideSpoilers && !reveal) OutlinedButton(onClick={ reveal=true }) { Text("Reveal synopsis") }
            else Text(overview.ifBlank { "No overview available" },style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun BridgeLoading(accent:Int) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().padding(vertical=12.dp).semantics { contentDescription="Loading viewing history" },verticalArrangement=Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            repeat(3) {
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(76.dp).height(108.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.10f),RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.fillMaxWidth(0.85f).height(18.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.12f),RoundedCornerShape(6.dp)))
                            Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha=0.08f),RoundedCornerShape(6.dp)))
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun BridgeEmptyState(accent:Int,title:String,detail:String,actionLabel:String,onAction:()->Unit) {
    BridgeTheme(accent) {
        var entered by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { entered=true }
        val opacity by animateFloatAsState(if(entered) 1f else 0.55f,animationSpec=tween(180),label="empty-state")
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp).alpha(opacity)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text(detail,style=MaterialTheme.typography.bodyMedium)
                if(actionLabel.isNotBlank()) OutlinedButton(onClick=onAction) { Text(actionLabel) }
            }
        }
    }
}
