package com.traktbridge.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModernSessionDetails(accent:Int,item:PosterLibraryItem,status:String,metadata:String,sampled:String,position:String,evidence:String,primary:UiAction?,actions:List<UiAction>) {
    BridgeTheme(accent) {
        var expanded by remember { mutableStateOf(false) }
        var menu by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth().padding(vertical=8.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    LibraryPoster(item,Modifier.width(88.dp).aspectRatio(2f/3f))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(item.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                        Text(metadata,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(status,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                        Text(sampled,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(actions.isNotEmpty()) Box {
                        TextButton(onClick={menu=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.size(48.dp)) { Text("⋯",style=MaterialTheme.typography.headlineSmall) }
                        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                            actions.forEach { action ->
                                DropdownMenuItem(text={Text(action.label,color=if(action.label.startsWith("Delete") || action.label.startsWith("Remove")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)},onClick={menu=false;action.run()})
                            }
                        }
                    }
                }
                item.progress?.let { progress ->
                    LinearProgressIndicator(progress={progress.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(5.dp))
                    Text(position,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(primary!=null) Button(onClick=primary.run,modifier=Modifier.fillMaxWidth()) { Text(primary.label) }
                if(evidence.isNotBlank()) {
                    TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Hide playback details" else "Playback details  ›") }
                    if(expanded) Text(evidence.trim(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ModernIdentifyDialog(accent:Int,initial:String,onSearch:(String,String)->Unit,onExternal:()->Unit,learned:UiAction?,onClose:()->Unit) {
    BridgeTheme(accent) {
        var query by remember { mutableStateOf(initial) }
        var kind by remember { mutableStateOf("show") }
        Surface(color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Identify title",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                Text("Find the exact movie or episode on Trakt.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(value=query,onValueChange={query=it},label={Text("Title")},modifier=Modifier.fillMaxWidth(),maxLines=3)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=kind=="show",onClick={kind="show"},label={Text("TV / Anime")})
                    FilterChip(selected=kind=="movie",onClick={kind="movie"},label={Text("Movie")})
                }
                Button(onClick={onSearch(query.trim(),kind)},enabled=query.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("Search Trakt") }
                TextButton(onClick=onExternal) { Text("Search by IMDb / TMDB ID") }
                if(learned!=null) TextButton(onClick=learned.run) { Text(learned.label) }
                Text("Matching does not mark a title watched.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
fun ModernActionDialog(accent:Int,title:String,detail:String,labels:List<String>,onSelect:(Int)->Unit,onClose:()->Unit) {
    BridgeTheme(accent) {
        Surface(color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                labels.forEachIndexed { index,label ->
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { onSelect(index) }.padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(label,Modifier.weight(1f),color=if(label.startsWith("Remove")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("›",color=MaterialTheme.colorScheme.primary)
                    }
                    if(index<labels.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
                TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}
