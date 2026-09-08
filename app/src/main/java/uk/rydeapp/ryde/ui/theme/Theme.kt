package uk.rydeapp.ryde.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9BB3FF),
    onPrimary = DeepNavy,
    primaryContainer = Color(0xFF2148A8),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF72E5C5),
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFF144C45),
    onSecondaryContainer = Color(0xFFD5F5EC),
    background = DeepNavy,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = Color(0xFFC4CCE1),
    outline = Color(0xFF8490AC)
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = MidnightNavy,
    secondary = Color(0xFF087966),
    onSecondary = Color.White,
    secondaryContainer = PaleMint,
    onSecondaryContainer = Color(0xFF064B40),
    background = WarmOffWhite,
    onBackground = MidnightNavy,
    surface = Color.White,
    onSurface = MidnightNavy,
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Slate,
    outline = Color(0xFF747E98)
)

@Composable
fun RydeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
