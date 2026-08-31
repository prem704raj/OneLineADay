plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.onelineaday.dailydiary"

    // Android 16
    compileSdk = 36

    ndkVersion = "25.0.1"

    defaultConfig {
        applicationId = "com.onelineaday.dailydiary"

        // Keep Android 8.0 as minimum supported Android version
        minSdk = 26

        // Required for Google Play submissions from August 31, 2026
        targetSdk = 36

        // Previous repository versionCode was 6.
        // Every Play Store update must use a higher versionCode.
        versionCode = 7
        versionName = "4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // -------------------------
    // Android Core
    // -------------------------

    implementation("androidx.core:core-ktx:1.12.0")

    implementation(
        "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
    )

    implementation(
        "androidx.activity:activity-compose:1.8.2"
    )

    // -------------------------
    // Jetpack Compose
    // -------------------------

    implementation(
        platform("androidx.compose:compose-bom:2024.06.00")
    )

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // -------------------------
    // Navigation
    // -------------------------

    implementation(
        "androidx.navigation:navigation-compose:2.7.6"
    )

    // -------------------------
    // Biometric Authentication
    // -------------------------

    implementation(
        "androidx.biometric:biometric:1.2.0-alpha05"
    )

    // -------------------------
    // Room Database
    // -------------------------

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")

    ksp(
        "androidx.room:room-compiler:2.7.1"
    )

    // -------------------------
    // ViewModel
    // -------------------------

    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
    )

    // -------------------------
    // Images / Video thumbnails
    // -------------------------

    implementation(
        "io.coil-kt:coil-compose:2.5.0"
    )

    implementation(
        "io.coil-kt:coil-video:2.5.0"
    )

    // -------------------------
    // Preferences
    // -------------------------

    implementation(
        "androidx.datastore:datastore-preferences:1.0.0"
    )

    // -------------------------
    // PDF generation
    // -------------------------

    implementation(
        "com.itextpdf:itext7-core:7.2.5"
    )

    // -------------------------
    // Google AdMob
    // -------------------------

    implementation(
        "com.google.android.gms:play-services-ads:23.6.0"
    )

    // -------------------------
    // Google Play Billing
    // -------------------------

    implementation(
        "com.android.billingclient:billing-ktx:6.1.0"
    )

    // -------------------------
    // Google Fonts
    // -------------------------

    implementation(
        "androidx.compose.ui:ui-text-google-fonts:1.6.0"
    )

    // -------------------------
    // JSON Backup
    // -------------------------

    implementation(
        "com.google.code.gson:gson:2.10.1"
    )

    // -------------------------
    // Splash Screen
    // -------------------------

    implementation(
        "androidx.core:core-splashscreen:1.0.1"
    )

    // -------------------------
    // Accompanist
    // -------------------------

    implementation(
        "com.google.accompanist:accompanist-systemuicontroller:0.32.0"
    )

    // -------------------------
    // Unit tests
    // -------------------------

    testImplementation(
        "junit:junit:4.13.2"
    )

    // -------------------------
    // Instrumentation tests
    // -------------------------

    androidTestImplementation(
        "androidx.test.ext:junit:1.1.5"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.5.1"
    )

    androidTestImplementation(
        platform("androidx.compose:compose-bom:2024.01.00")
    )

    androidTestImplementation(
        "androidx.compose.ui:ui-test-junit4"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )

    debugImplementation(
        "androidx.compose.ui:ui-test-manifest"
    )
}
