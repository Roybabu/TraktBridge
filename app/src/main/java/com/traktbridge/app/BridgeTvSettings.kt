package com.traktbridge.app
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun BridgeTvSettings(accent:Int,actions:List<UiAction>) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(accent))) {
        Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("TV quick settings",fontSize=28.sp)
            actions.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach { action -> TvAction(action.label,"",Modifier.weight(1f),action.run) }
                    if(pair.size==1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
