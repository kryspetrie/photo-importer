package org.kryspetrie.fileimport.domain.model

/**
 * Aspect ratio presets for photo cropping and export.
 *
 * Provides common aspect ratios with orientation awareness and common print sizes. Portrait ratios
 * have a value < 1.0, landscape ratios > 1.0, and square = 1.0.
 *
 * The [value] is expressed as width/height ratio (e.g., 3:2 = 1.5, 2:3 ≈ 0.667). A value of 0.0
 * means "use current/original aspect ratio" (no snapping).
 *
 * This enum supersedes the former `AspectRatioPreset`, which is now removed. Entry names follow the
 * `LANDSCAPE_W_H` / `PORTRAIT_H_W` convention for clarity.
 */
enum class AspectRatio(val displayName: String, val value: Double, val printSize: String = "") {
    ORIGINAL("Original", 0.0, "Use photo's original aspect ratio"),
    SQUARE("Square (1:1)", 1.0, "4x4, 8x8, 12x12 inches"),
    PORTRAIT_4_3("Portrait (4:3)", 3.0 / 4.0, "4x3, 8x6, 12x9 inches"),
    LANDSCAPE_3_4("Landscape (3:4)", 4.0 / 3.0, "3x4, 6x8, 9x12 inches"),
    PORTRAIT_2_3("Portrait (2:3)", 2.0 / 3.0, "2x3, 4x6, 8x12 inches"),
    LANDSCAPE_3_2("Landscape (3:2)", 3.0 / 2.0, "3x2, 6x4, 12x8 inches"),
    PORTRAIT_5_7("Portrait (5:7)", 5.0 / 7.0, "5x7, 10x14 inches"),
    PORTRAIT_4_5("Portrait (4:5)", 4.0 / 5.0, "4x5, 8x10, 16x20 inches"),
    LANDSCAPE_5_4("Landscape (5:4)", 5.0 / 4.0, "5x4, 10x8 inches"),
    WIDE_16_9("Wide (16:9)", 16.0 / 9.0, "Computer screens, TVs"),
    PANORAMA_3_1("Panorama (3:1)", 3.0 / 1.0, "Wide prints, banners");

    /**
     * Returns true if this ratio is portrait-oriented (value < 1.0, excluding ORIGINAL and SQUARE).
     */
    fun isPortrait(): Boolean = value > 0.0 && value < 1.0
}
