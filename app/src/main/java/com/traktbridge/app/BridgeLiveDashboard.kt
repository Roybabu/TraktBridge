package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LiveUiSession(val key:String,val device:String,val title:String,val detail:String,val provider:String,val state:String,val progress:Int?,val timing:String,val evidence:String="")
@Composable
fun BridgeLiveDashboard(accent:Int,rows:List<LiveUiSession>,status:String,onControls:()->Unit,onContinue:()->Unit,onSettings:()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(status,style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onControls,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Controls") }
                Button(onClick=onContinue,modifier=Modifier.weight(1f).heightIn(min=48.dp)) { Text("Continue here") }
            }
            if(rows.isEmpty()) {
                Text("No fresh playback sessions. Start tracking on each paired device.")
                OutlinedButton(onClick=onSettings) { Text("Device settings") }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=520.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(rows,key={it.key}) { row ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(row.device,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                            Text(row.title,style=MaterialTheme.typography.titleLarge)
                            if(row.detail.isNotBlank()) Text(row.detail)
                            Text(row.provider+" • "+row.state,style=MaterialTheme.typography.bodyMedium)
                            row.progress?.let { LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth()) }
                            if(row.timing.isNotBlank()) Text(row.timing,style=MaterialTheme.typography.bodySmall)
                            if(row.evidence.isNotBlank()) Text(row.evidence,style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
