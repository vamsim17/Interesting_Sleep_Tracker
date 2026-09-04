package com.example.interesting_sleep_tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme(val label: String, val icon: String) {
    DARK("Dark", "☾"),
    LIGHT("Light", "☼"),
    MIDNIGHT("Midnight", "✦"),
    SUNSET("Sunset", "🌇"),
    FOREST("Forest", "🍃"),
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

private val MidnightColorScheme = darkColorScheme(
    primary = Color(0xFF80D8FF),
    secondary = Color(0xFF40C4FF),
    tertiary = Color(0xFF00B0FF),
    background = Color(0xFF050B14),
    surface = Color(0xFF0D1527),
    surfaceVariant = Color(0xFF162238),
    onPrimary = Color(0xFF003258),
    onSecondary = Color(0xFF003258),
    onBackground = Color(0xFFE1F5FE),
    onSurface = Color(0xFFE1F5FE),
    onSurfaceVariant = Color(0xFFB3E5FC),
)

private val SunsetColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    secondary = Color(0xFFFF8A65),
    tertiary = Color(0xFFE57373),
    background = Color(0xFF1A120B),
    surface = Color(0xFF2C1D11),
    surfaceVariant = Color(0xFF3E2B1E),
    onPrimary = Color(0xFF4A2800),
    onSecondary = Color(0xFF4A1900),
    onBackground = Color(0xFFFFF3E0),
    onSurface = Color(0xFFFFF3E0),
    onSurfaceVariant = Color(0xFFFFE0B2),
)

private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFFA5D6A7),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFC5E1A5),
    background = Color(0xFF0B140D),
    surface = Color(0xFF142417),
    surfaceVariant = Color(0xFF1E3523),
    onPrimary = Color(0xFF0A3818),
    onSecondary = Color(0xFF003731),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9),
    onSurfaceVariant = Color(0xFFC8E6C9),
)

@Composable
fun Interesting_Sleep_TrackerTheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (appTheme) {
        AppTheme.DARK -> DarkColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.MIDNIGHT -> MidnightColorScheme
        AppTheme.SUNSET -> SunsetColorScheme
        AppTheme.FOREST -> ForestColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
