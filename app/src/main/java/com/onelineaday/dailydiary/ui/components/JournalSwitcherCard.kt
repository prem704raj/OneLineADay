package com.onelineaday.dailydiary.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.Journal

@Composable
fun JournalSwitcherCard(
    journals: List<Journal>,
    activeJournalId: String,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit,
    isDuoJournal: (String) -> Boolean = { it.startsWith("duo_") },
    onVerifyDuoPin: (String) -> Boolean = { true },
    onCreateDuo: (partnerName: String, pin: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isDuoMode by remember { mutableStateOf(false) }
    var partnerName by remember { mutableStateOf("") }
    var duoPin by remember { mutableStateOf("") }

    var pendingDuoJournal by remember { mutableStateOf<Journal?>(null) }
    var pinAttempt by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val active = journals.firstOrNull { it.id == activeJournalId }
    val isCurrentDuo = active?.let { isDuoJournal(it.id) } == true

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCurrentDuo) Icons.Rounded.People else Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentDuo) "Current Partner Space" else stringResource(R.string.current_journal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = active?.name ?: stringResource(R.string.default_journal_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = stringResource(R.string.change),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(stringResource(R.string.choose_journal))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    journals.forEach { journal ->
                        val isDuo = isDuoJournal(journal.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isDuo) {
                                        pendingDuoJournal = journal
                                        pinAttempt = ""
                                        pinError = false
                                    } else {
                                        onSelect(journal.id)
                                        showDialog = false
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDuo) Icons.Rounded.People else Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = if (isDuo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = journal.name,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (journal.id == activeJournalId) FontWeight.Bold else FontWeight.Normal
                            )

                            if (isDuo) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "PIN Protected",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }

                            if (journal.id == activeJournalId) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        HorizontalDivider()
                    }

                    Spacer(Modifier.height(8.dp))

                    // Toggle between Standard and Duo Journal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isDuoMode,
                            onClick = { isDuoMode = false },
                            label = { Text("Standard") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = isDuoMode,
                            onClick = { isDuoMode = true },
                            label = { Text("👥 Duo Partner") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!isDuoMode) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it.take(40) },
                            label = { Text(stringResource(R.string.new_journal_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val name = newName.trim()
                                if (name.isNotBlank()) {
                                    onCreate(name)
                                    newName = ""
                                    showDialog = false
                                }
                            },
                            enabled = newName.trim().isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.create_journal))
                        }
                    } else {
                        OutlinedTextField(
                            value = partnerName,
                            onValueChange = { partnerName = it.take(25) },
                            label = { Text("Partner Name") },
                            placeholder = { Text("e.g. Alex, Sarah") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = duoPin,
                            onValueChange = { if (it.length <= 6) duoPin = it.filter { char -> char.isDigit() } },
                            label = { Text("Set 4-Digit Duo PIN") },
                            placeholder = { Text("••••") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val pName = partnerName.trim()
                                val pin = duoPin.trim()
                                if (pName.isNotBlank() && pin.length >= 4) {
                                    onCreateDuo(pName, pin)
                                    partnerName = ""
                                    duoPin = ""
                                    showDialog = false
                                }
                            },
                            enabled = partnerName.trim().isNotBlank() && duoPin.trim().length >= 4,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.People, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create Duo Journal")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Duo PIN Entry Dialog
    pendingDuoJournal?.let { journal ->
        AlertDialog(
            onDismissRequest = { pendingDuoJournal = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock ${journal.name}")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your secret Duo PIN to switch to this shared journal partition.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = {
                            pinAttempt = it.filter { c -> c.isDigit() }
                            pinError = false
                        },
                        label = { Text("Duo PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError,
                        supportingText = if (pinError) {
                            { Text("Incorrect PIN. Please try again.") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onVerifyDuoPin(pinAttempt)) {
                            onSelect(journal.id)
                            pendingDuoJournal = null
                            showDialog = false
                        } else {
                            pinError = true
                        }
                    },
                    enabled = pinAttempt.isNotBlank()
                ) {
                    Text("Unlock & Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuoJournal = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
