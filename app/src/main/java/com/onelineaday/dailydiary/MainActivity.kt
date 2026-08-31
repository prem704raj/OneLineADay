package com.onelineaday.dailydiary

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onelineaday.dailydiary.billing.BillingManager
import com.onelineaday.dailydiary.security.AppLockManager
import com.onelineaday.dailydiary.ui.screens.LockScreen
import com.onelineaday.dailydiary.ui.screens.MainNavigation
import com.onelineaday.dailydiary.ui.theme.AppTheme
import com.onelineaday.dailydiary.ui.theme.OneLineADayTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        /*
         * Apply saved language on Android 12 and lower.
         */
        if (
            android.os.Build.VERSION.SDK_INT <
            android.os.Build.VERSION_CODES.TIRAMISU
        ) {

            val prefs =
                getSharedPreferences(
                    AppLockManager.PREFS_NAME,
                    Context.MODE_PRIVATE
                )

            val language =
                prefs.getString(
                    "language",
                    "en"
                ) ?: "en"

            val locale =
                java.util.Locale(language)

            java.util.Locale.setDefault(locale)

            val configuration =
                android.content.res.Configuration(
                    resources.configuration
                )

            configuration.setLocale(locale)

            @Suppress("DEPRECATION")
            resources.updateConfiguration(
                configuration,
                resources.displayMetrics
            )
        }

        installSplashScreen()

        super.onCreate(savedInstanceState)

        PremiumManager.init(this)

        /*
         * Billing remains untouched for now.
         * We will fix it separately as requested.
         */
        BillingManager.init(this)

        enableEdgeToEdge()

        setContent {

            val context =
                LocalContext.current

            val lifecycleOwner =
                LocalLifecycleOwner.current

            val prefs =
                remember {
                    context.getSharedPreferences(
                        AppLockManager.PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                }

            val systemDarkMode =
                isSystemInDarkTheme()

            /*
             * -------------------------
             * Theme state
             * -------------------------
             */

            var isDarkMode by remember {

                mutableStateOf(
                    prefs.getBoolean(
                        "dark_mode",
                        systemDarkMode
                    )
                )
            }

            var appThemeName by remember {

                mutableStateOf(
                    prefs.getString(
                        "app_theme",
                        "DEFAULT"
                    ) ?: "DEFAULT"
                )
            }

            val currentAppTheme = try {

                AppTheme.valueOf(appThemeName)

            } catch (_: Exception) {

                AppTheme.DEFAULT
            }

            /*
             * -------------------------
             * App Lock state
             * -------------------------
             */

            var appLockEnabled by remember {

                mutableStateOf(
                    AppLockManager.isEnabled(context)
                )
            }

            /*
             * If lock was already enabled when the
             * application started, start locked.
             */
            var isUnlocked by remember {

                mutableStateOf(
                    !appLockEnabled
                )
            }

            /*
             * Prevent multiple authentication dialogs
             * from being opened simultaneously.
             */
            var authenticationInProgress by remember {

                mutableStateOf(false)
            }

            /*
             * Error shown on LockScreen.
             */
            var authenticationError by remember {

                mutableStateOf<String?>(null)
            }

            /*
             * -------------------------
             * Listen for App Lock setting changes
             * -------------------------
             *
             * SettingsScreen changes SharedPreferences.
             *
             * MainActivity should immediately know
             * whether App Lock was enabled/disabled.
             */
            DisposableEffect(prefs) {

                val listener =
                    android.content.SharedPreferences
                        .OnSharedPreferenceChangeListener {
                                _,
                                key ->

                            if (
                                key ==
                                AppLockManager.KEY_APP_LOCK
                            ) {

                                val enabled =
                                    AppLockManager
                                        .isEnabled(context)

                                appLockEnabled =
                                    enabled

                                /*
                                 * If the user disables App Lock
                                 * after authenticating, make sure
                                 * the application becomes unlocked.
                                 */
                                if (!enabled) {
                                    isUnlocked = true
                                }
                            }
                        }

                prefs.registerOnSharedPreferenceChangeListener(
                    listener
                )

                onDispose {

                    prefs.unregisterOnSharedPreferenceChangeListener(
                        listener
                    )
                }
            }

            /*
             * -------------------------
             * Automatically lock when app goes
             * into background
             * -------------------------
             */
            DisposableEffect(
                lifecycleOwner,
                appLockEnabled
            ) {

                val observer =
                    LifecycleEventObserver {
                            _,
                            event ->

                        if (
                            event ==
                            Lifecycle.Event.ON_STOP &&
                            appLockEnabled
                        ) {

                            isUnlocked = false
                            authenticationError = null
                        }
                    }

                lifecycleOwner.lifecycle.addObserver(
                    observer
                )

                onDispose {

                    lifecycleOwner.lifecycle.removeObserver(
                        observer
                    )
                }
            }

            /*
             * -------------------------
             * Persist theme changes
             * -------------------------
             */
            LaunchedEffect(isDarkMode) {

                prefs.edit()
                    .putBoolean(
                        "dark_mode",
                        isDarkMode
                    )
                    .apply()
            }

            LaunchedEffect(appThemeName) {

                prefs.edit()
                    .putString(
                        "app_theme",
                        appThemeName
                    )
                    .apply()
            }

            /*
             * -------------------------
             * Authentication
             * -------------------------
             */

            val showUnlockPrompt: () -> Unit = {

                if (!authenticationInProgress) {

                    if (
                        !AppLockManager
                            .canAuthenticate(context)
                    ) {

                        authenticationError =
                            "Device security is unavailable. " +
                                "Set up a fingerprint, face unlock, " +
                                "PIN, pattern, or password in Android settings."

                    } else {

                        authenticationError = null
                        authenticationInProgress = true

                        AppLockManager.authenticate(

                            activity =
                                this@MainActivity,

                            title =
                                "Unlock One Line A Day",

                            subtitle =
                                "Confirm your identity to view your diary",

                            onSuccess = {

                                authenticationInProgress =
                                    false

                                authenticationError =
                                    null

                                isUnlocked =
                                    true
                            },

                            onCancelled = {

                                authenticationInProgress =
                                    false

                                /*
                                 * Stay locked.
                                 */
                            },

                            onError = { error ->

                                authenticationInProgress =
                                    false

                                authenticationError =
                                    error
                            }
                        )
                    }
                }
            }

            /*
             * Automatically show authentication
             * when launching a locked diary.
             */
            LaunchedEffect(
                appLockEnabled,
                isUnlocked
            ) {

                if (
                    appLockEnabled &&
                    !isUnlocked &&
                    AppLockManager.canAuthenticate(
                        context
                    )
                ) {

                    showUnlockPrompt()
                }
            }

            /*
             * -------------------------
             * Application UI
             * -------------------------
             */

            OneLineADayTheme(
                darkTheme = isDarkMode,
                appTheme = currentAppTheme
            ) {

                Surface(
                    modifier =
                        Modifier.fillMaxSize(),
                    color =
                        MaterialTheme
                            .colorScheme
                            .background
                ) {

                    if (
                        appLockEnabled &&
                        !isUnlocked
                    ) {

                        LockScreen(

                            authenticationAvailable =
                                AppLockManager
                                    .canAuthenticate(context),

                            errorMessage =
                                authenticationError,

                            onUnlockClick =
                                showUnlockPrompt,

                            onOpenSecuritySettings = {

                                try {

                                    startActivity(
                                        Intent(
                                            Settings.ACTION_SECURITY_SETTINGS
                                        )
                                    )

                                } catch (_: Exception) {

                                    /*
                                     * Extremely uncommon,
                                     * but don't crash if a device
                                     * manufacturer doesn't expose
                                     * this settings screen.
                                     */
                                }
                            }
                        )

                    } else {

                        MainNavigation(
                            isDarkMode =
                                isDarkMode,

                            onDarkModeChange = {
                                isDarkMode = it
                            },

                            currentAppTheme =
                                currentAppTheme,

                            onThemeChange = {
                                appThemeName =
                                    it.name
                            }
                        )
                    }
                }
            }
        }
    }
}
