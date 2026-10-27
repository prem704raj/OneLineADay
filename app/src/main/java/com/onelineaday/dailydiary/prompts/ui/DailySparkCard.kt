package com.onelineaday.dailydiary.prompts.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.prompts.DailySparkPrompt
import com.onelineaday.dailydiary.prompts.PromptCategory

@Composable
fun DailySparkCard(
    prompt: DailySparkPrompt,
    isAnswered: Boolean,
    selectedCategory: PromptCategory?,
    onAnswerPrompt: (DailySparkPrompt) -> Unit,
    onShufflePrompt: () -> Unit,
    onSelectCategory: (PromptCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showCategoryMenu by remember { mutableStateOf(false) }
    val categoryColor = remember(prompt.category) { Color(prompt.category.colorHex) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = categoryColor.copy(alpha = 0.2f),
                spotColor = categoryColor.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header Row: Spark Icon + "Daily Spark" + Category Chip + Shuffle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = stringResource(R.string.daily_spark_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Category Filter Dropdown
                    Box {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedCategory != null) categoryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { showCategoryMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = selectedCategory?.displayName ?: "All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedCategory != null) categoryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = {
                                    onSelectCategory(null)
                                    showCategoryMenu = false
                                }
                            )
                            PromptCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(category.colorHex))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(category.displayName)
                                        }
                                    },
                                    onClick = {
                                        onSelectCategory(category)
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Shuffle Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShufflePrompt()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.shuffle_prompt),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Prompt Question Text
            Text(
                text = "\"${prompt.text}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Row: Category pill + Action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = categoryColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = prompt.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (isAnswered) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.prompt_answered),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAnswerPrompt(prompt)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.answer_prompt),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
