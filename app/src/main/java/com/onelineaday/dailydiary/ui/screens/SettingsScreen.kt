package com.onelineaday.dailydiary.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.onelineaday.dailydiary.security.AppLockManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.ads.AdsConsentManager
import com.onelineaday.dailydiary.ads.RewardedAdManager
import com.onelineaday.dailydiary.ui.components.JournalSwitcherCard
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onelineaday.dailydiary.ads.BannerAdView
import com.onelineaday.dailydiary.notifications.ReminderManager
import com.onelineaday.dailydiary.ui.theme.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.onelineaday.dailydiary.viewmodel.JournalUiState
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import com.onelineaday.dailydiary.backup.BackupExportResult
import com.onelineaday.dailydiary.backup.BackupRestoreResult
import com.onelineaday.dailydiary.backup.EncryptedBackupManager
import com.onelineaday.dailydiary.backup.CloudBackupManager
import com.onelineaday.dailydiary.backup.cloud.CloudBackupPrefs
import com.onelineaday.dailydiary.backup.crypto.BackupContainerType
import com.onelineaday.dailydiary.data.JournalDatabase
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.onelineaday.dailydiary.utils.PdfExportHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JournalViewModel,
    uiState: JournalUiState,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    currentAppTheme: com.onelineaday.dailydiary.ui.theme.AppTheme = com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT,
    onThemeChange: (com.onelineaday.dailydiary.ui.theme.AppTheme) -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    var isExporting by remember { mutableStateOf(false) }
    var showAdDialog by remember { mutableStateOf(false) }
    var adRewarded by remember { mutableStateOf(false) }
    
    // Pre-load rewarded ad when Settings screen opens
    LaunchedEffect(Unit) {
        RewardedAdManager.loadAd(context)
    }
    
    // Notification settings
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("reminders_enabled", false)) }
    var reminderHour by remember { mutableStateOf(prefs.getInt("reminder_hour", 20)) }
    var reminderMinute by remember { mutableStateOf(prefs.getInt("reminder_minute", 0)) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }
    var pinSetupState by remember { mutableStateOf<String?>(null) }
    var tempPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var hasPin by remember { mutableStateOf(com.onelineaday.dailydiary.security.AppLockManager.hasPin(context)) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    val isPremium by com.onelineaday.dailydiary.PremiumManager.isPremium.collectAsState()

    val languages = listOf(
        "en" to "English",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "ur" to "Urdu",
        "es" to "Spanish",
        "pt" to "Portuguese",
        "fr" to "French",
        "de" to "German",
        "ar" to "Arabic",
        "id" to "Indonesian",
        "tr" to "Turkish",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese (Simplified)"
    )

    val currentLanguageCode = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(android.app.LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) locales.get(0)?.language ?: "en" else "en"
        } else {
            prefs.getString("language", "en") ?: "en"
        }
    }
    
    val currentLanguageName = languages.find { it.first == currentLanguageCode }?.second ?: "English"
    
    // Permission launcher for notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            notificationsEnabled = true
            prefs.edit().putBoolean("reminders_enabled", true).apply()
            ReminderManager(context).scheduleDailyReminder(reminderHour, reminderMinute)
            Toast.makeText(context, context.getString(R.string.toast_reminders_enabled), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }
    
    var isRestoringBackup by remember { mutableStateOf(false) }
    var isExportingBackup by remember { mutableStateOf(false) }
    var showBackupPrivacyDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePreview by remember { mutableStateOf<com.onelineaday.dailydiary.backup.BackupPreviewResult.Success?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val cloudBackupPrefs = remember { CloudBackupPrefs(context) }
    var showPassphraseSetupDialog by remember { mutableStateOf(false) }
    var showWebDavConfigDialog by remember { mutableStateOf(false) }
    var showProviderSelectDialog by remember { mutableStateOf(false) }
    var showRestorePassphraseDialog by remember { mutableStateOf(false) }
    var pendingEncryptedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var enteredRestorePassphrase by remember { mutableStateOf("") }
    var isLegacyRestore by remember { mutableStateOf(false) }
    var isPassphraseSaved by remember { mutableStateOf(cloudBackupPrefs.hasSavedPassphrase()) }
    var activeProvider by remember { mutableStateOf(cloudBackupPrefs.getActiveProviderType()) }
    var lastSyncStatus by remember { mutableStateOf(cloudBackupPrefs.getLastBackupStatus()) }
    var lastSyncTime by remember { mutableStateOf(cloudBackupPrefs.getLastBackupTimestamp()) }

    var showRestDaysDialog by remember { mutableStateOf(false) }
    var showAiKeyDialog by remember { mutableStateOf(false) }
    val aiKeyManager = remember { viewModel.aiKeyManager }
    var isAiOptIn by remember { mutableStateOf(aiKeyManager.isCloudAiOptIn()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isRestoringBackup = true
                val db = JournalDatabase.getDatabase(context)
                val encManager = EncryptedBackupManager(context, db)
                when (val containerType = encManager.detectContainerType(uri)) {
                    is BackupContainerType.Encrypted -> {
                        val saved = cloudBackupPrefs.getSavedPassphrase()
                        if (saved != null && saved.isNotEmpty()) {
                            when (val preview = encManager.inspectBackup(uri, saved)) {
                                is com.onelineaday.dailydiary.backup.BackupPreviewResult.Success -> {
                                    pendingRestoreUri = uri
                                    enteredRestorePassphrase = String(saved)
                                    restorePreview = preview
                                    isLegacyRestore = false
                                    showRestoreConfirmDialog = true
                                }
                                else -> {
                                    pendingEncryptedRestoreUri = uri
                                    enteredRestorePassphrase = ""
                                    showRestorePassphraseDialog = true
                                }
                            }
                        } else {
                            pendingEncryptedRestoreUri = uri
                            enteredRestorePassphrase = ""
                            showRestorePassphraseDialog = true
                        }
                    }
                    is BackupContainerType.LegacyZip -> {
                        when (val preview = encManager.inspectBackup(uri, null)) {
                            is com.onelineaday.dailydiary.backup.BackupPreviewResult.Success -> {
                                pendingRestoreUri = uri
                                restorePreview = preview
                                isLegacyRestore = true
                                showRestoreConfirmDialog = true
                            }
                            is com.onelineaday.dailydiary.backup.BackupPreviewResult.Failure -> {
                                Toast.makeText(context, context.getString(R.string.restore_failed, preview.reason), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is BackupContainerType.Invalid -> {
                        Toast.makeText(context, "Selected file is not a valid OneLineADay backup archive.", Toast.LENGTH_LONG).show()
                    }
                }
                isRestoringBackup = false
            }
        }
    }

    if (showRestorePassphraseDialog && pendingEncryptedRestoreUri != null) {
        var passInput by remember { mutableStateOf("") }
        var passError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = {
                showRestorePassphraseDialog = false
                pendingEncryptedRestoreUri = null
            },
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Encrypted Backup Passphrase") },
            text = {
                Column {
                    Text(
                        "This backup is protected with AES-256-GCM encryption. Enter the passphrase created when this backup was exported.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it; passError = null },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passError != null,
                        supportingText = passError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passInput.isEmpty()) {
                            passError = "Passphrase cannot be empty"
                            return@Button
                        }
                        scope.launch {
                            isRestoringBackup = true
                            val db = JournalDatabase.getDatabase(context)
                            val encManager = EncryptedBackupManager(context, db)
                            val uri = pendingEncryptedRestoreUri!!
                            when (val preview = encManager.inspectBackup(uri, passInput.toCharArray())) {
                                is com.onelineaday.dailydiary.backup.BackupPreviewResult.Success -> {
                                    showRestorePassphraseDialog = false
                                    pendingRestoreUri = uri
                                    enteredRestorePassphrase = passInput
                                    restorePreview = preview
                                    isLegacyRestore = false
                                    showRestoreConfirmDialog = true
                                }
                                is com.onelineaday.dailydiary.backup.BackupPreviewResult.Failure -> {
                                    passError = "Incorrect passphrase or corrupted archive"
                                }
                            }
                            isRestoringBackup = false
                        }
                    }
                ) {
                    Text("Unlock & Preview")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestorePassphraseDialog = false
                        pendingEncryptedRestoreUri = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRestoreConfirmDialog && restorePreview != null && pendingRestoreUri != null) {
        val preview = restorePreview!!
        val uriToRestore = pendingRestoreUri!!
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
                restorePreview = null
            },
            title = {
                Text(stringResource(R.string.restore_backup_confirm_title))
            },
            text = {
                Column {
                    if (isLegacyRestore) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "Notice: Unencrypted Legacy Archive. We recommend exporting an encrypted backup after restore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Text(
                        stringResource(
                            R.string.restore_backup_confirm_message,
                            preview.entryCount,
                            preview.journalCount,
                            preview.minDate ?: "—",
                            preview.maxDate ?: "—"
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        scope.launch {
                            isRestoringBackup = true
                            val db = JournalDatabase.getDatabase(context)
                            val encManager = EncryptedBackupManager(context, db)
                            val passChars = if (isLegacyRestore) null else enteredRestorePassphrase.toCharArray()
                            when (val result = encManager.restoreBackup(uriToRestore, passChars)) {
                                is BackupRestoreResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.restore_success, result.restoredEntries, result.restoredJournals),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    viewModel.restoreBackup(uriToRestore)
                                }
                                is BackupRestoreResult.Failure -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.restore_failed, result.reason),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            isRestoringBackup = false
                            pendingRestoreUri = null
                            restorePreview = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.restore_backup_proceed))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                        restorePreview = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPassphraseSetupDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var saveInKeystore by remember { mutableStateOf(true) }
        var passError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPassphraseSetupDialog = false },
            icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Backup Recovery Passphrase") },
            text = {
                Column {
                    Text(
                        "Set a strong recovery passphrase to encrypt your backups with AES-256-GCM. Your passphrase is never sent to any server.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it; passError = null },
                        label = { Text("Passphrase (min 6 characters)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; passError = null },
                        label = { Text("Confirm Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passError != null,
                        supportingText = passError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { saveInKeystore = !saveInKeystore }
                    ) {
                        Checkbox(checked = saveInKeystore, onCheckedChange = { saveInKeystore = it })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Save securely in Android Keystore for automated background backup",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.length < 6) {
                            passError = "Passphrase must be at least 6 characters"
                            return@Button
                        }
                        if (newPass != confirmPass) {
                            passError = "Passphrases do not match"
                            return@Button
                        }
                        if (saveInKeystore) {
                            cloudBackupPrefs.savePassphrase(newPass.toCharArray())
                            isPassphraseSaved = true
                        } else {
                            cloudBackupPrefs.clearSavedPassphrase()
                            isPassphraseSaved = false
                        }
                        showPassphraseSetupDialog = false
                        Toast.makeText(context, "Passphrase configured successfully", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseSetupDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showProviderSelectDialog) {
        val providers = listOf(
            "none" to "None (Local Only)",
            "google_drive" to "Google Drive (App Data folder)",
            "webdav" to "WebDAV (Nextcloud / NAS)"
        )
        AlertDialog(
            onDismissRequest = { showProviderSelectDialog = false },
            icon = { Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Select Cloud Storage Provider") },
            text = {
                Column {
                    providers.forEach { (id, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeProvider = id
                                    cloudBackupPrefs.setActiveProviderType(id)
                                    showProviderSelectDialog = false
                                    if (id == "webdav" && cloudBackupPrefs.getWebDavServerUrl().isBlank()) {
                                        showWebDavConfigDialog = true
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (activeProvider == id),
                                onClick = {
                                    activeProvider = id
                                    cloudBackupPrefs.setActiveProviderType(id)
                                    showProviderSelectDialog = false
                                    if (id == "webdav" && cloudBackupPrefs.getWebDavServerUrl().isBlank()) {
                                        showWebDavConfigDialog = true
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderSelectDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showWebDavConfigDialog) {
        var serverUrl by remember { mutableStateOf(cloudBackupPrefs.getWebDavServerUrl()) }
        var username by remember { mutableStateOf(cloudBackupPrefs.getWebDavUsername()) }
        var password by remember { mutableStateOf(cloudBackupPrefs.getWebDavPassword()) }
        var webDavError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showWebDavConfigDialog = false },
            icon = { Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("WebDAV Server Configuration") },
            text = {
                Column {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it; webDavError = null },
                        label = { Text("Server URL (e.g. https://cloud.example.com/dav)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; webDavError = null },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; webDavError = null },
                        label = { Text("Password / App Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (webDavError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(webDavError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (serverUrl.isBlank() || username.isBlank()) {
                            webDavError = "Server URL and Username are required."
                            return@Button
                        }
                        cloudBackupPrefs.setWebDavConfig(serverUrl.trim(), username.trim(), password)
                        showWebDavConfigDialog = false
                        Toast.makeText(context, "WebDAV settings saved", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavConfigDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    fun toggleNotifications(enabled: Boolean) {
        if (enabled) {
            // Check and request permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            
            notificationsEnabled = true
            prefs.edit().putBoolean("reminders_enabled", true).apply()
            ReminderManager(context).scheduleDailyReminder(reminderHour, reminderMinute)
            Toast.makeText(context, context.getString(R.string.toast_reminders_enabled), Toast.LENGTH_SHORT).show()
        } else {
            notificationsEnabled = false
            prefs.edit().putBoolean("reminders_enabled", false).apply()
            ReminderManager(context).cancelReminder()
            Toast.makeText(context, context.getString(R.string.toast_reminders_disabled), Toast.LENGTH_SHORT).show()
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Premium Status / Upgrade Banner
                if (!isPremium) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPremiumDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = stringResource(R.string.premium_go_premium),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.upgrade_to_premium),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = stringResource(R.string.upgrade_to_premium_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = stringResource(R.string.open),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFA500)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = stringResource(R.string.premium_active),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.premium_active),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.premium_active_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
                
                // Appearance & Security Section
                SettingsSection(title = stringResource(R.string.settings_appearance)) {
                    SettingsItem(
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = currentLanguageName,
                        onClick = { showLanguageDialog = true }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var showThemeModeDialog by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.dark_mode),
                        subtitle = when (themeMode) {
                            "dark" -> "Dark"
                            "light" -> "Light"
                            else -> "System Default"
                        },
                        onClick = { showThemeModeDialog = true }
                    )
                    
                    if (showThemeModeDialog) {
                        AlertDialog(
                            onDismissRequest = { showThemeModeDialog = false },
                            title = { Text("App Theme Mode") },
                            text = {
                                Column {
                                    val options = listOf("system" to "System Default", "light" to "Light", "dark" to "Dark")
                                    options.forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onThemeModeChange(value)
                                                    showThemeModeDialog = false
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = themeMode == value,
                                                onClick = {
                                                    onThemeModeChange(value)
                                                    showThemeModeDialog = false
                                                }
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(label, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showThemeModeDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    // Theme Selector
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(
                            text = if (isPremium) "App Theme" else "App Theme (Premium)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 56.dp, bottom = 8.dp)
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(com.onelineaday.dailydiary.ui.theme.AppTheme.values()) { theme ->
                                val isSelected = currentAppTheme == theme
                                val isThemeLocked = !isPremium && theme != com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (theme) {
                                                com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT -> SunsetOrange
                                                com.onelineaday.dailydiary.ui.theme.AppTheme.OCEAN -> androidx.compose.ui.graphics.Color(0xFF0066CC)
                                                com.onelineaday.dailydiary.ui.theme.AppTheme.FOREST -> androidx.compose.ui.graphics.Color(0xFF008000)
                                                com.onelineaday.dailydiary.ui.theme.AppTheme.MONOCHROME -> androidx.compose.ui.graphics.Color(0xFF424242)
                                            }
                                        )
                                        .clickable { 
                                            if (!isThemeLocked) {
                                                onThemeChange(theme)
                                            } else {
                                                showPremiumDialog = true
                                            }
                                        }
                                        .border(
                                            width = if (isSelected) 3.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = androidx.compose.ui.graphics.Color.White
                                        )
                                    } else if (isThemeLocked) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Locked",
                                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsToggleItem(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.settings_app_lock),
                        subtitle = if (appLockEnabled) {
                            stringResource(R.string.app_lock_protected)
                        } else {
                            stringResource(R.string.app_lock_require_auth)
                        },
                        isChecked = appLockEnabled,
                        onCheckedChange = { shouldEnable ->
                            val activity = context as? FragmentActivity
                            if (activity == null) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.app_lock_not_supported),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (!AppLockManager.canAuthenticate(context)) {
                                /*
                                 * IMPORTANT:
                                 *
                                 * Never enable App Lock if the user
                                 * currently has no way to authenticate.
                                 */
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.app_lock_setup_first),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                /*
                                 * Authenticate BOTH when enabling
                                 * and disabling the lock.
                                 *
                                 * This prevents someone who gets access
                                 * to an already-unlocked diary from simply
                                 * switching off protection.
                                 */
                                AppLockManager.authenticate(
                                    activity = activity,
                                    title = if (shouldEnable) {
                                        context.getString(R.string.enable_app_lock)
                                    } else {
                                        context.getString(R.string.disable_app_lock)
                                    },
                                    subtitle = if (shouldEnable) {
                                        context.getString(R.string.confirm_identity_protect)
                                    } else {
                                        context.getString(R.string.confirm_identity_remove)
                                    },
                                    onSuccess = {
                                        AppLockManager.setEnabled(
                                            context,
                                            shouldEnable
                                        )
                                        com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(context)
                                        appLockEnabled = shouldEnable
                                        Toast.makeText(
                                            context,
                                            if (shouldEnable) {
                                                context.getString(R.string.app_lock_enabled_toast)
                                            } else {
                                                context.getString(R.string.app_lock_disabled_toast)
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onCancelled = {
                                        /*
                                         * Do absolutely nothing.
                                         * Toggle stays in its original state.
                                         */
                                    },
                                    onError = { error ->
                                        Toast.makeText(
                                            context,
                                            error,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (hasPin) {
                        SettingsItem(
                            icon = Icons.Rounded.Password,
                            title = stringResource(R.string.change_pin),
                            subtitle = stringResource(R.string.pin_subtitle),
                            onClick = { 
                                pinSetupState = "CHANGE_VERIFY"
                                pinError = null
                            }
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        SettingsItem(
                            icon = Icons.Rounded.Delete,
                            title = stringResource(R.string.remove_pin),
                            subtitle = stringResource(R.string.pin_subtitle),
                            onClick = { 
                                pinSetupState = "REMOVE_VERIFY"
                                pinError = null
                            }
                        )
                    } else {
                        SettingsItem(
                            icon = Icons.Rounded.Password,
                            title = stringResource(R.string.set_pin),
                            subtitle = stringResource(R.string.pin_subtitle),
                            onClick = { 
                                pinSetupState = "SETUP_NEW"
                                pinError = null
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Notifications Section
                SettingsSection(title = stringResource(R.string.settings_notifications)) {
                    SettingsToggleItem(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.notifications),
                        subtitle = if (notificationsEnabled) 
                            stringResource(R.string.settings_reminder_at, String.format("%02d:%02d", reminderHour, reminderMinute)) 
                        else 
                            stringResource(R.string.settings_reminder_nudge),
                        isChecked = notificationsEnabled,
                        onCheckedChange = { toggleNotifications(it) }
                    )
                    
                    if (notificationsEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        SettingsItem(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.reminder_time),
                            subtitle = String.format("%02d:%02d", reminderHour, reminderMinute),
                            onClick = { showTimePicker = true }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Journal Switcher
                JournalSwitcherCard(
                    journals = uiState.journals,
                    activeJournalId = uiState.activeJournalId,
                    onSelect = { journalId ->
                        viewModel.setActiveJournal(journalId)
                    },
                    onCreate = { name ->
                        viewModel.createJournal(name)
                    },
                    isDuoJournal = { journalId ->
                        viewModel.isDuoJournal(journalId)
                    },
                    onVerifyDuoPin = { pin ->
                        viewModel.verifyDuoPin(pin)
                    },
                    onCreateDuo = { partnerName, pin ->
                        viewModel.createDuoJournal(partnerName, pin)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Data Section
                SettingsSection(title = stringResource(R.string.settings_data)) {
                    SettingsItem(
                        icon = Icons.Rounded.PictureAsPdf,
                        title = stringResource(R.string.export_pdf),
                        subtitle = stringResource(R.string.settings_export_pdf_subtitle),
                        onClick = {
                            val isPremium = com.onelineaday.dailydiary.PremiumManager.isPremium.value
                            if (isPremium) {
                                exportPdf(scope, context, uiState) { isExporting = it }
                            } else {
                                showAdDialog = true
                            }
                        },
                        isLoading = isExporting
                    )
                    
    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.settings_export_backup),
                        subtitle = stringResource(R.string.settings_export_backup_subtitle),
                        isLoading = isExportingBackup,
                        onClick = { 
                            showBackupPrivacyDialog = true
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.Restore,
                        title = stringResource(R.string.settings_restore_backup),
                        subtitle = stringResource(R.string.settings_restore_backup_subtitle),
                        isLoading = isRestoringBackup,
                        onClick = { 
                            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Material 3 Cloud Backup Section
                SettingsSection(title = stringResource(R.string.cloud_backup)) {
                    // Live Sync Status Card
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (activeProvider) {
                                            "google_drive" -> Icons.Rounded.Cloud
                                            "webdav" -> Icons.Rounded.Storage
                                            else -> Icons.Rounded.CloudOff
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = when (activeProvider) {
                                            "google_drive" -> "Google Drive Sync"
                                            "webdav" -> "WebDAV Sync"
                                            else -> "Cloud Sync Disabled"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SuggestionChip(
                                    onClick = { showProviderSelectDialog = true },
                                    label = {
                                        Text(
                                            when (lastSyncStatus) {
                                                "SUCCESS" -> "Synced"
                                                "FAILED" -> "Failed"
                                                "AUTH_EXPIRED" -> "Re-auth"
                                                else -> "Ready"
                                            }
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (lastSyncStatus == "SUCCESS") Icons.Rounded.CheckCircle else Icons.Rounded.Sync,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (lastSyncStatus == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            val lastSyncStr = if (lastSyncTime > 0) {
                                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSyncTime))
                            } else {
                                stringResource(R.string.never_backed_up)
                            }
                            Text(
                                text = "Last Sync: $lastSyncStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Cloud Provider Selection
                    SettingsItem(
                        icon = Icons.Rounded.CloudQueue,
                        title = "Cloud Storage Provider",
                        subtitle = when (activeProvider) {
                            "google_drive" -> "Google Drive (App Data folder)"
                            "webdav" -> "WebDAV (${cloudBackupPrefs.getWebDavServerUrl().ifBlank { "Not configured" }})"
                            else -> "None (Local storage only)"
                        },
                        onClick = { showProviderSelectDialog = true }
                    )

                    if (activeProvider == "webdav") {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsItem(
                            icon = Icons.Rounded.Dns,
                            title = "WebDAV Server Configuration",
                            subtitle = if (cloudBackupPrefs.getWebDavServerUrl().isNotBlank()) cloudBackupPrefs.getWebDavServerUrl() else "Tap to configure Nextcloud/NAS server",
                            onClick = { showWebDavConfigDialog = true }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Passphrase Configuration
                    SettingsItem(
                        icon = Icons.Rounded.Key,
                        title = "Backup Encryption Passphrase",
                        subtitle = if (isPassphraseSaved) "Protected by Android Keystore (AES-256-GCM)" else "Set recovery passphrase for automated sync",
                        onClick = { showPassphraseSetupDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Auto Backup Toggle
                    var autoBackupEnabled by remember { mutableStateOf(cloudBackupPrefs.isAutoBackupEnabled()) }
                    SettingsToggleItem(
                        icon = Icons.Rounded.CloudUpload,
                        title = stringResource(R.string.auto_backup),
                        subtitle = "Daily background backup (Wi-Fi and battery not low)",
                        isChecked = autoBackupEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !cloudBackupPrefs.hasSavedPassphrase()) {
                                showPassphraseSetupDialog = true
                            } else {
                                autoBackupEnabled = enabled
                                val cloudBackupManager = CloudBackupManager(context)
                                if (enabled) {
                                    cloudBackupManager.scheduleAutoBackup(24, requireUnmetered = true, requireBatteryNotLow = true)
                                } else {
                                    cloudBackupManager.cancelAutoBackup()
                                }
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Immediate Backup Now
                    SettingsItem(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.backup_now),
                        subtitle = "Trigger immediate encrypted backup",
                        isLoading = isExportingBackup,
                        onClick = {
                            if (activeProvider != "none" && cloudBackupPrefs.hasSavedPassphrase()) {
                                CloudBackupManager(context).triggerImmediateBackup()
                                Toast.makeText(context, "Cloud backup enqueued in background.", Toast.LENGTH_SHORT).show()
                            } else {
                                showBackupPrivacyDialog = true
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Habit Resilience & Streak Freezes Section
                SettingsSection(title = "Habit Resilience & Streaks") {
                    SettingsItem(
                        icon = Icons.Rounded.AcUnit,
                        title = "Streak Freezes",
                        subtitle = "${uiState.availableFreezes} of 5 available (Earn 1 every 7 days)",
                        onClick = {
                            Toast.makeText(context, "Streak Freezes automatically protect your streak when life gets busy. Earn up to 5 freezes through daily consistency.", Toast.LENGTH_LONG).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val restDaysLabel = when {
                        uiState.restDays.isEmpty() -> "None (Every day counts)"
                        uiState.restDays == setOf(java.time.DayOfWeek.SUNDAY) -> "Sundays only"
                        uiState.restDays == setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY) -> "Weekends (Sat & Sun)"
                        else -> "${uiState.restDays.size} days per week"
                    }

                    SettingsItem(
                        icon = Icons.Rounded.Weekend,
                        title = "Scheduled Rest Days",
                        subtitle = restDaysLabel,
                        onClick = { showRestDaysDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // AI & Voice Privacy (BYOK) Section
                SettingsSection(title = "AI & Voice Privacy") {
                    SettingsItem(
                        icon = Icons.Rounded.Mic,
                        title = "Voice Dictation & Transcription",
                        subtitle = "100% On-Device (Zero cloud audio upload)",
                        onClick = {
                            Toast.makeText(context, "Speech recognition runs locally via Android on-device speech packs. Your voice is never streamed to external servers.", Toast.LENGTH_LONG).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsToggleItem(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Cloud AI Features (Opt-In)",
                        subtitle = if (isAiOptIn) "Enabled (Requires personal API key)" else "Disabled (100% offline local processing)",
                        isChecked = isAiOptIn,
                        onCheckedChange = { optIn ->
                            if (optIn && !aiKeyManager.hasApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI)) {
                                showAiKeyDialog = true
                            } else {
                                isAiOptIn = optIn
                                aiKeyManager.setCloudAiOptIn(optIn)
                            }
                        }
                    )

                    if (isAiOptIn) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        val hasKey = aiKeyManager.hasApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI)
                        SettingsItem(
                            icon = Icons.Rounded.VpnKey,
                            title = "Gemini API Key (BYOK)",
                            subtitle = if (hasKey) "Stored securely in Android Keystore" else "Tap to configure your API key",
                            onClick = { showAiKeyDialog = true }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // About Section
                SettingsSection(title = stringResource(R.string.about)) {
                    val appVersion = remember {
                        runCatching {
                            com.onelineaday.dailydiary.BuildConfig.VERSION_NAME
                        }.getOrNull() ?: "4.3"
                    }
                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.version),
                        subtitle = appVersion,
                        onClick = { }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.Feedback,
                        title = stringResource(R.string.settings_send_feedback),
                        subtitle = stringResource(R.string.settings_feedback_subtitle),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("developeraisteps@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_feedback_subject))
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.toast_no_email_client), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.Share,
                        title = stringResource(R.string.settings_share_app),
                        subtitle = stringResource(R.string.settings_share_subtitle),
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text, context.packageName))
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.settings_share_chooser)))
                        }
                    )
    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
    
                    SettingsItem(
                        icon = Icons.Rounded.Star,
                        title = stringResource(R.string.settings_rate_app),
                        subtitle = stringResource(R.string.settings_rate_subtitle),
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
                            } catch (e: android.content.ActivityNotFoundException) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                            }
                        }
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.PrivacyTip,
                        title = stringResource(R.string.settings_privacy_policy),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        onClick = onNavigateToPrivacyPolicy
                    )

                    val privacyOptionsRequired by AdsConsentManager.privacyOptionsRequired.collectAsState()
                    if (privacyOptionsRequired) {
                        SettingsItem(
                            icon = Icons.Rounded.PrivacyTip,
                            title = stringResource(R.string.ad_privacy_choices),
                            subtitle = stringResource(R.string.ad_privacy_subtitle),
                            onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    AdsConsentManager.showPrivacyOptions(activity)
                                }
                            }
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    SettingsItem(
                        icon = Icons.Rounded.ShoppingCart,
                        title = stringResource(R.string.settings_restore_purchases),
                        subtitle = stringResource(R.string.settings_restore_purchases_subtitle),
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                com.onelineaday.dailydiary.billing.BillingManager.restorePurchases(activity)
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // App Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📝",
                            style = MaterialTheme.typography.displayMedium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = stringResource(R.string.settings_app_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = stringResource(R.string.settings_memories_captured, uiState.totalEntries),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (showRestDaysDialog) {
        AlertDialog(
            onDismissRequest = { showRestDaysDialog = false },
            icon = { Icon(Icons.Rounded.Weekend, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Scheduled Rest Days") },
            text = {
                Column {
                    Text(
                        "Choose designated rest days where missed entries will not break your journaling streak.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val options = listOf(
                        "None (Daily habit)" to emptySet<java.time.DayOfWeek>(),
                        "Sundays Only" to setOf(java.time.DayOfWeek.SUNDAY),
                        "Weekends (Saturday & Sunday)" to setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
                    )
                    options.forEach { (label, set) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setRestDays(set)
                                    showRestDaysDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.restDays == set,
                                onClick = {
                                    viewModel.setRestDays(set)
                                    showRestDaysDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestDaysDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAiKeyDialog) {
        var keyInput by remember { mutableStateOf(aiKeyManager.getApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI) ?: "") }
        AlertDialog(
            onDismissRequest = { showAiKeyDialog = false },
            icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Bring Your Own API Key") },
            text = {
                Column {
                    Text(
                        "Your API key is hardware-encrypted in Android Keystore using AES-256-GCM. We never transmit your key or diary entries to third parties without your explicit request.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Google Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        aiKeyManager.saveApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI, keyInput.trim())
                        if (keyInput.isNotBlank()) {
                            aiKeyManager.setCloudAiOptIn(true)
                            isAiOptIn = true
                        }
                        showAiKeyDialog = false
                        Toast.makeText(context, "API Key saved securely.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                if (aiKeyManager.hasApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI)) {
                    TextButton(
                        onClick = {
                            aiKeyManager.clearApiKey(com.onelineaday.dailydiary.ai.privacy.AiProvider.GEMINI)
                            aiKeyManager.setCloudAiOptIn(false)
                            isAiOptIn = false
                            showAiKeyDialog = false
                            Toast.makeText(context, "API Key cleared.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Remove Key", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = { showAiKeyDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Backup Privacy Warning Dialog
    if (showBackupPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showBackupPrivacyDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_export_backup),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.backup_archive_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupPrivacyDialog = false
                        scope.launch {
                            isExportingBackup = true
                            val savedPass = cloudBackupPrefs.getSavedPassphrase()
                            if (savedPass != null && savedPass.isNotEmpty()) {
                                val encManager = EncryptedBackupManager(context, JournalDatabase.getDatabase(context))
                                when (val result = encManager.exportEncryptedBackup(savedPass)) {
                                    is BackupExportResult.Success -> {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, result.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Encrypted Backup"))
                                    }
                                    is BackupExportResult.Failure -> {
                                        Toast.makeText(context, context.getString(R.string.backup_failed, result.reason), Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                when (val result = viewModel.exportBackup()) {
                                    is BackupExportResult.Success -> {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, result.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_backup_archive)))
                                    }
                                    is BackupExportResult.Failure -> {
                                        Toast.makeText(context, context.getString(R.string.backup_failed, result.reason), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            isExportingBackup = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.export_and_share))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPrivacyDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Rewarded Ad Confirmation Dialog
    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { showAdDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { 
                Text(
                    text = stringResource(R.string.ad_dialog_title),
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(stringResource(R.string.ad_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdDialog = false
                        val activity = context.findActivity()
                        if (activity != null) {
                            RewardedAdManager.showAd(
                                activity = activity,
                                onRewarded = {
                                    adRewarded = true
                                },
                                onAdDismissed = {
                                    if (adRewarded) {
                                        adRewarded = false
                                        exportPdf(scope, context, uiState) { isExporting = it }
                                    }
                                },
                                onAdNotAvailable = {
                                    // Fallback: let user export even if ad fails
                                    exportPdf(scope, context, uiState) { isExporting = it }
                                }
                            )
                        } else {
                            // Not an Activity context — export directly
                            exportPdf(scope, context, uiState) { isExporting = it }
                        }
                    }
                ) {
                    Text(stringResource(R.string.ad_dialog_watch))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderHour,
            initialMinute = reminderMinute
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.settings_set_reminder_time)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reminderHour = timePickerState.hour
                        reminderMinute = timePickerState.minute
                        prefs.edit()
                            .putInt("reminder_hour", reminderHour)
                            .putInt("reminder_minute", reminderMinute)
                            .apply()
                        
                        // Reschedule with new time
                        ReminderManager(context).scheduleDailyReminder(reminderHour, reminderMinute)
                        Toast.makeText(
                            context, 
                            context.getString(R.string.settings_reminder_set_for, String.format("%02d:%02d", reminderHour, reminderMinute)), 
                            Toast.LENGTH_SHORT
                        ).show()
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(R.string.settings_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.settings_select_language)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(languages.size) { index ->
                        val lang = languages[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLanguageDialog = false
                                    val languageCode = lang.first
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = android.os.LocaleList.forLanguageTags(languageCode)
                                        val activity = context as? Activity
                                        activity?.recreate()
                                    } else {
                                        val locale = java.util.Locale(languageCode)
                                        java.util.Locale.setDefault(locale)
                                        val resources = context.resources
                                        val config = android.content.res.Configuration(resources.configuration)
                                        config.setLocale(locale)
                                        @Suppress("DEPRECATION")
                                        resources.updateConfiguration(config, resources.displayMetrics)
                                        
                                        prefs.edit().putString("language", languageCode).apply()
                                        
                                        val activity = context as? Activity
                                        activity?.recreate()
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLanguageCode == lang.first,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = lang.second,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPremiumDialog) {
        com.onelineaday.dailydiary.ui.components.PremiumDialog(
            onDismiss = { showPremiumDialog = false }
        )
    }

    if (pinSetupState != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                pinSetupState = null 
                pinError = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { 
                            pinSetupState = null 
                            pinError = null
                        },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                    
                    val title = when (pinSetupState) {
                        "SETUP_NEW", "CHANGE_NEW" -> stringResource(R.string.set_pin)
                        "SETUP_CONFIRM", "CHANGE_CONFIRM" -> stringResource(R.string.confirm_pin)
                        "CHANGE_VERIFY", "REMOVE_VERIFY" -> stringResource(R.string.enter_pin)
                        else -> ""
                    }
                    
                    com.onelineaday.dailydiary.ui.components.PinEntryScreen(
                        title = title,
                        subtitle = stringResource(R.string.pin_subtitle),
                        errorMessage = pinError,
                        onPinComplete = { enteredPin ->
                            when (pinSetupState) {
                                "SETUP_NEW", "CHANGE_NEW" -> {
                                    tempPin = enteredPin
                                    pinSetupState = if (pinSetupState == "SETUP_NEW") "SETUP_CONFIRM" else "CHANGE_CONFIRM"
                                    pinError = null
                                }
                                "SETUP_CONFIRM", "CHANGE_CONFIRM" -> {
                                    if (enteredPin == tempPin) {
                                        com.onelineaday.dailydiary.security.AppLockManager.setPin(context, enteredPin)
                                        hasPin = true
                                        pinSetupState = null
                                        Toast.makeText(context, context.getString(R.string.pin_set_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        pinError = context.getString(R.string.pin_mismatch)
                                        pinSetupState = if (pinSetupState == "SETUP_CONFIRM") "SETUP_NEW" else "CHANGE_NEW"
                                    }
                                }
                                "CHANGE_VERIFY", "REMOVE_VERIFY" -> {
                                    if (com.onelineaday.dailydiary.security.AppLockManager.verifyPin(context, enteredPin)) {
                                        if (pinSetupState == "CHANGE_VERIFY") {
                                            pinSetupState = "CHANGE_NEW"
                                            pinError = null
                                        } else {
                                            com.onelineaday.dailydiary.security.AppLockManager.removePin(context)
                                            hasPin = false
                                            pinSetupState = null
                                            Toast.makeText(context, context.getString(R.string.pin_removed), Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        pinError = context.getString(R.string.wrong_pin)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isLoading) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

private fun exportPdf(
    scope: CoroutineScope,
    context: Context,
    uiState: JournalUiState,
    onExportingChange: (Boolean) -> Unit = {}
) {
    scope.launch {
        onExportingChange(true)
        try {
            val uri = PdfExportHelper.generateJournalPdf(
                context,
                uiState.entries,
                uiState.currentStreak,
                uiState.longestStreak,
                uiState.totalEntries
            )
            if (uri != null) {
                sharePdf(context, uri)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_export_failed, e.message),
                Toast.LENGTH_SHORT
            ).show()
        } finally {
            onExportingChange(false)
        }
    }
}

private fun sharePdf(context: Context, uri: android.net.Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.my_journal_pdf_subject))
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.toast_share_pdf)))
}