package com.onelineaday.dailydiary.memory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.memory.FlashbackMemory

@Composable
fun YearBadge(
    yearOffset: Int,
    year: Int? = null,
    modifier: Modifier = Modifier
) {
    val text = if (year != null) {
        "$year · ${FlashbackMemory.formatTimeAgo(yearOffset).uppercase()}"
    } else {
        FlashbackMemory.formatTimeAgo(yearOffset).uppercase()
    }

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
