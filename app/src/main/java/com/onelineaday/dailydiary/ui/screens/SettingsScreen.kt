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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import com.onelineaday.dailydiary.ads.RewardedAdManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onelineaday.dailydiary.ads.BannerAdView
import com.onelineaday.dailydiary.notifications.ReminderManager
import com.onelineaday.dailydiary.ui.theme.*
import com.onelineaday.dailydiary.viewmodel.JournalUiState
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import com.onelineaday.dailydiary.widget.JournalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: JournalViewModel,
    uiState: JournalUiState,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
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
    var showTimePicker by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
    
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackup(context, it) }
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

                    SettingsToggleItem(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.dark_mode),
                        subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                        isChecked = isDarkMode,
                        onCheckedChange = onDarkModeChange
                    )
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    // Theme Selector
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(
                            text = "App Theme (Premium)",
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
                                        .clickable { onThemeChange(theme) }
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
                        subtitle = stringResource(R.string.settings_app_lock_subtitle),
                        isChecked = appLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                // Prompt biometric before enabling
                                val activity = context as? FragmentActivity
                                if (activity != null) {
                                    val executor = ContextCompat.getMainExecutor(context)
                                    val prompt = BiometricPrompt(activity, executor,
                                        object : BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                super.onAuthenticationSucceeded(result)
                                                appLockEnabled = true
                                                prefs.edit().putBoolean("app_lock", true).apply()
                                                Toast.makeText(context, "App Lock Enabled", Toast.LENGTH_SHORT).show()
                                            }

                                            override fun onAuthenticationFailed() {
                                                super.onAuthenticationFailed()
                                                Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                                            }
                                            
                                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                super.onAuthenticationError(errorCode, errString)
                                                Toast.makeText(context, "Cannot enable: $errString", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    
                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                        .setTitle("Enable App Lock")
                                        .setSubtitle("Authenticate to enable App Lock")
                                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                        .build()
                                        
                                    prompt.authenticate(promptInfo)
                                } else {
                                    Toast.makeText(context, "App Lock not supported on this device", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                appLockEnabled = false
                                prefs.edit().putBoolean("app_lock", false).apply()
                                Toast.makeText(context, "App Lock Disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
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
                
                // Data Section
                SettingsSection(title = stringResource(R.string.settings_data)) {
                    SettingsItem(
                        icon = Icons.Rounded.PictureAsPdf,
                        title = stringResource(R.string.export_pdf),
                        subtitle = stringResource(R.string.settings_export_pdf_subtitle),
                        onClick = {
                            val isPremium = com.onelineaday.dailydiary.PremiumManager.isPremium.value
                            if (isPremium) {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        val uri = com.onelineaday.dailydiary.utils.PdfExportHelper.generateJournalPdf(context, uiState.entries, uiState.currentStreak, uiState.longestStreak, uiState.totalEntries)
                                        if (uri != null) {
                                            sharePdf(context, uri)
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_export_failed, e.message),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isExporting = false
                                }
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
                        onClick = { 
                            val uri = viewModel.exportBackup(context)
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Save Backup to Google Drive"))
                            } else {
                                Toast.makeText(context, "Failed to create backup", Toast.LENGTH_SHORT).show()
                            }
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
                        onClick = { 
                            importLauncher.launch("application/zip")
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // About Section
                SettingsSection(title = stringResource(R.string.about)) {
                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.version),
                        subtitle = stringResource(R.string.settings_version_number),
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
            
            BannerAdView()
        }
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
                                        scope.launch {
                                            isExporting = true
                                            try {
                                                val uri = com.onelineaday.dailydiary.utils.PdfExportHelper.generateJournalPdf(context, uiState.entries, uiState.currentStreak, uiState.longestStreak, uiState.totalEntries)
                                        if (uri != null) {
                                            sharePdf(context, uri)
                                        }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.toast_export_failed, e.message),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            isExporting = false
                                        }
                                    }
                                },
                                onAdNotAvailable = {
                                    // Fallback: let user export even if ad fails
                                    scope.launch {
                                        isExporting = true
                                        try {
                                            val uri = com.onelineaday.dailydiary.utils.PdfExportHelper.generateJournalPdf(context, uiState.entries, uiState.currentStreak, uiState.longestStreak, uiState.totalEntries)
                                        if (uri != null) {
                                            sharePdf(context, uri)
                                        }
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_export_failed, e.message),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        isExporting = false
                                    }
                                }
                            )
                        } else {
                            // Not an Activity context — export directly
                            scope.launch {
                                isExporting = true
                                try {
                                    val uri = com.onelineaday.dailydiary.utils.PdfExportHelper.generateJournalPdf(context, uiState.entries, uiState.currentStreak, uiState.longestStreak, uiState.totalEntries)
                                        if (uri != null) {
                                            sharePdf(context, uri)
                                        }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_export_failed, e.message),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isExporting = false
                            }
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

private fun sharePdf(context: Context, uri: android.net.Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        putExtra(android.content.Intent.EXTRA_SUBJECT, "My One Line A Day Journal")
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    
    context.startActivity(android.content.Intent.createChooser(intent, "Share Journal PDF"))
}
