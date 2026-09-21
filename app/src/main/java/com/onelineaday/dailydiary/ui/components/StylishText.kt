package com.onelineaday.dailydiary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.ui.theme.*

/**
 * Stylish gradient text input field with animated colors
 */
@Composable
fun StylishTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Write your line for today...",
    modifier: Modifier = Modifier
) {
    // Animated gradient offset for shimmering effect
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    // Beautiful gradient colors
    val gradientColors = listOf(
        GradientStart,
        GradientMiddle,
        GradientEnd,
        AccentTeal,
        GradientStart
    )
    
    // Create animated gradient brush for text
    val textBrush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(offset, 0f),
        end = Offset(offset + 500f, 200f)
    )
    
    // Background gradient
    val bgGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        )
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgGradient)
            .padding(20.dp)
    ) {
        if (value.isEmpty()) {
            // Animated placeholder
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                brush = if (value.isNotEmpty()) textBrush else null,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                lineHeight = 28.sp,
                shadow = Shadow(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f
                )
            ),
            cursorBrush = Brush.verticalGradient(
                colors = listOf(GradientStart, GradientEnd)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Display styled entry text with gradient effect
 */
@Composable
fun StylishEntryText(
    text: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "textGradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textOffset"
    )
    
    val gradientColors = listOf(
        SunsetOrange,
        SunsetRose,
        Color(0xFFAA66FF),  // Purple
        AccentTeal,
        SunsetAmber
    )
    
    val textBrush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(offset, 0f),
        end = Offset(offset + 400f, 100f)
    )
    
    Text(
        text = text,
        style = TextStyle(
            brush = textBrush,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            lineHeight = 32.sp,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.15f),
                offset = Offset(0f, 3f),
                blurRadius = 6f
            )
        ),
        modifier = modifier
    )
}

/**
 * Static styled text for timeline cards (less CPU intensive)
 */
@Composable
fun StylishCardText(
    text: String,
    maxLines: Int = 3,
    modifier: Modifier = Modifier
) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.onSurface
    )
    
    val textBrush = Brush.horizontalGradient(colors = gradientColors)
    
    Text(
        text = text,
        style = TextStyle(
            brush = textBrush,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            lineHeight = 24.sp
        ),
        maxLines = maxLines,
        modifier = modifier
    )
}
