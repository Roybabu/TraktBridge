package com.traktbridge.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BridgeTheme(accent:Int,content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Color(accent),onPrimary=Color(0xff171022),background=Color(0xff0d0f16),surface=Color(0xff1b1e2a),onSurface=Color(0xfff1f0f6)),content=content)
}

@Composable
fun BridgeHeader(accent:Int,onSearch:()->Unit) {
    BridgeTheme(accent) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TRAKTBRIDGE",color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                Text("Your viewing companion",color=MaterialTheme.colorScheme.onSurface,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            }
            TextButton(onClick=onSearch) { Text("Search") }
        }
    }
}
