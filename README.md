# One Line A Day 📝

A modern, privacy-focused micro-journaling application built with Jetpack Compose and Material 3. Capture life effortlessly in one sentence a day with zero friction, local-first storage, rich multimedia attachments, and deep statistics.

---

## ✨ Features

| Feature | Description |
|---|---|
| **📝 Daily Entry** | Capture your day in one single sentence in under 10 seconds |
| **😊 Mood Tracking** | 10 expressive moods with animated emoji reactions and distribution charts |
| **🎙️ Voice Memories** | In-app audio recorder with failure-safe lifecycle, real-time waveform, and playback progress |
| **📷 Photo & Media** | Attach multiple photos and videos via Android's privacy-preserving Photo Picker |
| **🏷️ Smart Tags** | Tag memories for progressive filtering and categorization |
| **🔒 App Lock & Privacy** | Biometric, fingerprint, PIN, or device credential authentication with Recents screenshot protection |
| **📅 Timeline View** | Grouped by month with quick search, tag filtering, mood chips, and date jump |
| **🔥 Pure Streak Engine** | Non-destructive current and longest streak calculation |
| **📊 Journey Statistics** | Weekly & monthly entry counts, calendar days remaining, mood distribution, and milestones |
| **📤 PDF Journal Export** | Compile and share your journal entries into a clean PDF document |
| **💾 Logical ZIP Backup & Restore** | Safe, versioned ZIP archive backup and atomic restore with checksum & traversal verification |
| **🔔 Daily Reminders** | Configurable daily reminder notifications restored automatically on reboot |
| **📱 Home Screen Widget** | Glanceable today's line on your home screen with private mode |
| **🎨 Themes & Dark Mode** | WCAG AA accessible Sunset palette, Ocean, Forest, and Monochrome themes |

---

## 🏗️ Architecture & Technology Stack

- **Language**: Kotlin 2.0+
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target / Compile SDK**: API 36 (Android 16 Readiness)
- **UI Framework**: Jetpack Compose with Material 3
- **Local Persistence**: Room Database (Schema v5) with SQLite Foreign Key constraints
- **Concurrency & Reactivity**: Kotlin Coroutines & StateFlow / SharedFlow
- **Image & Video Loading**: Coil Compose & Coil Video
- **PDF Generation**: iText 7
- **In-App Billing**: Google Play Billing Library
- **Advertising**: Google Mobile Ads SDK with UMP Privacy Consent integration
- **App Lock**: AndroidX Biometric 1.1.0

---

## 📁 Repository Structure

```
app/src/main/
├── java/com/onelineaday/dailydiary/
│   ├── MainActivity.kt               # Single Activity host with edge-to-edge & lock perimeter
│   ├── OneLineApp.kt                 # Application class initializing ads, channels, and billing
│   ├── PremiumManager.kt             # Premium entitlement state management
│   ├── ads/                          # Banner, Native, Rewarded, and Consent management
│   ├── audio/                        # AudioRecorderHelper (Result API) & AudioPlayerHelper
│   ├── backup/                       # Logical backup creator, validator, and atomic restore
│   ├── billing/                      # BillingManager validating recognized product IDs
│   ├── data/                         # Room entities (v5), JournalDao, JournalDatabase, Repository
│   ├── media/                        # MediaStorageManager for safe internal file copy & cleanup
│   ├── notifications/                # ReminderManager & BootReceiver
│   ├── security/                     # AppLockManager with BiometricPrompt & credential fallback
│   ├── ui/
│   │   ├── components/               # MoodPicker, AudioAttachment, MediaGallery, TagInput, etc.
│   │   ├── screens/                  # HomeScreen, TimelineScreen, StatsScreen, SettingsScreen, Nav
│   │   └── theme/                    # Color.kt (WCAG AA accessible), Theme.kt, Type.kt
│   ├── utils/                        # StreakCalculator, DateFormatters
│   ├── viewmodel/                    # JournalViewModel with single-flight saves & cancellable jobs
│   └── widget/                       # JournalWidget provider & layout
└── res/
    ├── values/                       # strings.xml, colors, themes
    ├── layout/                       # widget_journal.xml
    └── xml/                          # backup_rules.xml, data_extraction_rules.xml, widget_info.xml
```

---

## 🔒 Permissions Declared

The application declares the following permissions in `AndroidManifest.xml`:

| Permission | Purpose |
|---|---|
| `android.permission.RECORD_AUDIO` | Required only when recording voice journal memories |
| `android.permission.POST_NOTIFICATIONS` | Used to deliver optional daily reminder alerts |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedules configured reminder alarms after device restart |
| `android.permission.USE_BIOMETRIC` | Enables biometric authentication when App Lock is configured |
| `android.permission.INTERNET` | Required by Google Mobile Ads and Google Play Billing |
| `android.permission.ACCESS_NETWORK_STATE` | Checks network availability for ad and billing requests |
| `com.android.vending.BILLING` | Enables optional premium subscription and lifetime purchases |

> [!NOTE]
> The app uses Android's modern Photo Picker (`ActivityResultContracts.PickVisualMedia`) for image and video selection. It does **not** request broad storage permissions such as `READ_MEDIA_IMAGES` or `READ_EXTERNAL_STORAGE`.

---

## 🚀 Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17
- Android SDK Platform 36

### Build Commands
```bash
# Clean and compile debug build
./gradlew assembleDebug

# Run unit test suite
./gradlew testDebugUnitTest

# Lint check
./gradlew lintDebug
```

---

## 📄 Privacy & Data Safety

- **Local-First**: Journal text, selected moods, attached photos, and voice notes are stored directly within the application's private internal storage (`filesDir` and Room SQLite database).
- **Zero Third-Party Journal Sync**: We do not transmit or sync your private entries to external diary servers.
- **Third-Party Services**: Google Mobile Ads and Google Play Billing process device identifiers and diagnostics strictly according to Google's Privacy Policy. Detailed disclosures are available within the in-app Privacy Policy.

---

Made with ❤️ for mindful daily journaling.
