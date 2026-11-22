package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ScreenStopDarkColorScheme = darkColorScheme(
    primary = AlabasterInk,
    onPrimary = ObsidianCanvas,
    primaryContainer = GraphiteHighest,
    onPrimaryContainer = AlabasterInk,
    secondary = SignalVermilion,
    onSecondary = AlabasterInk,
    secondaryContainer = SignalVermilionDim,
    onSecondaryContainer = SignalVermilionBright,
    tertiary = SafeEmerald,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = SafeEmeraldDim,
    onTertiaryContainer = SafeEmerald,
    background = ObsidianCanvas,
    onBackground = AlabasterInk,
    surface = CarbonSurface,
    onSurface = AlabasterInk,
    surfaceVariant = SlateContainer,
    onSurfaceVariant = ZincSecondary,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = SlateContainer,
    surfaceContainerHigh = GraphiteHighest,
    surfaceContainerHighest = HairlineBorder,
    outline = HairlineBorder,
    outlineVariant = HairlineSubtle,
    error = SignalVermilion,
    onError = AlabasterInk,
    errorContainer = SignalVermilionDim,
    onErrorContainer = SignalVermilionBright
)

val ScreenStopShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScreenStopTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ScreenStopDarkColorScheme,
        typography = ScreenStopTypography,
        shapes = ScreenStopShapes,
        content = content
    )
}
