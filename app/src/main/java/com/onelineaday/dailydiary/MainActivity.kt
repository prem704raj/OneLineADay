package com.onelineaday.dailydiary

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onelineaday.dailydiary.ui.screens.LockScreen
import com.onelineaday.dailydiary.ui.screens.MainNavigation
import com.onelineaday.dailydiary.ui.theme.OneLineADayTheme
import com.onelineaday.dailydiary.billing.BillingManager

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved language for API < 33
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val lang = prefs.getString("language", "en") ?: "en"
            val locale = java.util.Locale(lang)
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        installSplashScreen()
        super.onCreate(savedInstanceState)
        PremiumManager.init(this)
        BillingManager.init(this)
        enableEdgeToEdge()
        
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
            val systemDarkMode = isSystemInDarkTheme()
            
            var isDarkMode by remember { 
                mutableStateOf(prefs.getBoolean("dark_mode", systemDarkMode)) 
            }
            
            var appThemeName by remember {
                mutableStateOf(prefs.getString("app_theme", "DEFAULT") ?: "DEFAULT")
            }
            val currentAppTheme = try {
                com.onelineaday.dailydiary.ui.theme.AppTheme.valueOf(appThemeName)
            } catch (e: Exception) {
                com.onelineaday.dailydiary.ui.theme.AppTheme.DEFAULT
            }
            
            // App Lock State
            var isUnlocked by remember { mutableStateOf(!prefs.getBoolean("app_lock", false)) }
            
            // Re-lock when app goes to background
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        if (prefs.getBoolean("app_lock", false)) {
                            isUnlocked = false
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            
            LaunchedEffect(isDarkMode) {
                prefs.edit().putBoolean("dark_mode", isDarkMode).apply()
            }
            LaunchedEffect(appThemeName) {
                prefs.edit().putString("app_theme", appThemeName).apply()
            }
            
            // Helper to show biometric prompt
            val showBiometricPrompt = {
                val executor = ContextCompat.getMainExecutor(this@MainActivity)
                val biometricPrompt = BiometricPrompt(this@MainActivity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            isUnlocked = true
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock One Line A Day")
                    .setSubtitle("Confirm your identity to view your diary")
                    .setNegativeButtonText("Cancel")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            
            val appLockEnabled = prefs.getBoolean("app_lock", false)
            val canAuthenticate = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            
            // Show prompt on first launch if locked
            LaunchedEffect(Unit) {
                if (appLockEnabled && !isUnlocked && canAuthenticate) {
                    showBiometricPrompt()
                }
            }

            OneLineADayTheme(
                darkTheme = isDarkMode,
                appTheme = currentAppTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (appLockEnabled && !isUnlocked) {
                        LockScreen(
                            onUnlockClick = showBiometricPrompt
                        )
                    } else {
                        MainNavigation(
                            isDarkMode = isDarkMode,
                            onDarkModeChange = { isDarkMode = it },
                            currentAppTheme = currentAppTheme,
                            onThemeChange = { appThemeName = it.name }
                        )
                    }
                }
            }
        }
    }
}
