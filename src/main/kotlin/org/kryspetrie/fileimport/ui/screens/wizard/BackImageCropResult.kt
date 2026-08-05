package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.geometry.Rect

/**
 * Result of a back-image crop/quad selection.
 * - [rect]: Normalized rectangle crop (left, top, right, bottom) if rectangular crop was used.
 * - [quad]: 4-point perspective corners if quad crop was used.
 * - Exactly one of [rect] or [quad] should be non-null (or both null for full image).
 */
data class BackImageCropResult(val rect: Rect? = null, val quad: QuadCorners? = null) {
    /**
     * Convert to flat list for storage.
     * - 4 values: rect crop [left, top, right, bottom]
     * - 8 values: quad crop [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]
     * - null: full image (no crop)
     */
    fun toNormalizedList(): List<Float>? {
        return when {
            quad != null -> quad.toFlatList()
            rect != null -> listOf(rect.left, rect.top, rect.right, rect.bottom)
            else -> null
        }
    }
}
