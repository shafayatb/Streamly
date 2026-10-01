package com.shafayatb.streamly.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

private val LightColors: ColorScheme = lightColorScheme(
    primary = StreamlyPalette.Indigo700,
    onPrimary = StreamlyPalette.White,
    primaryContainer = StreamlyPalette.Indigo100,
    onPrimaryContainer = StreamlyPalette.Indigo900,
    secondary = StreamlyPalette.Indigo500,
    onSecondary = StreamlyPalette.White,
    background = StreamlyPalette.Mist50,
    onBackground = StreamlyPalette.Ink900,
    surface = StreamlyPalette.White,
    onSurface = StreamlyPalette.Ink900,
    surfaceVariant = StreamlyPalette.Mist100,
    onSurfaceVariant = StreamlyPalette.Slate500,
    outline = StreamlyPalette.Slate300,
    error = StreamlyPalette.Coral500,
    onError = StreamlyPalette.White,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = StreamlyPalette.Indigo200,
    onPrimary = StreamlyPalette.Indigo950,
    primaryContainer = StreamlyPalette.Indigo700,
    onPrimaryContainer = StreamlyPalette.Indigo100,
    secondary = StreamlyPalette.Indigo200,
    onSecondary = StreamlyPalette.Indigo950,
    background = StreamlyPalette.Night900,
    onBackground = StreamlyPalette.Mist50,
    surface = StreamlyPalette.Night800,
    onSurface = StreamlyPalette.Mist50,
    surfaceVariant = StreamlyPalette.Night700,
    onSurfaceVariant = StreamlyPalette.Slate300,
    outline = StreamlyPalette.Slate500,
    error = StreamlyPalette.Coral200,
    onError = StreamlyPalette.Indigo950,
)

private val StreamlyTypography: Typography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
public fun StreamlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StreamlyTypography,
        content = content,
    )
}
