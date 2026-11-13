package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
            Image(painterResource(R.drawable.bridge_logo),contentDescription=null,modifier=Modifier.size(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TRAKTBRIDGE",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("Your viewing companion",color=MaterialTheme.colorScheme.onSurface,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick=hapticClick(onSearch),modifier=Modifier.heightIn(min=48.dp)) { Text("Search") }
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
                Button(onClick=hapticClick(onTracking),modifier=Modifier.fillMaxWidth()) { Text("Tracking settings") }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick=hapticClick(onReview),modifier=Modifier.weight(1f)) { Text("Review") }
                    OutlinedButton(onClick=hapticClick(onDiscover),modifier=Modifier.weight(1f)) { Text("Discover") }
                }
                OutlinedButton(onClick=hapticClick(onPlanner),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Plan a watch") }
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
                Button(onClick=hapticClick(onSearch),modifier=Modifier.weight(1f)) { Text("Search") }
                OutlinedButton(onClick=hapticClick(onFilter),modifier=Modifier.weight(1f)) { Text("Filters") }
            }
            TextButton(onClick=hapticClick(onManual)) { Text("Log a watch") }
        }
    }
}

@Composable
fun BridgeNavigation(accent:Int,labels:List<String>,selected:Int,wide:Boolean,onSelect:(Int)->Unit) {
    BridgeTheme(accent) {
        if(wide) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { index,label ->
                    if(index==selected) Button(onClick=hapticClick { onSelect(index) },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(label) }
                    else OutlinedButton(onClick=hapticClick { onSelect(index) },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(label) }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .selectableGroup().navigationBarsPadding().padding(horizontal=4.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                labels.forEachIndexed { index,label ->
                    val active=index==selected
                    val tint=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=0.68f)
                    val click=hapticClick { onSelect(index) }
                    Column(
                        Modifier.weight(1f).heightIn(min=56.dp).selectable(selected=active,onClick=click,role=Role.Tab).padding(vertical=6.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center
                    ) {
                        BridgeNavIcon(index,tint)
                        Spacer(Modifier.height(5.dp))
                        Text(label,color=tint,fontSize=11.sp,fontWeight=if(active) FontWeight.Medium else FontWeight.Normal,
                            maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                            overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeNavIcon(index:Int,tint:Color) {
    Canvas(Modifier.size(24.dp)) {
        val unit=size.minDimension/24f
        fun point(x:Float,y:Float)=Offset(x*unit,y*unit)
        val stroke=Stroke(width=1.8f*unit,cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) {
            drawLine(tint,point(x1,y1),point(x2,y2),strokeWidth=1.8f*unit,cap=StrokeCap.Round)
        }
        fun path(vararg coordinates:Float,closed:Boolean=false) {
            val p=Path().apply {
                moveTo(coordinates[0]*unit,coordinates[1]*unit)
                for(i in 2 until coordinates.size step 2) lineTo(coordinates[i]*unit,coordinates[i+1]*unit)
                if(closed) close()
            }
            drawPath(p,tint,style=stroke)
        }
        fun circle(x:Float,y:Float,r:Float) { drawCircle(tint,r*unit,point(x,y),style=stroke) }
        when(index) {
            0 -> {
                path(3f,10f,12f,3f,21f,10f,21f,21f,15f,21f,15f,14f,9f,14f,9f,21f,3f,21f,closed=true)
            }
            1 -> {
                path(4f,4f,8f,4f,8f,20f,4f,20f,closed=true)
                path(10f,4f,14f,4f,14f,20f,10f,20f,closed=true)
                path(16f,5f,19f,4f,23f,19f,19f,20f,closed=true)
            }
            2 -> { circle(12f,12f,9f); path(8f,12f,11f,15f,16f,9f) }
            3 -> {
                line(4f,20f,4f,11f); line(9f,20f,9f,5f); line(14f,20f,14f,9f); line(19f,20f,19f,3f)
            }
            else -> {
                val p=Path()
                for(i in 0 until 32) {
                    val angle=(i*2.0*Math.PI/32.0)-Math.PI/2
                    val radius=if(i%4==0 || i%4==3) 10.0 else 7.7
                    val x=(12+radius*kotlin.math.cos(angle)).toFloat()*unit
                    val y=(12+radius*kotlin.math.sin(angle)).toFloat()*unit
                    if(i==0) p.moveTo(x,y) else p.lineTo(x,y)
                }
                p.close()
                drawPath(p,tint,style=stroke)
                circle(12f,12f,3.2f)
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
                Button(onClick=hapticClick(onLog),modifier=Modifier.fillMaxWidth()) { Text("Log a watch manually") }
                OutlinedButton(onClick=hapticClick(onSync),modifier=Modifier.fillMaxWidth()) { Text("Retry approved uploads") }
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
            if(hideSpoilers && !reveal) OutlinedButton(onClick=hapticClick { reveal=true }) { Text("Reveal synopsis") }
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
                if(actionLabel.isNotBlank()) OutlinedButton(onClick=hapticClick(onAction)) { Text(actionLabel) }
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
                    Button(onClick=hapticClick(onDevices),modifier=Modifier.fillMaxWidth()) { Text("Now Playing") }
                    OutlinedButton(onClick=hapticClick(onTracking),modifier=Modifier.fillMaxWidth()) { Text("Device settings") }
                }
            }
        }
    }
}
