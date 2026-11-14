package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NexoDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF083344),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianOutline,
    error = RoseError,
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFFE4E6)
)

private val NexoLightColorScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = CyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF164E63),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = LightBg,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

val NexoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun NexoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NexoDarkColorScheme else NexoLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NexoTypography,
        shapes = NexoShapes,
        content = content
    )
}
