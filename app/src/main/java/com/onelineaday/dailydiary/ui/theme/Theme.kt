package com.onelineaday.dailydiary.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SunsetOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4D6),
    onPrimaryContainer = Color(0xFF3D1C00),
    
    secondary = LavenderMid,
    onSecondary = Color.White,
    secondaryContainer = LavenderLight,
    onSecondaryContainer = LavenderDark,
    
    tertiary = AccentTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCF5F2),
    onTertiaryContainer = Color(0xFF00403D),
    
    background = LightBackground,
    onBackground = LightOnBackground,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    
    outline = LightOutline,
    outlineVariant = Color(0xFFD6CFC6),
    
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColorScheme = darkColorScheme(
    primary = SunsetAmber,
    onPrimary = Color(0xFF3D1C00),
    primaryContainer = Color(0xFF5C3D00),
    onPrimaryContainer = Color(0xFFFFE4D6),
    
    secondary = LavenderLight,
    onSecondary = LavenderDark,
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = LavenderLight,
    
    tertiary = AccentTeal,
    onTertiary = Color(0xFF00403D),
    tertiaryContainer = Color(0xFF00605A),
    onTertiaryContainer = Color(0xFFCCF5F2),
    
    background = DarkBackground,
    onBackground = DarkOnBackground,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    
    outline = DarkOutline,
    outlineVariant = Color(0xFF49454F),
    
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

enum class AppTheme {
    DEFAULT, OCEAN, FOREST, MONOCHROME
}

@Composable
fun OneLineADayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.DEFAULT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.OCEAN -> if (darkTheme) {
            DarkColorScheme.copy(
                primary = Color(0xFF66B2FF),
                primaryContainer = Color(0xFF004C99),
                onPrimaryContainer = Color(0xFFCCE5FF),
                secondary = Color(0xFF66FFFF),
                tertiary = Color(0xFF00CCCC)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFF0066CC),
                primaryContainer = Color(0xFFCCE5FF),
                onPrimaryContainer = Color(0xFF003366),
                secondary = Color(0xFF009999),
                tertiary = Color(0xFF0073E6)
            )
        }
        AppTheme.FOREST -> if (darkTheme) {
            DarkColorScheme.copy(
                primary = Color(0xFF80E580),
                primaryContainer = Color(0xFF006600),
                onPrimaryContainer = Color(0xFFE5FFE5),
                secondary = Color(0xFFB3FFB3),
                tertiary = Color(0xFF33CC33)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFF008000),
                primaryContainer = Color(0xFFE5FFE5),
                onPrimaryContainer = Color(0xFF003300),
                secondary = Color(0xFF33CC33),
                tertiary = Color(0xFF006600)
            )
        }
        AppTheme.MONOCHROME -> if (darkTheme) {
            DarkColorScheme.copy(
                primary = Color(0xFFE0E0E0),
                primaryContainer = Color(0xFF424242),
                onPrimaryContainer = Color(0xFFF5F5F5),
                secondary = Color(0xFFBDBDBD),
                tertiary = Color(0xFF9E9E9E)
            )
        } else {
            LightColorScheme.copy(
                primary = Color(0xFF424242),
                primaryContainer = Color(0xFFE0E0E0),
                onPrimaryContainer = Color(0xFF212121),
                secondary = Color(0xFF616161),
                tertiary = Color(0xFF757575)
            )
        }
        AppTheme.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
