package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests for crop rectangle rotation transforms in BackImagePickerDialog. */
class BackImageCropRotationTest {

    @Test
    fun `rotate90CW with top-left quarter stays in valid bounds`() {
        // Top-left quarter: (0, 0) to (0.5, 0.5)
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate90CW()
        // 90° CW: (x,y) → (1-y, x). Corners:
        //   (0, 0) → (1, 0), (0.5, 0) → (1, 0.5), (0.5, 0.5) → (0.5, 0.5), (0, 0.5) → (0.5, 0)
        // Bounding box: left=0.5, top=0, right=1, bottom=0.5
        assertEquals(0.5f, rotated.left, 0.001f)
        assertEquals(0f, rotated.top, 0.001f)
        assertEquals(1f, rotated.right, 0.001f)
        assertEquals(0.5f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate90CCW with top-left quarter stays in valid bounds`() {
        // Top-left quarter: (0, 0) to (0.5, 0.5)
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate90CCW()
        // 90° CCW: (x,y) → (y, 1-x). Corners:
        //   (0, 0) → (0, 1), (0.5, 0) → (0, 0.5), (0.5, 0.5) → (0.5, 0.5), (0, 0.5) → (0.5, 1)
        // Bounding box: left=0, top=0.5, right=0.5, bottom=1
        assertEquals(0f, rotated.left, 0.001f)
        assertEquals(0.5f, rotated.top, 0.001f)
        assertEquals(0.5f, rotated.right, 0.001f)
        assertEquals(1f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate180 with top-left quarter goes to bottom-right`() {
        // Top-left quarter: (0, 0) to (0.5, 0.5)
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate180()
        // 180°: (x,y) → (1-x, 1-y). The bounding box flips:
        assertEquals(0.5f, rotated.left, 0.001f)
        assertEquals(0.5f, rotated.top, 0.001f)
        assertEquals(1f, rotated.right, 0.001f)
        assertEquals(1f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate90CW then rotate90CCW returns to original`() {
        val rect = Rect(left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.7f)
        val cw = rect.rotate90CW()
        val back = cw.rotate90CCW()
        assertEquals(rect.left, back.left, 0.001f)
        assertEquals(rect.top, back.top, 0.001f)
        assertEquals(rect.right, back.right, 0.001f)
        assertEquals(rect.bottom, back.bottom, 0.001f)
    }

    @Test
    fun `rotate90CCW then rotate90CW returns to original`() {
        val rect = Rect(left = 0.15f, top = 0.25f, right = 0.65f, bottom = 0.75f)
        val ccw = rect.rotate90CCW()
        val back = ccw.rotate90CW()
        assertEquals(rect.left, back.left, 0.001f)
        assertEquals(rect.top, back.top, 0.001f)
        assertEquals(rect.right, back.right, 0.001f)
        assertEquals(rect.bottom, back.bottom, 0.001f)
    }

    @Test
    fun `two rotate90CW equals one rotate180`() {
        val rect = Rect(left = 0.2f, top = 0.3f, right = 0.8f, bottom = 0.9f)
        val doubleCW = rect.rotate90CW().rotate90CW()
        val one80 = rect.rotate180()
        assertEquals(one80.left, doubleCW.left, 0.001f)
        assertEquals(one80.top, doubleCW.top, 0.001f)
        assertEquals(one80.right, doubleCW.right, 0.001f)
        assertEquals(one80.bottom, doubleCW.bottom, 0.001f)
    }

    @Test
    fun `four rotate90CW returns to original`() {
        val rect = Rect(left = 0.1f, top = 0.15f, right = 0.4f, bottom = 0.55f)
        val result = rect.rotate90CW().rotate90CW().rotate90CW().rotate90CW()
        assertEquals(rect.left, result.left, 0.001f)
        assertEquals(rect.top, result.top, 0.001f)
        assertEquals(rect.right, result.right, 0.001f)
        assertEquals(rect.bottom, result.bottom, 0.001f)
    }

    @Test
    fun `rotate180 is its own inverse`() {
        val rect = Rect(left = 0.2f, top = 0.3f, right = 0.7f, bottom = 0.8f)
        val result = rect.rotate180().rotate180()
        assertEquals(rect.left, result.left, 0.001f)
        assertEquals(rect.top, result.top, 0.001f)
        assertEquals(rect.right, result.right, 0.001f)
        assertEquals(rect.bottom, result.bottom, 0.001f)
    }

    @Test
    fun `full image rect stays full after any rotation`() {
        val full = Rect(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val cw = full.rotate90CW()
        assertEquals(0f, cw.left, 0.001f)
        assertEquals(0f, cw.top, 0.001f)
        assertEquals(1f, cw.right, 0.001f)
        assertEquals(1f, cw.bottom, 0.001f)

        val ccw = full.rotate90CCW()
        assertEquals(0f, ccw.left, 0.001f)
        assertEquals(0f, ccw.top, 0.001f)
        assertEquals(1f, ccw.right, 0.001f)
        assertEquals(1f, ccw.bottom, 0.001f)

        val r180 = full.rotate180()
        assertEquals(0f, r180.left, 0.001f)
        assertEquals(0f, r180.top, 0.001f)
        assertEquals(1f, r180.right, 0.001f)
        assertEquals(1f, r180.bottom, 0.001f)
    }
}
