package com.traktbridge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

data class PlannerEntry(val poster:PosterLibraryItem,val remaining:Long)
private fun minutes(ms:Long):String=if(ms<0) "Time unknown" else ((ms+59999)/60000).toString()+" min"

@Composable
fun ModernToolPage(accent:Int,title:String,onClose:()->Unit,content:@Composable ()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DetailHeader(title,onClose)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun PlannerRow(entry:PlannerEntry,onClose:()->Unit) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable { onClose(); entry.poster.open() }.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            LibraryPoster(entry.poster,Modifier.width(56.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f)) {
                Text(entry.poster.title,fontWeight=FontWeight.SemiBold)
                Text(entry.poster.subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(minutes(entry.remaining)+" remaining",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
            }
            Text("›",color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ModernViewingPlanner(accent:Int,entries:List<PlannerEntry>,onPlan:(Long)->Unit,onRandom:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Viewing planner",onClose) {
        var budget by remember { mutableIntStateOf(60) }
        val fits=remember(entries,budget) { entries.filter { it.remaining in 1..budget*60000L }.sortedBy { it.remaining } }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            item {
                Text("How much time do you have?",style=MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf(30,60,90,120,180).forEach { value -> FilterChip(selected=budget==value,onClick={budget=value},label={Text("$value min")}) }
                }
                Text(fits.size.toString()+" unfinished titles fit your time",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(fits.isEmpty()) item { Text("No unfinished titles with known remaining time fit this budget.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(fits.take(30)) { PlannerRow(it,onClose) }
            item { Button(onClick={onPlan(budget*60000L)},enabled=fits.isNotEmpty(),modifier=Modifier.fillMaxWidth()) { Text("Build viewing plan") } }
            item { ModernDetailAction("Surprise me from my Trakt watchlist",onRandom) }
        }
    }
}

@Composable
fun ModernPlannerResults(accent:Int,title:String,entries:List<PlannerEntry>,onClose:()->Unit) {
    ModernToolPage(accent,title,onClose) {
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(entries.isEmpty()) item { Text("No matching unfinished titles.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(entries) { PlannerRow(it,onClose) }
        }
    }
}

@Composable
fun ModernSearchEntry(accent:Int,onSearch:(String)->Unit,onAssistant:()->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Search",onClose) {
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query,{query=it},label={Text("Movies, shows or anime")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Button(onClick={onSearch(query.trim())},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Search library and catalogue") }
            HorizontalDivider()
            ModernDetailAction("Viewing assistant",onAssistant)
            Text("Find viewing history, unfinished titles and time-based suggestions.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ModernAssistant(accent:Int,onCommand:(String)->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Viewing assistant",onClose) {
        var command by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("What would you like to find?",style=MaterialTheme.typography.titleLarge)
            Text("Local command matching. No external AI service.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(command,{command=it},label={Text("Ask about your viewing")},modifier=Modifier.fillMaxWidth(),maxLines=3)
            Button(onClick={onCommand(command.trim())},enabled=command.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Find") }
            Text("Try a suggestion",style=MaterialTheme.typography.titleMedium)
            listOf("Unfinished movies","90 minutes","Viewing history","Recommend something","Subscriptions").forEach { example -> ModernDetailAction(example) { onCommand(example) } }
        }
    }
}

@Composable
fun ModernDashboardSettings(accent:Int,initialOrder:List<String>,initialHidden:Set<String>,initialStart:Int,initialWidget:String,onSave:(List<String>,Set<String>,Int,String)->Unit,onClose:()->Unit) {
    ModernToolPage(accent,"Customize dashboard",onClose) {
        var order by remember { mutableStateOf(initialOrder) }
        var hidden by remember { mutableStateOf(initialHidden) }
        var start by remember { mutableIntStateOf(initialStart) }
        var widget by remember { mutableStateOf(initialWidget) }
        val labels=mapOf("metrics" to "Statistics","goal" to "Monthly goal","now" to "Now playing","continue" to "Continue watching and Up next","review" to "Review queue","discover" to "Explore","recent" to "Recently watched")
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Home sections",style=MaterialTheme.typography.titleLarge)
                Text("Turn sections on or off and arrange their order.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                order.forEachIndexed { index,key ->
                    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(labels[key] ?: key,Modifier.weight(1f))
                            TextButton(onClick={order=order.toMutableList().apply { add(index-1,removeAt(index)) }},enabled=index>0,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)) { Text("↑") }
                            TextButton(onClick={order=order.toMutableList().apply { add(index+1,removeAt(index)) }},enabled=index<order.lastIndex,contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)) { Text("↓") }
                            Switch(checked=key !in hidden,onCheckedChange={ checked -> hidden=if(checked) hidden-key else hidden+key })
                        }
                    }
                }
                Text("Open app on",style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Home","Library","Review","Activity").forEachIndexed { index,label -> FilterChip(selected=start==index,onClick={start=index},label={Text(label)}) }
                }
                Text("Home-screen widget",style=MaterialTheme.typography.titleMedium)
                listOf("private" to "Private (no titles)","continue" to "Continue watching","review" to "Review count").forEach { (key,label) ->
                    Row(Modifier.fillMaxWidth().clickable { widget=key },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=widget==key,onClick={widget=key}); Text(label) }
                }
            }
            Button(onClick={onSave(order,hidden,start,widget)},modifier=Modifier.fillMaxWidth().padding(top=8.dp)) { Text("Save dashboard") }
        }
    }
}
