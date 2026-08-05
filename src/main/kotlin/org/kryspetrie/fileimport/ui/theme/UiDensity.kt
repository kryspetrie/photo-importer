package org.kryspetrie.fileimport.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.UiDensity

/** Spacing and sizing tokens derived from [UiDensity]. */
data class UiDensityScale(
    val spacingXs: Dp,
    val spacingSm: Dp,
    val spacingMd: Dp,
    val spacingLg: Dp,
    val controlMinHeight: Dp,
    val iconSize: Dp,
    val thumbnailCardSize: Dp,
    val commandBarHeight: Dp,
)

fun UiDensity.toScale(): UiDensityScale =
    when (this) {
        UiDensity.COMPACT ->
            UiDensityScale(
                spacingXs = DefaultSpacing.xs,
                spacingSm = DefaultSpacing.sm,
                spacingMd = DefaultSpacing.md,
                spacingLg = DefaultSpacing.lg,
                controlMinHeight = DefaultSpacing.buttonHeightStandard,
                iconSize = DefaultSpacing.iconMedium + DefaultSpacing.xs,
                thumbnailCardSize = DefaultSpacing.thumbnailSize - DefaultSpacing.sm * 2,
                commandBarHeight = DefaultSpacing.buttonHeightTall,
            )
        UiDensity.COMFORTABLE ->
            UiDensityScale(
                spacingXs = DefaultSpacing.sm,
                spacingSm = DefaultSpacing.md,
                spacingMd = DefaultSpacing.lg,
                spacingLg = DefaultSpacing.xl,
                controlMinHeight = DefaultSpacing.buttonHeightStandard + DefaultSpacing.sm,
                iconSize = DefaultSpacing.iconMedium + DefaultSpacing.sm,
                thumbnailCardSize = DefaultSpacing.thumbnailSize + DefaultSpacing.sm * 2,
                commandBarHeight = DefaultSpacing.fieldHeight,
            )
        UiDensity.SPACIOUS ->
            UiDensityScale(
                spacingXs = DefaultSpacing.sm + DefaultSpacing.xs,
                spacingSm = DefaultSpacing.md + DefaultSpacing.xs,
                spacingMd = DefaultSpacing.xl,
                spacingLg = DefaultSpacing.xl + DefaultSpacing.sm,
                controlMinHeight = DefaultSpacing.buttonHeightTall,
                iconSize = DefaultSpacing.iconMedium + DefaultSpacing.md - DefaultSpacing.xs,
                thumbnailCardSize = DefaultSpacing.thumbnailSize + DefaultSpacing.xxl,
                commandBarHeight = DefaultSpacing.fieldHeight + DefaultSpacing.sm,
            )
    }

val LocalUiDensityScale = staticCompositionLocalOf { UiDensity.COMFORTABLE.toScale() }

@Composable
fun DensityThemeProvider(density: UiDensity, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUiDensityScale provides density.toScale()) { content() }
}

object UiDensityDefaults {
    /** Metadata editor narrow layout breakpoint (browser drawer). */
    val metadataEditorNarrowBreakpoint = 1100.dp

    /** Metadata editor ultra-wide breakpoint (three panes). */
    val metadataEditorUltraWideBreakpoint = 1600.dp
}
