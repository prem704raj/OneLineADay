# OneLineADay — 16-Phase Production Roadmap

## Architecture Overview
OneLineADay is a privacy-first, local-first daily journaling and memory application built on modern Jetpack Compose (Material 3), Room DB (Version 5 baseline, strictly non-destructive), Kotlin Coroutines/Flow, WorkManager, Coil, AdMob, and Play Billing 9.1.0.

---

## Milestone Mapping & Feature Inventory

| # | Feature Code | Feature Name | Milestone | Status | Verification & Target Deliverables |
|---|---|---|---|---|---|
| F1 | `BACKUP_CRYPTO` | AES-256-GCM Backup Encryption (`.olad.enc`) | M1 | **COMPLETED** | PBKDF2-HMAC-SHA256 (120k iterations), 16B salt, 12B nonce, AAD header binding |
| F2 | `BACKUP_STREAM` | Stream-Based Backup Pipeline & Two-Phase Restore | M1 | **COMPLETED** | Stream-based Cipher I/O, $O(1)$ memory, atomic staging rollback on bad tag |
| F3 | `BACKUP_CLOUD` | Cloud Storage Abstraction (Google Drive & WebDAV) | M1 | **COMPLETED** | Direct REST v3 (`drive.appdata`) + RFC 4918 WebDAV (`MKCOL`/`PUT`/`PROPFIND`) |
| F4 | `BACKUP_COMPAT` | Transparent Legacy ZIP Sniffing & Backward Compat | M1 | **COMPLETED** | `PK\x03\x04` auto-detection, zip-slip defense, user security notice |
| F5 | `BACKUP_SCHED` | Automated Cloud & Local Backup Scheduling | M1 | **COMPLETED** | WorkManager periodic worker, unmetered network, battery not low, 2-hr flex window |
| F6 | `MEM_TIMELINE` | On This Day 2.0 (5-Year Historical Flashbacks) | M2 | **COMPLETED** | Multi-year date matching ($Y-1$ to $Y-5$), Room indexed B-Tree seek Flow, photo/audio timeline |
| F7 | `MEM_LEAPDAY` | Leap Day (Feb 29) Observation Engine | M2 | **COMPLETED** | Gregorian century math, Feb 29 quadrennial mapping, Feb 28 non-leap observation with visual badge |
| F8 | `MEM_NOTIF` | Privacy-Safe Flashback Notifications | M2 | **COMPLETED** | Generic lockscreen teaser notifications (`VISIBILITY_PRIVATE`), deep link gated by AppLockManager |
| F9 | `MEM_CARDS` | High-Resolution Shareable Story Cards | M2 | **COMPLETED** | 9:16 vertical (1080x1920) and 16:9 horizontal (1920x1080) with 5 themes, zero distortion |
| F10 | `PROMPT_CATALOG` | 10-Category Daily Spark Prompt Catalog | M2 | **COMPLETED** | 320 curated prompts across 10 categories + 1 quadrennial leap prompt, pure compiled Kotlin |
| F11 | `PROMPT_ROTATION`| Deterministic Daily Rotation & Home Integration | M2 | **COMPLETED** | Day-of-year seeded formula, category filters, shuffle offset, Home Screen card with 1-tap answering |
| F12 | `PIXELS_MOSAIC` | Year in Pixels 12-Month Mood Mosaic | M3 | **COMPLETED** | 12x31 interactive grid with annual navigation |
| F13 | `PIXELS_STATS` | Custom Mood Palettes & Annual Metrics | M3 | **COMPLETED** | Mood distribution counts, dominant mood, and transition stability score |
| F14 | `PIXELS_EXPORT` | Year in Pixels Social Export Card | M3 | **COMPLETED** | High-res 2000x2400 share card with annual stats and custom palette |
| F15 | `WIDGET_TODAY` | Interactive Widget: Today's Line | M3 | **COMPLETED** | Home screen widget with direct write/open intent |
| F16 | `WIDGET_MOOD` | Interactive Widget: Quick Mood | M3 | **COMPLETED** | 1-tap mood logging & Year in Pixels launcher from widget |
| F17 | `WIDGET_JOURNAL`| Interactive Widget: Quick Journal | M3 | **COMPLETED** | Compact launcher and text logger with direct app routing |
| F18 | `WIDGET_LOCK` | Widget Biometric Security Masking | M3 | **COMPLETED** | Obfuscates widget content and gates deep links with AppLockManager |
| F19 | `STREAK_FREEZE` | Streak Freezes & Inventory Persisted in DB | M4 | **COMPLETED** | Additive `streak_freezes` Room table (v6), inventory computation, and auto-consumption |
| F20 | `STREAK_GRACE` | Streak 24-Hour Grace Period & Rest Days | M4 | **COMPLETED** | 24-hour grace window, scheduled rest days, and HomeScreen freeze badge |
| F21 | `AI_VOICE_LOCAL` | On-Device Voice-to-Line Transcription | M4 | **COMPLETED** | Offline Android SpeechRecognizer (`EXTRA_PREFER_OFFLINE=true`), zero cloud audio streaming |
| F22 | `AI_DISTILL` | Concise Daily Line Distillation | M4 | **COMPLETED** | Deterministic single-line text distillation, filler stripping, and 1-tap UI action |
| F23 | `AI_PRIVACY` | Zero Hardcoded Keys & BYOK Security | M4 | **COMPLETED** | Android Keystore AES-256-GCM encrypted BYOK storage and strict opt-in consent |
| F24 | `INSIGHTS_LIFE` | Weekly Life Patterns Insights Engine | M5 | **COMPLETED** | Factual day-of-week, mood correlation, and reflective word count analytics |
| F25 | `VIDEO_RECAP` | Monthly Memory Video Recap & 1-Second Reel | M5 | **COMPLETED** | Monthly photo sequence compilation with 1.0s/day frame duration |
| F26 | `CAPSULE_TIME` | Future Self Time Capsules | M5 | **COMPLETED** | Time-locked encrypted entries with scheduled local reveal notifications |
| F27 | `PDF_BOOK` | "My Life in a Book" High-Res PDF Export | M5 | **COMPLETED** | Multi-page book-formatted PDF with cover, metadata, and photos |
| F28 | `SEARCH_LOCAL` | Local Semantic Search ("Ask My Journal") | M5 | **COMPLETED** | Ranked multi-term scoring, semantic mood intents, and tag filters |
| F29 | `CONSTELLATION` | Visual Memory Constellation | M5 | **COMPLETED** | Celestial spiral node graph connecting memories sharing tags and dates |
| F30 | `DUO_JOURNAL` | Isolated Duo Journaling Architecture | M5 | **COMPLETED** | Isolated secondary partner journal partition with independent PIN hashing |
| F31 | `MINDFUL_ZEN` | Mindful Zen Garden | M5 | **COMPLETED** | Unlocked calm garden items across 3, 7, 14, 30, 60, 100 streak milestones |
| F32 | `E2E_HARDENING` | Full Adversarial E2E Validation | M6 | **COMPLETED** | 100% test pass rate across 32 suites (244 tests) spanning Tiers 1-5 |

---

## Phase Status Summary
- **Milestone 1 (Phase 1)**: Cryptographic Backup & Cloud Storage Engine — **COMPLETED**
- **Milestone 2 (Phase 2 & 3)**: On This Day 2.0, Five-Year Timeline & Daily Spark Prompts — **COMPLETED**
- **Milestone 3 (Phase 4 & 5)**: Year in Pixels & Interactive Widgets — **COMPLETED**
- **Milestone 4 (Phase 6 & 7)**: Streak Resilience & Privacy-First AI Audio — **COMPLETED**
- **Milestone 5 (Phases 8 to 16)**: Advanced Memory Suite & Insights — **COMPLETED**
- **Milestone 6**: 100% E2E Pass & Adversarial Hardening — **COMPLETED & VERIFIED**