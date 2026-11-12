package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HistoryUiRow(val key:String,val title:String,val detail:String,val source:String,val time:String)
@Composable
fun BridgeDeviceHistory(accent:Int,rows:List<HistoryUiRow>,loading:Boolean,error:Boolean) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Read-only paired-device history",style=MaterialTheme.typography.titleLarge)
            Text("Overlapping local observations are hidden. These entries never enter your Trakt upload queue.")
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                error -> Text("Unable to read paired-device history")
                rows.isEmpty() -> Text("No new paired-device records. Enable history sharing on both devices.")
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max=540.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(rows,key={it.key}) { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(row.title,style=MaterialTheme.typography.titleMedium)
                                if(row.detail.isNotBlank()) Text(row.detail)
                                Text(row.source,style=MaterialTheme.typography.bodyMedium)
                                Text(row.time,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
