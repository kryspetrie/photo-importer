package org.kryspetrie.fileimport.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing design tokens for the PhotoImporter application.
 *
 * Provides a consistent spacing scale based on a 4dp base unit, following the Material Design 3
 * spacing recommendations. All spacing values in the UI should reference these tokens instead of
 * hardcoded dp values.
 *
 * ## Usage
 *
 * ```kotlin
 * Spacer(Modifier.height(DefaultSpacing.md))
 * Column(modifier = Modifier.padding(DefaultSpacing.lg))
 * ```
 *
 * ## Scale
 * | Token | Value | Usage                                |
 * |-------|-------|--------------------------------------|
 * | none  | 0dp   | No spacing                           |
 * | xs    | 2dp   | Tight padding inside chips, tags     |
 * | sm    | 4dp   | Inline padding, small gaps           |
 * | md    | 8dp   | Default padding, standard gaps       |
 * | lg    | 12dp  | Section padding, card inner padding  |
 * | xl    | 16dp  | Section spacing, card outer padding  |
 * | xxl   | 24dp  | Major section breaks, dialog padding |
 * | xxxl  | 32dp  | Screen-level margins, hero spacing   |
 * | huge  | 48dp  | Top-level vertical spacing           |
 */
object DefaultSpacing {
    val none = 0.dp
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp

    // ── Semantic spacing ──────────────────────────────────────

    /** Padding inside a button or small interactive element. */
    val buttonContentHorizontal = 8.dp

    /** Height of a compact button (e.g., inline action). */
    val buttonHeightCompact = 28.dp

    /** Height of a standard button. */
    val buttonHeightStandard = 32.dp

    /** Height of a tall/primary button. */
    val buttonHeightTall = 40.dp

    /** Height of an input field. */
    val fieldHeight = 48.dp

    /** Width of the sidebar thumbnail strip. */
    val sidebarWidth = 120.dp

    /** Width/height of a single thumbnail in the sidebar. */
    val thumbnailSize = 80.dp

    /** Size of a small icon button (e.g., toolbar). */
    val iconButtonSmall = 24.dp

    /** Size of a standard icon button. */
    val iconButtonStandard = 32.dp

    /** Size of a medium icon. */
    val iconMedium = 16.dp

    /** Size of a large icon. */
    val iconLarge = 64.dp

    /** Width for a narrow input field (e.g., destination path). */
    val fieldWidthNarrow = 220.dp

    /** Width for a standard input/content panel. */
    val panelWidthStandard = 400.dp

    /** Maximum width for dialog content. */
    val dialogWidthMax = 600.dp

    /** Corner radius for small rounded elements (chips, tags). */
    val cornerSmall = 4.dp

    /** Corner radius for cards, dialogs. */
    val cornerMedium = 6.dp

    /** Corner radius for large cards, sheets. */
    val cornerLarge = 8.dp

    /** Border width for thin outlines. */
    val borderThin = 1.dp

    /** Divider thickness. */
    val dividerThickness = 1.dp

    /** Stroke width for circular progress indicators. */
    val progressStrokeWidth = 2.dp

    /** Circular progress indicator size (compact). */
    val progressSizeCompact = 14.dp
}
