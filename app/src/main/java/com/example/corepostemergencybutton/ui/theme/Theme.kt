package com.example.corepostemergencybutton.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ClayRed,
    onPrimary = Fog,
    primaryContainer = Ember,
    onPrimaryContainer = Fog,
    secondary = Slate,
    onSecondary = Fog,
    tertiary = Mint,
    onTertiary = Ink,
    background = Fog,
    onBackground = Ink,
    surface = ColorTokens.surfaceLight,
    onSurface = Ink,
    surfaceVariant = Ash,
    onSurfaceVariant = Slate,
    error = ClayRedDark,
)

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    primaryContainer = ClayRedDark,
    onPrimaryContainer = Fog,
    secondary = Mint,
    onSecondary = Ink,
    tertiary = Slate,
    onTertiary = Fog,
    background = ColorTokens.backgroundDark,
    onBackground = Fog,
    surface = ColorTokens.surfaceDark,
    onSurface = Fog,
    surfaceVariant = ColorTokens.surfaceDarkVariant,
    onSurfaceVariant = Ash,
    error = Ember,
)

@Composable
fun CorePostTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

private object ColorTokens {
    val surfaceLight = Color(0xFFFFFFFF)
    val backgroundDark = Color(0xFF110F0E)
    val surfaceDark = Color(0xFF181414)
    val surfaceDarkVariant = Color(0xFF272120)
}
