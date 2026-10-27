package com.onelineaday.dailydiary.memory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LeapDayBadge(
    modifier: Modifier = Modifier,
    isObserved: Boolean = false,
    originalYear: Int? = null
) {
    val text = when {
        isObserved && originalYear != null -> "✨ Observed from Feb 29, $originalYear"
        isObserved -> "✨ Observed from Feb 29"
        else -> "✨ Leap Day Memory"
    }

    val gradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF9333EA), // Purple-600
            Color(0xFFD97706)  // Amber-600
        )
    )

    Box(
        modifier = modifier
            .background(
                brush = gradient,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}
