package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = NavyLight,
    onPrimaryContainer = Color.White,
    secondary = GreenIncome,
    onSecondary = Color.White,
    tertiary = PurpleBalance,
    onTertiary = Color.White,
    background = Color(0xFF12181F),
    surface = NavySidebar,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NavyLight,
    outline = Color(0xFF475569)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBF5FB),
    onPrimaryContainer = PrimaryBlueDark,
    secondary = GreenIncome,
    onSecondary = Color.White,
    secondaryContainer = GreenLight,
    onSecondaryContainer = Color(0xFF1E8449),
    tertiary = PurpleBalance,
    onTertiary = Color.White,
    tertiaryContainer = PurpleLight,
    onTertiaryContainer = Color(0xFF6C3483),
    background = GrayBackground,
    surface = GraySurface,
    onBackground = NavySidebar,
    onSurface = NavySidebar,
    surfaceVariant = Color(0xFFF8FAFC),
    outline = GrayBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

