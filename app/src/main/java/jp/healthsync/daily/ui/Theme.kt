package jp.healthsync.daily.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Pine = Color(0xFF0F3D3E)
private val PineSoft = Color(0xFFD5E6E2)
private val Persimmon = Color(0xFFB85C38)
private val Paper = Color(0xFFF4F1EA)
private val Card = Color(0xFFFFFCF7)
private val Ink = Color(0xFF1C1B19)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF6F3EE),
    primaryContainer = PineSoft,
    onPrimaryContainer = Pine,
    secondary = Persimmon,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E1D6),
    onSurfaceVariant = Color(0xFF3E4947),
    error = Color(0xFF9B2335),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7D6D0),
    onPrimary = Color(0xFF08302F),
    primaryContainer = Color(0xFF1E4A49),
    onPrimaryContainer = PineSoft,
    secondary = Color(0xFFE59A7A),
    onSecondary = Color(0xFF3D1A0C),
    background = Color(0xFF141615),
    onBackground = Color(0xFFE8E4DC),
    surface = Color(0xFF1E2221),
    onSurface = Color(0xFFE8E4DC),
    surfaceVariant = Color(0xFF2A312F),
    onSurfaceVariant = Color(0xFFC5CDC9),
    error = Color(0xFFFFB3B8),
)

@Composable
fun HealthDailyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
