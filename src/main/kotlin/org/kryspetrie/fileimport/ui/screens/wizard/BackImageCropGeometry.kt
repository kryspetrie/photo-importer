@file:Suppress("TooManyFunctions", "MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Transform a normalized crop rectangle for a 90° clockwise image rotation.
 *
 * When the displayed image rotates 90° CW, normalized coordinates transform as (x, y) → (1-y, x).
 * This matches [FaceRegion.rotate90CW].
 */
internal fun Rect.rotate90CW(): Rect {
    val x1 = 1.0f - bottom
    val y1 = left
    val x2 = 1.0f - top
    val y2 = right
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/** Transform a normalized crop rectangle for a 90° counter-clockwise image rotation. */
internal fun Rect.rotate90CCW(): Rect {
    val x1 = top
    val y1 = 1.0f - right
    val x2 = bottom
    val y2 = 1.0f - left
    return Rect(
        left = minOf(x1, x2),
        top = minOf(y1, y2),
        right = maxOf(x1, x2),
        bottom = maxOf(y1, y2),
    )
}

/** Transform a normalized crop rectangle for a 180° image rotation. */
internal fun Rect.rotate180(): Rect {
    return Rect(left = 1.0f - right, top = 1.0f - bottom, right = 1.0f - left, bottom = 1.0f - top)
}

// ─── Quad (4-point) rotation transforms ───────────────────────────────────────────────────

/** Quad as 4 normalized (x,y) points: topLeft, topRight, bottomRight, bottomLeft. */
data class QuadCorners(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset,
) {
    /** Convert to flat list: [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]. */
    fun toFlatList(): List<Float> =
        listOf(
            topLeft.x,
            topLeft.y,
            topRight.x,
            topRight.y,
            bottomRight.x,
            bottomRight.y,
            bottomLeft.x,
            bottomLeft.y,
        )

    /** Rotate quad corners 90° CW when the displayed image rotates. */
    fun rotate90CW(): QuadCorners {
        // When image rotates 90° CW: (x,y) → (1-y, x) for each point
        // And corner labels shift: topLeft ← topRight, topRight ← bottomRight, etc.
        val t = { p: Offset -> Offset(1f - p.y, p.x) }
        return QuadCorners(
            topLeft = t(topRight),
            topRight = t(bottomRight),
            bottomRight = t(bottomLeft),
            bottomLeft = t(topLeft),
        )
    }

    /** Rotate quad corners 90° CCW when the displayed image rotates. */
    fun rotate90CCW(): QuadCorners {
        // (x,y) → (y, 1-x) for each point
        // Corner labels shift: topLeft ← bottomLeft, topRight ← topLeft, etc.
        val t = { p: Offset -> Offset(p.y, 1f - p.x) }
        return QuadCorners(
            topLeft = t(bottomLeft),
            topRight = t(topLeft),
            bottomRight = t(topRight),
            bottomLeft = t(bottomRight),
        )
    }

    /** Rotate quad corners 180° when the displayed image rotates. */
    fun rotate180(): QuadCorners {
        // (x,y) → (1-x, 1-y) for each point
        // Corner labels shift: topLeft ← bottomRight, topRight ← bottomLeft, etc.
        val t = { p: Offset -> Offset(1f - p.x, 1f - p.y) }
        return QuadCorners(
            topLeft = t(bottomRight),
            topRight = t(bottomLeft),
            bottomRight = t(topLeft),
            bottomLeft = t(topRight),
        )
    }

    companion object {
        /** Create from a flat list: [tl_x, tl_y, tr_x, tr_y, br_x, br_y, bl_x, bl_y]. */
        fun fromFlatList(values: List<Float>): QuadCorners {
            require(values.size == 8) { "Expected 8 values, got ${values.size}" }
            return QuadCorners(
                topLeft = Offset(values[0], values[1]),
                topRight = Offset(values[2], values[3]),
                bottomRight = Offset(values[4], values[5]),
                bottomLeft = Offset(values[6], values[7]),
            )
        }
    }
}

