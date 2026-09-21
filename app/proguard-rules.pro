# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.onelineaday.dailydiary.data.** { *; }

# iText PDF
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# SLF4J (iText dependency)
-dontwarn org.slf4j.**

# Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Google Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
-keep class com.android.vending.billing.** { *; }

# Keep Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep Compose
-dontwarn androidx.compose.**

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Keep Biometric
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**
