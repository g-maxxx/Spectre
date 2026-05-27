package dev.thomasbuilds.spectre.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.thomasbuilds.spectre.settings.ThemeMode

private val DarkColors =
  darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.Black,
    secondary = DarkAccent,
    onSecondary = Color.Black,
    background = DarkBg,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = SpectreRed,
    onError = Color.Black
  )

private val LightColors =
  lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    secondary = LightAccent,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = SpectreRed,
    onError = Color.White
  )

@Composable
fun SpectreTheme(
  mode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val useDark =
    when (mode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme = if (useDark) DarkColors else LightColors

  MaterialTheme(
    colorScheme = colorScheme,
    content = content
  )
}
