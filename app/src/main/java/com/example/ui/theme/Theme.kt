package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorOutlineSubtle = androidx.compose.ui.graphics.Color(0xFF1D293F)

private val KumarDarkColorScheme = darkColorScheme(
    primary = MoltenGold,
    onPrimary = ObsidianVoid,
    primaryContainer = MoltenGoldDeep,
    onPrimaryContainer = MoltenGoldGlow,
    secondary = TelemetryCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = TelemetryCyanDeep,
    onSecondaryContainer = TelemetryCyan,
    tertiary = EmeraldOptimal,
    onTertiary = ObsidianVoid,
    tertiaryContainer = EmeraldDeep,
    onTertiaryContainer = EmeraldOptimal,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = TitaniumSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = TitaniumSurface,
    surfaceContainerHigh = TitaniumElevated,
    outline = GlassBorder,
    outlineVariant = ColorOutlineSubtle,
    error = CrimsonCritical,
    onError = ObsidianVoid,
    errorContainer = CrimsonDeep,
    onErrorContainer = CrimsonCritical
)

val KumarErpShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun KumarFoodsErpTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KumarDarkColorScheme,
        typography = KumarErpTypography,
        shapes = KumarErpShapes,
        content = content
    )
}
