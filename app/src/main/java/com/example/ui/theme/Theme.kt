package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(raw: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == raw } ?: SYSTEM
    }
}

private val LightColorScheme = lightColorScheme(
    primary = TwelveLightPrimary,
    onPrimary = TwelveLightOnPrimary,
    primaryContainer = TwelveLightPrimaryContainer,
    onPrimaryContainer = TwelveLightOnPrimaryContainer,
    secondary = TwelveLightSecondary,
    onSecondary = TwelveLightOnSecondary,
    secondaryContainer = TwelveLightSecondaryContainer,
    onSecondaryContainer = TwelveLightOnSecondaryContainer,
    background = TwelveLightBackground,
    onBackground = TwelveLightOnBackground,
    surface = TwelveLightSurface,
    surfaceVariant = TwelveLightSurfaceVariant,
    onSurface = TwelveLightOnSurface,
    onSurfaceVariant = TwelveLightOnSurfaceVariant,
    outline = TwelveLightOutline,
    outlineVariant = TwelveLightOutlineVariant,
    error = TwelveLightError,
    onError = TwelveLightOnError,
    errorContainer = TwelveLightErrorContainer,
    onErrorContainer = TwelveLightOnErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = TwelveDarkPrimary,
    onPrimary = TwelveDarkOnPrimary,
    primaryContainer = TwelveDarkPrimaryContainer,
    onPrimaryContainer = TwelveDarkOnPrimaryContainer,
    secondary = TwelveDarkSecondary,
    onSecondary = TwelveDarkOnSecondary,
    secondaryContainer = TwelveDarkSecondaryContainer,
    onSecondaryContainer = TwelveDarkOnSecondaryContainer,
    background = TwelveDarkBackground,
    onBackground = TwelveDarkOnBackground,
    surface = TwelveDarkSurface,
    surfaceVariant = TwelveDarkSurfaceVariant,
    onSurface = TwelveDarkOnSurface,
    onSurfaceVariant = TwelveDarkOnSurfaceVariant,
    outline = TwelveDarkOutline,
    outlineVariant = TwelveDarkOutlineVariant,
    error = TwelveDarkError,
    onError = TwelveDarkOnError,
    errorContainer = TwelveDarkErrorContainer,
    onErrorContainer = TwelveDarkOnErrorContainer,
)

// Thẻ và bề mặt dùng bo góc 16dp theo quy chuẩn Twelve UI/UX
val TwelveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun TwelveTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TwelveShapes,
        content = content,
    )
}
