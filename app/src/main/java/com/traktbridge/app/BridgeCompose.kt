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
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

private val BridgeScaled=staticCompositionLocalOf { false }

@Composable
fun BridgeTheme(accent:Int,content:@Composable ()->Unit) {
    val context=LocalContext.current
    val prefs=context.getSharedPreferences("tracker",android.content.Context.MODE_PRIVATE)
    val mode=AppearancePolicy.mode(prefs.getString("appearance_mode","dark").orEmpty())
    val scheme=when {
        mode=="dynamic" && android.os.Build.VERSION.SDK_INT>=31 -> dynamicDarkColorScheme(context)
        mode=="amoled" -> darkColorScheme(primary=Color(accent),onPrimary=Color(0xff171022),background=Color.Black,surface=Color(0xff101010),onSurface=Color(BridgeDesign.ink))
        else -> darkColorScheme(primary=Color(accent),onPrimary=Color(0xff171022),background=Color(BridgeDesign.background),surface=Color(BridgeDesign.surface),onSurface=Color(BridgeDesign.ink))
    }
    val density=LocalDensity.current
    val tv=context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val scale=if(BridgeScaled.current) 1f else UiAccessibility.readingScale(prefs.getInt("reading_size",0),tv)
    CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*scale),BridgeScaled provides true) {
        MaterialTheme(colorScheme=if(prefs.getBoolean("high_contrast",false)) scheme.copy(onSurface=Color.White,onSurfaceVariant=Color.White) else scheme,content=content)
    }
}

@Composable
fun BridgeHeader(accent:Int,onSearch:()->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRAKTBRIDGE",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("Your viewing companion",color=MaterialTheme.colorScheme.onSurface,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick=onSearch,modifier=Modifier.heightIn(min=48.dp)) { Text("Search") }
        }
    }
}

@Composable
fun BridgeHomeSummary(accent:Int,live:Boolean,pending:Int,weekly:String,onTracking:()->Unit,onReview:()->Unit,onDiscover:()->Unit,onPlanner:()->Unit) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(live) "Tracking active" else "Tracking inactive",color=if(live) Color(BridgeDesign.green) else Color(BridgeDesign.warning),fontWeight=FontWeight.SemiBold)
                Text(weekly,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Sampled this week",style=MaterialTheme.typography.bodyMedium)
                Text(pending.toString()+" titles awaiting review or sync",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick=onTracking,modifier=Modifier.fillMaxWidth()) { Text("Tracking settings") }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=onReview,modifier=Modifier.weight(1f)) { Text("Review") }
                    OutlinedButton(onClick=onDiscover,modifier=Modifier.weight(1f)) { Text("Discover") }
                }
                OutlinedButton(onClick=onPlanner,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Plan a watch") }
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
                    NavigationBarItem(selected=index==selected,onClick={onSelect(index)},icon={ Text(listOf("⌂","▤","✓","▥","⚙")[index],fontSize=20.sp) },label={ Text(label,fontSize=11.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) },alwaysShowLabel=true)
                }
            }
        }
    }
}

@Composable
fun BridgeSection(accent:Int,title:String) {
    BridgeTheme(accent) { Text(title,modifier=Modifier.fillMaxWidth().padding(top=12.dp,bottom=10.dp).semantics { heading() },style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface) }
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
        val context=LocalContext.current
        val reduce=context.getSharedPreferences("tracker",0).getBoolean("reduce_motion",false)
        val opacity by animateFloatAsState(if(entered || reduce) 1f else 0.55f,animationSpec=tween(if(reduce) 0 else 180),label="empty-state")
        Card(Modifier.fillMaxWidth().padding(vertical=6.dp).alpha(opacity)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text(detail,style=MaterialTheme.typography.bodyMedium)
                if(actionLabel.isNotBlank()) OutlinedButton(onClick=onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun BridgeExpandedHome(accent:Int,live:Boolean,pending:Int,weekly:String,onTracking:()->Unit,onReview:()->Unit,onDiscover:()->Unit,onPlanner:()->Unit,onDevices:()->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.1f)) { BridgeHomeSummary(accent,live,pending,weekly,onTracking,onReview,onDiscover,onPlanner) }
            Card(Modifier.weight(1f).padding(vertical=6.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Connected devices",style=MaterialTheme.typography.headlineSmall)
                    Text("See live playback across your paired phone, tablet and TV.",style=MaterialTheme.typography.bodyLarge)
                    Button(onClick=onDevices,modifier=Modifier.fillMaxWidth()) { Text("Now Playing") }
                    OutlinedButton(onClick=onTracking,modifier=Modifier.fillMaxWidth()) { Text("Device settings") }
                }
            }
        }
    }
}
