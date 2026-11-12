package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PairedUiDevice(val id:String,val name:String,val online:Boolean)
@Composable
fun BridgeDevices(accent:Int,name:String,devices:List<PairedUiDevice>,sharing:Boolean,history:Boolean,controls:Boolean,onShowQr:()->Unit,onEnter:()->Unit,onImport:()->Unit,onRevoke:(String)->Unit,onSettings:()->Unit) {
    BridgeTheme(accent) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(name,style=MaterialTheme.typography.headlineSmall)
            Text("Playback: "+if(sharing) "enabled" else "disabled")
            Text("History: "+(if(history) "enabled" else "disabled")+" • Remote controls: "+(if(controls) "enabled" else "disabled"))
            Text("On both devices, exchange an invitation and compare the 8-digit verification PIN. Approval is required on each device.")
            Button(onClick=onShowQr,modifier=Modifier.fillMaxWidth()) { Text("1. Show my QR and PIN") }
            OutlinedButton(onClick=onEnter,modifier=Modifier.fillMaxWidth()) { Text("2. Verify other device") }
            OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text("Read QR from image") }
            Text("Approved devices",style=MaterialTheme.typography.titleLarge)
            if(devices.isEmpty()) Text("No devices paired")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(devices,key={it.id}) { device ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(device.name,style=MaterialTheme.typography.titleMedium)
                                Text(if(device.online) "Fresh connection" else "Not currently connected")
                                Text(device.id.take(12),style=MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick={onRevoke(device.id)}) { Text("Revoke") }
                        }
                    }
                }
            }
            OutlinedButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()) { Text("Sharing and privacy settings") }
        }
    }
}
