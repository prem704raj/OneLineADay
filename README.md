# One Line A Day 📝

A modern, privacy-focused micro-journaling application built with Jetpack Compose and Material 3. Capture life effortlessly in one sentence a day with zero friction, local-first storage, rich multimedia attachments, and deep statistics.

---

## ✨ Features

| Feature | Description |
|---|---|
| **📖 Multi-Journal Support** | Create and manage multiple distinct journals for different areas of life |
| **📅 Backdate Entries** | Capture or edit entries for past dates with quick calendar navigation |
| **🎙️ Audio Voice Notes** | In-app audio recorder with failure-safe lifecycle, real-time waveform, and playback progress |
| **📷 Photo & Video Attachments** | Attach photos and videos via Android's privacy-preserving Photo Picker |
| **😊 Mood Tracking** | 10 expressive moods with animated emoji reactions and distribution charts |
| **🏷️ Tags** | Tag memories for quick categorization, search, and progressive filtering |
| **🔔 Daily Reminders** | Configurable daily reminder notifications restored automatically on reboot |
| **🔥 Streak Tracking** | Non-destructive current and longest streak calculation to build lasting habits |
| **📊 Mood Trend Chart & Stats** | Visual mood trends, weekly & monthly entry counts, and milestone statistics |
| **🗓️ Calendar Heatmap** | Interactive visual activity heatmap tracking your journaling consistency |
| **🎉 Year in Review** | Curated annual recap summarizing memories, moods, and highlights |
| **🔒 Biometric App Lock** | Biometric, fingerprint, PIN, or device credential authentication with Recents screenshot protection |
| **📤 PDF Export** | Export formatted journal entries using Android native PdfDocument with StaticLayout text shaping |
| **💾 ZIP Backup & Restore** | Safe, versioned ZIP archive backup and atomic restore with checksum & traversal verification |
| **🎨 Dark/Light/System Theme** | WCAG AA accessible themes with support for Dark, Light, and System modes |
| **🌍 15 Language Localization** | Full localization support across 15 languages |
| **📱 Home Screen Widget** | Glanceable today's line on your home screen with private mode |
| **📢 AdMob Integration** | AdMob integration with UMP consent management |
| **💎 Play Billing** | Play Billing with monthly subscription and lifetime premium purchase options |

---

## 🏗️ Architecture & Technology Stack

- **Architecture**: MVVM with AndroidViewModel
- **UI Framework**: Jetpack Compose Material 3
- **Local Persistence**: Room database with migrations 1-5
- **Concurrency & Reactivity**: Kotlin Coroutines + Flow
- **Image Loading**: Coil for images
- **PDF Generation**: Android native PdfDocument with StaticLayout text shaping
- **In-App Billing**: Play Billing 9.1 (monthly + lifetime premium)
- **Advertising**: AdMob integration with UMP consent
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
│   ├── utils/                        # StreakCalculator, DateFormatters, PdfExportHelper, ShareImageHelper
│   ├── viewmodel/                    # JournalViewModel with single-flight saves & cancellable jobs
│   └── widget/                       # JournalWidget provider & layout
└── res/
    ├── values/                       # strings.xml, colors, themes (15 language localizations)
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
- Gradle 9.5.0
- AGP 9.2.1
- Kotlin 2.1.20
- Target SDK 36 (Android 16)
- Min SDK 26 (Android 8.0)

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
