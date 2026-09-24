package com.onelineaday.dailydiary.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun PinEntryScreen(
    title: String,
    subtitle: String? = null,
    onPinComplete: (String) -> Unit,
    onBiometricFallback: (() -> Unit)? = null,
    errorMessage: String? = null
) {
    var enteredDigits by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(enteredDigits) {
        if (enteredDigits.length == 4) {
            onPinComplete(enteredDigits)
            enteredDigits = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(title, style = MaterialTheme.typography.headlineSmall)
        
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < enteredDigits.length) 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(modifier = Modifier.height(24.dp)) {
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        val pad = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(if (onBiometricFallback != null) "BIO" else "", "0", "DEL")
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in pad) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable(enabled = key.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (key) {
                                        "DEL" -> if (enteredDigits.isNotEmpty()) enteredDigits = enteredDigits.dropLast(1)
                                        "BIO" -> onBiometricFallback?.invoke()
                                        else -> if (enteredDigits.length < 4 && key.isNotEmpty()) enteredDigits += key
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (key) {
                                "DEL" -> Icon(Icons.Rounded.Backspace, contentDescription = "Delete")
                                "BIO" -> Icon(Icons.Rounded.Fingerprint, contentDescription = "Biometric", modifier = Modifier.size(32.dp))
                                "" -> { }
                                else -> Text(key, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
