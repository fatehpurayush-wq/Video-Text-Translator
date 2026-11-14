package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppThemeMode

private fun buildColorScheme(mode: AppThemeMode): ColorScheme {
    val bg: Color
    val surface: Color
    val elevated: Color
    val highest: Color
    when (mode) {
        AppThemeMode.OBSIDIAN -> {
            bg = ObsidianBg
            surface = ObsidianSurface
            elevated = ObsidianSurfaceElevated
            highest = ObsidianSurfaceHighest
        }
        AppThemeMode.AMOLED -> {
            bg = AmoledBg
            surface = AmoledSurface
            elevated = AmoledSurfaceElevated
            highest = AmoledSurfaceHighest
        }
        AppThemeMode.SLATE -> {
            bg = SlateBg
            surface = SlateSurface
            elevated = SlateSurfaceElevated
            highest = SlateSurfaceHighest
        }
    }
    return darkColorScheme(
        primary = CyanElectric,
        onPrimary = ObsidianBg,
        primaryContainer = CyanContainer,
        onPrimaryContainer = OnCyanContainer,
        secondary = AmberWarm,
        onSecondary = ObsidianBg,
        secondaryContainer = AmberContainer,
        onSecondaryContainer = OnAmberContainer,
        tertiary = EmeraldReady,
        onTertiary = ObsidianBg,
        tertiaryContainer = EmeraldContainer,
        onTertiaryContainer = EmeraldReady,
        error = RoseAlert,
        onError = ObsidianBg,
        errorContainer = RoseContainer,
        onErrorContainer = RoseAlert,
        background = bg,
        onBackground = TextPrimary,
        surface = surface,
        onSurface = TextPrimary,
        surfaceVariant = elevated,
        onSurfaceVariant = TextSecondary,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surface,
        surfaceContainer = elevated,
        surfaceContainerHigh = highest,
        surfaceContainerHighest = highest,
        outline = ObsidianOutline,
        outlineVariant = ObsidianOutline.copy(alpha = 0.55f)
    )
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VideoTextTranslatorTheme(
    themeMode: AppThemeMode = AppThemeMode.OBSIDIAN,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = buildColorScheme(themeMode),
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
