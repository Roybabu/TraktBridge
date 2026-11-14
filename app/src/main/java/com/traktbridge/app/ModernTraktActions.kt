package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModernTraktActions(accent:Int,title:String,initial:RemoteTitleState,onWatchlist:(Boolean,(Boolean)->Unit)->Unit,onRating:(Int,(Boolean)->Unit)->Unit,onClose:()->Unit) {
    BridgeTheme(accent) {
        var watchlisted by remember { mutableStateOf(initial.watchlisted) }
        var rating by remember { mutableIntStateOf(initial.rating) }
        var busy by remember { mutableStateOf(false) }
        var choosing by remember { mutableStateOf(false) }
        Surface(color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text("Manage on Trakt",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if(watchlisted) "Added to watchlist" else "Add to watchlist",fontWeight=FontWeight.Medium)
                        Text(if(watchlisted) "Tap to remove" else "Save for later",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked=watchlisted,enabled=!busy,onCheckedChange={ next ->
                        busy=true; onWatchlist(next) { ok -> if(ok) watchlisted=next; busy=false }
                    })
                }
                HorizontalDivider()
                TextButton(onClick={choosing=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Text(if(rating>0) "Your rating: "+rating+"/10 · Edit" else "Rate title")
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick=onClose,modifier=Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
        if(choosing) {
            AlertDialog(onDismissRequest={choosing=false},title={Text("Your rating")},text={
                Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                    for(value in 1..10) TextButton(onClick={
                        choosing=false; busy=true; onRating(value) { ok -> if(ok) rating=value; busy=false }
                    },modifier=Modifier.fillMaxWidth()) { Text(value.toString()+"/10"+if(value==rating) " · Current" else "") }
                }
            },confirmButton={TextButton(onClick={choosing=false}) {Text("Cancel")}},dismissButton={
                if(rating>0) TextButton(onClick={choosing=false;busy=true;onRating(0){ok->if(ok) rating=0;busy=false}}) {Text("Remove rating")}
            })
        }
    }
}
