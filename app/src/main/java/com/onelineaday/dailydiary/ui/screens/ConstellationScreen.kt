package com.onelineaday.dailydiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.constellation.ConstellationGraph
import com.onelineaday.dailydiary.constellation.MemoryConstellationEngine
import com.onelineaday.dailydiary.constellation.StarNode
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.theme.*
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationScreen(
    entries: List<JournalEntry>,
    onBack: () -> Unit,
    onNavigateToDetail: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val graph = remember(entries) {
        MemoryConstellationEngine.buildConstellation(entries)
    }

    var selectedStar by remember { mutableStateOf<StarNode?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF070B19), // Deep cosmic void
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Memory Constellation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                        Text(
                            text = "${graph.stars.size} memories • ${graph.edges.size} celestial bonds",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFF1F5F9)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (graph.stars.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Write your first memories to begin weaving your celestial sky.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Interactive Celestial Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(graph) {
                            detectTapGestures { tapOffset ->
                                val width = size.width
                                val height = size.height
                                var closestStar: StarNode? = null
                                var minDistance = Float.MAX_VALUE

                                for (star in graph.stars) {
                                    val starX = star.x * width
                                    val starY = star.y * height
                                    val dist = sqrt((tapOffset.x - starX) * (tapOffset.x - starX) + (tapOffset.y - starY) * (tapOffset.y - starY))
                                    // Hit target radius 48dp equivalent (~120px)
                                    if (dist < 120f && dist < minDistance) {
                                        minDistance = dist
                                        closestStar = star
                                    }
                                }
                                selectedStar = closestStar
                            }
                        }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 1. Draw subtle background glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xFF1E293B).copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(canvasWidth * 0.5f, canvasHeight * 0.5f),
                            radius = canvasWidth * 0.7f
                        ),
                        radius = canvasWidth * 0.7f,
                        center = Offset(canvasWidth * 0.5f, canvasHeight * 0.5f)
                    )

                    // 2. Draw Constellation Edges (bonds between shared tags)
                    for (edge in graph.edges) {
                        val start = Offset(edge.from.x * canvasWidth, edge.from.y * canvasHeight)
                        val end = Offset(edge.to.x * canvasWidth, edge.to.y * canvasHeight)
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = edge.opacity * 0.65f),
                            start = start,
                            end = end,
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // 3. Draw Stars
                    for (star in graph.stars) {
                        val center = Offset(star.x * canvasWidth, star.y * canvasHeight)
                        val isSelected = selectedStar == star

                        val starColor = when (star.entry.mood) {
                            Mood.HAPPY, Mood.EXCITED -> Color(0xFFFDE047) // Radiant yellow
                            Mood.GRATEFUL, Mood.LOVED -> Color(0xFFF472B6) // Rose pink
                            Mood.MOTIVATED -> Color(0xFF4ADE80)           // Emerald green
                            Mood.NEUTRAL, Mood.TIRED -> Color(0xFF94A3B8)  // Moon silver
                            Mood.SAD, Mood.ANXIOUS, Mood.ANGRY -> Color(0xFF818CF8) // Indigo
                        }

                        // Outer halo glow
                        val haloRadius = if (isSelected) star.radius * 3.5f else star.radius * 2.2f
                        drawCircle(
                            color = starColor.copy(alpha = if (isSelected) 0.55f else star.brightness * 0.28f),
                            radius = haloRadius,
                            center = center
                        )

                        // Core star
                        drawCircle(
                            color = starColor.copy(alpha = star.brightness),
                            radius = if (isSelected) star.radius * 1.4f else star.radius,
                            center = center
                        )

                        // If selected, draw orbital ring
                        if (isSelected) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f),
                                radius = haloRadius + 8f,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }
                    }
                }

                // Selected Memory Preview Modal Sheet
                AnimatedVisibility(
                    visible = selectedStar != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    selectedStar?.let { star ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = star.entry.mood.emoji, fontSize = 28.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = star.entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${star.entry.mood.label} Memory",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }

                                    IconButton(onClick = { selectedStar = null }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = star.entry.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE2E8F0),
                                    maxLines = 3
                                )

                                if (star.entry.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        star.entry.tags.take(3).forEach { tag ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF334155)
                                            ) {
                                                Text(
                                                    text = "#$tag",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF38BDF8),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { onNavigateToDetail(star.entry) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF38BDF8),
                                        contentColor = Color(0xFF0F172A)
                                    )
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Full Memory")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

