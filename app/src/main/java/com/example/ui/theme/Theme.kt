package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = VaultBlue,
    onPrimary = Color(0xFF001E30),
    primaryContainer = Color(0xFF123B59),
    onPrimaryContainer = Color(0xFFD0EDFF),
    secondary = VaultTextSecondary,
    background = VaultDarkBackground,
    onBackground = VaultTextPrimary,
    surface = VaultDarkSurface,
    onSurface = VaultTextPrimary,
    surfaceVariant = VaultDarkCard,
    onSurfaceVariant = VaultTextSecondary,
    outline = VaultDivider,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VaultBluePressed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EDFF),
    onPrimaryContainer = Color(0xFF001E30),
    background = VaultLightBackground,
    onBackground = Color(0xFF10161F),
    surface = VaultLightSurface,
    onSurface = Color(0xFF10161F),
    surfaceVariant = VaultLightCard,
    onSurfaceVariant = Color(0xFF556270),
  )

@Composable
fun GramVaultTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
