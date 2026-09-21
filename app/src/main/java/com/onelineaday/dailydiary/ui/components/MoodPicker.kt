package com.onelineaday.dailydiary.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.theme.*

@Composable
fun MoodPicker(
    selectedMood: Mood,
    onMoodSelected: (Mood) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "How are you feeling?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // First row of moods
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Mood.entries.take(5).forEach { mood ->
                MoodItem(
                    mood = mood,
                    isSelected = selectedMood == mood,
                    onClick = { onMoodSelected(mood) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Second row of moods
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Mood.entries.drop(5).forEach { mood ->
                MoodItem(
                    mood = mood,
                    isSelected = selectedMood == mood,
                    onClick = { onMoodSelected(mood) }
                )
            }
        }
    }
}

@Composable
fun MoodItem(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mood_scale"
    )
    
    val backgroundColor = if (isSelected) {
        getMoodColor(mood).copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size((40 * scale).dp)
                .then(
                    if (isSelected) {
                        Modifier
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(GradientStart, GradientEnd)
                                ),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = mood.emoji,
                fontSize = (24 * scale).sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = mood.label,
                style = MaterialTheme.typography.labelSmall,
                color = getMoodColor(mood),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MoodBadge(
    mood: Mood,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(getMoodColor(mood).copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = mood.emoji,
            fontSize = 16.sp
        )
        
        if (showLabel) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = mood.label,
                style = MaterialTheme.typography.labelSmall,
                color = getMoodColor(mood)
            )
        }
    }
}

@Composable
fun getMoodColor(mood: Mood): Color {
    return when (mood) {
        Mood.HAPPY -> MoodHappy
        Mood.NEUTRAL -> MoodNeutral
        Mood.SAD -> MoodSad
        Mood.ANGRY -> MoodAngry
        Mood.EXCITED -> MoodExcited
        Mood.TIRED -> MoodTired
        Mood.MOTIVATED -> MoodMotivated
        Mood.LOVED -> MoodLoved
        Mood.ANXIOUS -> MoodAnxious
        Mood.GRATEFUL -> MoodGrateful
    }
}
