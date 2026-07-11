package com.lingqi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingqi.app.ui.LingqiScreen
import com.lingqi.app.ui.particle.LingqiParticleView
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite

@Composable
fun HomeScreen(
    onNavigate: (LingqiScreen) -> Unit,
    onQuickStart: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { LingqiParticleView(it).apply { setActive(false) } },
            update = { it.setActive(false) }
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable(onClick = onQuickStart)
                .padding(horizontal = 52.dp, vertical = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("浸入灵栖", color = LingqiWhite.copy(alpha = 0.86f), fontSize = 19.sp)
            Text(
                "TOUCH TO ENTER LINGQI",
                color = LingqiMuted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HomeNavItem(
                label = "冥想库",
                icon = { Icon(Icons.Outlined.SelfImprovement, null, tint = Color.White.copy(alpha = 0.48f)) },
                onClick = { onNavigate(LingqiScreen.MeditationLibrary) }
            )
            HomeNavItem(
                label = "睡眠",
                icon = { Icon(Icons.Outlined.Bedtime, null, tint = Color.White.copy(alpha = 0.48f)) },
                onClick = { onNavigate(LingqiScreen.Sleep) }
            )
            HomeNavItem(
                label = "我的",
                icon = { Icon(Icons.Outlined.PersonOutline, null, tint = Color.White.copy(alpha = 0.48f)) },
                onClick = { onNavigate(LingqiScreen.Profile) }
            )
        }
    }
}

@Composable
private fun HomeNavItem(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(label, color = Color.White.copy(alpha = 0.42f), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
