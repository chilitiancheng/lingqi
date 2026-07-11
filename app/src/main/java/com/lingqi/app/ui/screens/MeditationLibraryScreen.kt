package com.lingqi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingqi.app.data.MeditationKind
import com.lingqi.app.meditation.MeditationCatalog
import com.lingqi.app.ui.components.ScreenHeader
import com.lingqi.app.ui.components.SectionTitle
import com.lingqi.app.ui.theme.LingqiLine
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite

@Composable
fun MeditationLibraryScreen(
    onBack: () -> Unit,
    onStart: (MeditationKind, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
    ) {
        item { ScreenHeader("冥想", "六种练习，按此刻的需要进入", onBack) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                SectionTitle("练习库", "5 · 10 · 20 分钟")
                Spacer(Modifier.height(14.dp))
            }
        }
        items(MeditationCatalog.practices) { kind ->
            PracticeRow(kind = kind, onStart = { minutes -> onStart(kind, minutes) })
        }
    }
}

@Composable
private fun PracticeRow(kind: MeditationKind, onStart: (Int) -> Unit) {
    var selectedMinutes by remember { mutableStateOf(if (kind == MeditationKind.BREATH_478) 5 else 10) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .background(Color(0xFF0A0B0A), RoundedCornerShape(6.dp))
            .padding(18.dp)
    ) {
        Text(kind.title, color = LingqiWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(kind.subtitle, color = LingqiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MeditationCatalog.durationMinutes.forEach { minutes ->
                Text(
                    "$minutes 分钟",
                    color = if (selectedMinutes == minutes) Color.Black else LingqiWhite,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(
                            if (selectedMinutes == minutes) LingqiWhite else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { selectedMinutes = minutes }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "开始",
                color = LingqiWhite,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(LingqiLine, RoundedCornerShape(4.dp))
                    .clickable { onStart(selectedMinutes) }
                    .padding(horizontal = 15.dp, vertical = 10.dp)
            )
        }
    }
}
