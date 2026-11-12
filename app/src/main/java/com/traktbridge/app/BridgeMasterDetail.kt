package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LibraryEntry(val key:String,val title:String,val subtitle:String,val detail:String,val open:()->Unit)
@Composable
fun BridgeMasterDetail(accent:Int,entries:List<LibraryEntry>,initialKey:String,onSelection:(String)->Unit) {
    BridgeTheme(accent) {
        var selected by remember(initialKey) { mutableStateOf(initialKey) }
        val current=entries.firstOrNull { it.key==selected } ?: entries.firstOrNull()
        Row(Modifier.fillMaxWidth().heightIn(min=260.dp,max=560.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                itemsIndexed(entries,key={_,item->item.key}) { _,item ->
                    OutlinedButton(onClick={ selected=item.key; onSelection(item.key) },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),
                        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(current?.key==item.key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth()) { Text(item.title,maxLines=2); Text(item.subtitle,style=MaterialTheme.typography.bodySmall,maxLines=2) }
                    }
                }
            }
            Card(Modifier.weight(1.2f)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(current?.title ?: "Select a title",style=MaterialTheme.typography.headlineSmall)
                    if(current!=null) {
                        Text(current.subtitle)
                        Text(current.detail)
                        Button(onClick=current.open,modifier=Modifier.fillMaxWidth()) { Text("Open sessions and actions") }
                    }
                }
            }
        }
    }
}
