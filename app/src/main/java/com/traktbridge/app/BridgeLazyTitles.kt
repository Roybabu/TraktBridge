package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LazyTitleEntry(val key:String,val row:TitleUi,val actions:List<UiAction>)
@Composable
fun BridgeLazyTitles(accent:Int,entries:List<LazyTitleEntry>) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=720.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        items(entries,key={it.key}) { item -> BridgeTitleCard(accent,item.row,item.actions) }
    }
}
