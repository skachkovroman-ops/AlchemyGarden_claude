package com.example.alchemygarden.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

private val Colors = lightColorScheme(
    primary = Forest, onPrimary = Parchment,
    secondary = Amber, onSecondary = Ink,
    background = Parchment, onBackground = Ink,
    surface = Parchment, onSurface = Ink,
    surfaceVariant = ParchmentDark, onSurfaceVariant = Ink,
    primaryContainer = ParchmentDark, onPrimaryContainer = Ink,
    error = Berry
)

@Composable
fun AlchemyTheme(content: @Composable () -> Unit) {
    val base = Typography()
    // Когда появится свой шрифт — подставь его вместо FontFamily.Serif.
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.Serif)
    )
    MaterialTheme(colorScheme = Colors, typography = typography, content = content)
}
