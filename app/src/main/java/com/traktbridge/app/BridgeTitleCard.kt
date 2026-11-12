package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView

data class TitleUi(val title:String,val platforms:String,val sessions:String,val status:String,val sampled:String,val progress:Int?,val position:String,val diagnostics:String,val artworkType:String,val artworkId:Long)
data class UiAction(val label:String,val run:()->Unit)
@Composable
fun BridgeTitleCard(accent:Int,row:TitleUi,actions:List<UiAction>) {
    BridgeTheme(accent) {
        Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    AndroidView(factory={ context -> ImageView(context).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play) } },
                        update={ image -> image.contentDescription="Poster for "+row.title; if(row.artworkId>0) Artwork.load(image.context,row.artworkType,row.artworkId,image) },
                        modifier=Modifier.width(84.dp).height(126.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(row.title,style=MaterialTheme.typography.titleLarge)
                        Text(row.platforms,style=MaterialTheme.typography.bodySmall)
                        Text(row.sessions,style=MaterialTheme.typography.bodySmall)
                        Text(row.status,color=MaterialTheme.colorScheme.primary)
                    }
                }
                Text(row.sampled,style=MaterialTheme.typography.bodyMedium)
                if(row.position.isNotBlank()) Text(row.position,style=MaterialTheme.typography.bodySmall)
                row.progress?.let { LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth()) }
                if(row.diagnostics.isNotBlank()) Text(row.diagnostics,style=MaterialTheme.typography.bodySmall)
                for(action in actions) OutlinedButton(onClick=action.run,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(action.label) }
            }
        }
    }
}
