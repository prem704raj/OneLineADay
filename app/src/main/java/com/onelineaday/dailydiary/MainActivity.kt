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
import com.onelineaday.dailydiary.ads.AdsConsentManager
import com.onelineaday.dailydiary.billing.BillingManager
import com.onelineaday.dailydiary.notifications.FlashbackNotificationManager
import com.onelineaday.dailydiary.security.AppLockManager
import com.onelineaday.dailydiary.ui.screens.LockScreen
import com.onelineaday.dailydiary.ui.screens.MainNavigation
import com.onelineaday.dailydiary.ui.theme.AppTheme
import com.onelineaday.dailydiary.ui.theme.OneLineADayTheme

class MainActivity : FragmentActivity(), com.onelineaday.dailydiary.security.WindowFlagMutator {

    private lateinit var windowPrivacyController: com.onelineaday.dailydiary.security.WindowPrivacyController
    private var lastStopTime: Long = 0L
    private var pendingNavDestination by mutableStateOf<String?>(null)

    override fun setFlags(flags: Int, mask: Int) {
        window.setFlags(flags, mask)
    }

    override fun clearFlags(flags: Int) {
        window.clearFlags(flags)
    }

    override fun onResume() {
        super.onResume()
        val appLockEnabled = AppLockManager.isAppLockEnabled(this)
        if (appLockEnabled && lastStopTime > 0L && System.currentTimeMillis() - lastStopTime > 30_000L) {
            AppLockManager.setSessionUnlocked(false)
        }
        lastStopTime = 0L
        if (::windowPrivacyController.isInitialized) {
            windowPrivacyController.onResume(
                isAppLockEnabled = appLockEnabled,
                isSessionUnlocked = AppLockManager.isSessionUnlocked()
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (::windowPrivacyController.isInitialized) {
            windowPrivacyController.onPause(
                isAppLockEnabled = AppLockManager.isAppLockEnabled(this)
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppLockManager.isAppLockEnabled(this)) {
            lastStopTime = System.currentTimeMillis()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val dest = intent?.getStringExtra(FlashbackNotificationManager.EXTRA_NAV_DESTINATION)
        if (!dest.isNullOrBlank()) {
            pendingNavDestination = dest
        } else {
            val widgetAction = intent?.getStringExtra("widget_action")
            if (widgetAction == "year_in_pixels") {
                pendingNavDestination = "year_in_pixels"
            }
        }
    }

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
        BillingManager.init(this)
        AdsConsentManager.gatherConsent(this)
        com.onelineaday.dailydiary.ads.TimedAdManager.init(this, this)
        handleIntent(intent)

        windowPrivacyController = com.onelineaday.dailydiary.security.WindowPrivacyController(this) { enabled ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(enabled)
            }
        }
        AppLockManager.initSession(this)
        windowPrivacyController.updateWindowPrivacy(
            isLocked = !AppLockManager.isSessionUnlocked(),
            isAppLockEnabled = AppLockManager.isAppLockEnabled(this)
        )

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

            var themeMode by remember {
                mutableStateOf(
                    try {
                        prefs.getString("theme_mode", null) ?: run {
                            if (prefs.contains("dark_mode")) {
                                if (prefs.getBoolean("dark_mode", false)) "dark" else "light"
                            } else {
                                "system"
                            }
                        }
                    } catch (e: Exception) {
                        "system"
                    }
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

            var lockType by remember {
                mutableStateOf(AppLockManager.getLockType(context))
            }

            var appLockEnabled by remember {
                mutableStateOf(lockType != AppLockManager.LockType.NONE)
            }

            /*
             * Observed session unlock state from AppLockManager.
             */
            val isUnlocked by AppLockManager.isSessionUnlockedFlow.collectAsState()

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
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == AppLockManager.KEY_APP_LOCK || key == AppLockManager.KEY_PIN_HASH) {
                        val newLockType = AppLockManager.getLockType(context)
                        val enabled = newLockType != AppLockManager.LockType.NONE
                        lockType = newLockType
                        appLockEnabled = enabled

                        /*
                         * If the user disables App Lock
                         * after authenticating, make sure
                         * the application becomes unlocked.
                         */
                        if (!enabled) {
                            AppLockManager.setSessionUnlocked(true)
                        }
                    }
                }

                prefs.registerOnSharedPreferenceChangeListener(listener)

                onDispose {

                    prefs.unregisterOnSharedPreferenceChangeListener(
                        listener
                    )
                }
            }

            LaunchedEffect(appLockEnabled, isUnlocked) {
                if (::windowPrivacyController.isInitialized) {
                    windowPrivacyController.updateWindowPrivacy(
                        isLocked = !isUnlocked,
                        isAppLockEnabled = appLockEnabled
                    )
                }
            }

            /*
             * -------------------------
             * Automatically lock when app goes
             * into background
             * -------------------------
             */
            var lastStopTime by remember { mutableStateOf(0L) }

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

                            lastStopTime = System.currentTimeMillis()
                        } else if (
                            event == Lifecycle.Event.ON_START && 
                            appLockEnabled
                        ) {
                            if (lastStopTime > 0L && System.currentTimeMillis() - lastStopTime > 30_000) {
                                AppLockManager.setSessionUnlocked(false)
                                authenticationError = null
                            }
                            lastStopTime = 0L
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
            LaunchedEffect(themeMode) {

                prefs.edit()
                    .putString(
                        "theme_mode",
                        themeMode
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

                                AppLockManager.setSessionUnlocked(true)
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
                themeMode = themeMode,
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

                            lockType = lockType,
                            
                            onPinComplete = { pin ->
                                when (val result = AppLockManager.verifyPinWithThrottling(context, pin)) {
                                    is AppLockManager.PinVerificationResult.Success -> {
                                        AppLockManager.setSessionUnlocked(true)
                                        authenticationError = null
                                    }
                                    is AppLockManager.PinVerificationResult.Failed -> {
                                        authenticationError = if (result.remainingAttempts > 0) {
                                            "${context.getString(R.string.wrong_pin)} (${result.remainingAttempts} attempts remaining)"
                                        } else {
                                            context.getString(R.string.wrong_pin)
                                        }
                                    }
                                    is AppLockManager.PinVerificationResult.LockedOut -> {
                                        authenticationError = "Too many attempts. Try again in ${result.secondsRemaining}s."
                                    }
                                }
                            },

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
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                            },

                            currentAppTheme =
                                currentAppTheme,

                            onThemeChange = {
                                appThemeName =
                                    it.name
                            },
                            pendingNavDestination = pendingNavDestination,
                            onClearPendingNavDestination = {
                                pendingNavDestination = null
                            }
                        )
                    }
                }
            }
        }
    }
}