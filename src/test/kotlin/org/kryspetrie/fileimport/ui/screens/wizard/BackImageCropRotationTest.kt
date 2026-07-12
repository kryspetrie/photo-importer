package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests for crop rectangle and quad rotation transforms in BackImagePickerDialog. */
class BackImageCropRotationTest {

    // ─── Rect rotation tests ──────────────────────────────────────────────────

    @Test
    fun `rotate90CW with top-left quarter stays in valid bounds`() {
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate90CW()
        assertEquals(0.5f, rotated.left, 0.001f)
        assertEquals(0f, rotated.top, 0.001f)
        assertEquals(1f, rotated.right, 0.001f)
        assertEquals(0.5f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate90CCW with top-left quarter stays in valid bounds`() {
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate90CCW()
        assertEquals(0f, rotated.left, 0.001f)
        assertEquals(0.5f, rotated.top, 0.001f)
        assertEquals(0.5f, rotated.right, 0.001f)
        assertEquals(1f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate180 with top-left quarter goes to bottom-right`() {
        val rect = Rect(left = 0f, top = 0f, right = 0.5f, bottom = 0.5f)
        val rotated = rect.rotate180()
        assertEquals(0.5f, rotated.left, 0.001f)
        assertEquals(0.5f, rotated.top, 0.001f)
        assertEquals(1f, rotated.right, 0.001f)
        assertEquals(1f, rotated.bottom, 0.001f)
    }

    @Test
    fun `rotate90CW then rotate90CCW returns to original`() {
        val rect = Rect(left = 0.1f, top = 0.2f, right = 0.6f, bottom = 0.7f)
        val back = rect.rotate90CW().rotate90CCW()
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
    fun `full image rect stays full after any rotation`() {
        val full = Rect(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val cw = full.rotate90CW()
        assertEquals(0f, cw.left, 0.001f)
        assertEquals(0f, cw.top, 0.001f)
        assertEquals(1f, cw.right, 0.001f)
        assertEquals(1f, cw.bottom, 0.001f)
    }

    // ─── Quad corner rotation tests ──────────────────────────────────────────

    @Test
    fun `quad rotate90CW maps corners correctly with label reordering`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.1f, 0.1f),
                topRight = Offset(0.3f, 0.1f),
                bottomRight = Offset(0.3f, 0.4f),
                bottomLeft = Offset(0.1f, 0.4f),
            )
        val rotated = quad.rotate90CW()
        // CW rotation: corner labels shift CCW, coords (x,y)→(1-y,x)
        // topLeft ← topRight transformed, topRight ← bottomRight transformed, etc.
        assertEquals(Offset(0.9f, 0.3f), rotated.topLeft)
        assertEquals(Offset(0.6f, 0.3f), rotated.topRight)
        assertEquals(Offset(0.6f, 0.1f), rotated.bottomRight)
        assertEquals(Offset(0.9f, 0.1f), rotated.bottomLeft)
    }

    @Test
    fun `quad rotate90CCW maps corners correctly with label reordering`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.1f, 0.1f),
                topRight = Offset(0.3f, 0.1f),
                bottomRight = Offset(0.3f, 0.4f),
                bottomLeft = Offset(0.1f, 0.4f),
            )
        val rotated = quad.rotate90CCW()
        // CCW: coords (x,y)→(y,1-x), topLeft ← bottomLeft transformed, etc.
        assertEquals(Offset(0.4f, 0.9f), rotated.topLeft)
        assertEquals(Offset(0.1f, 0.9f), rotated.topRight)
        assertEquals(Offset(0.1f, 0.7f), rotated.bottomRight)
        assertEquals(Offset(0.4f, 0.7f), rotated.bottomLeft)
    }

    @Test
    fun `quad rotate180 maps corners correctly`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.1f, 0.1f),
                topRight = Offset(0.3f, 0.1f),
                bottomRight = Offset(0.3f, 0.4f),
                bottomLeft = Offset(0.1f, 0.4f),
            )
        val rotated = quad.rotate180()
        // 180: coords (x,y)→(1-x,1-y), topLeft ← bottomRight transformed, etc.
        assertEquals(Offset(0.7f, 0.6f), rotated.topLeft)
        assertEquals(Offset(0.9f, 0.6f), rotated.topRight)
        assertEquals(Offset(0.9f, 0.9f), rotated.bottomRight)
        assertEquals(Offset(0.7f, 0.9f), rotated.bottomLeft)
    }

    private fun assertOffsetEquals(expected: Offset, actual: Offset, tolerance: Float = 0.001f) {
        assertEquals(expected.x, actual.x, tolerance, "x mismatch")
        assertEquals(expected.y, actual.y, tolerance, "y mismatch")
    }

    @Test
    fun `quad CW then CCW returns to original`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.2f, 0.1f),
                topRight = Offset(0.5f, 0.15f),
                bottomRight = Offset(0.6f, 0.7f),
                bottomLeft = Offset(0.15f, 0.65f),
            )
        val back = quad.rotate90CW().rotate90CCW()
        assertOffsetEquals(quad.topLeft, back.topLeft)
        assertOffsetEquals(quad.topRight, back.topRight)
        assertOffsetEquals(quad.bottomRight, back.bottomRight)
        assertOffsetEquals(quad.bottomLeft, back.bottomLeft)
    }

    @Test
    fun `quad four CW rotations returns to original`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.2f, 0.1f),
                topRight = Offset(0.5f, 0.15f),
                bottomRight = Offset(0.6f, 0.7f),
                bottomLeft = Offset(0.15f, 0.65f),
            )
        val result = quad.rotate90CW().rotate90CW().rotate90CW().rotate90CW()
        assertOffsetEquals(quad.topLeft, result.topLeft)
        assertOffsetEquals(quad.topRight, result.topRight)
        assertOffsetEquals(quad.bottomRight, result.bottomRight)
        assertOffsetEquals(quad.bottomLeft, result.bottomLeft)
    }

    @Test
    fun `quad two CW rotations equals one 180`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.2f, 0.1f),
                topRight = Offset(0.5f, 0.15f),
                bottomRight = Offset(0.6f, 0.7f),
                bottomLeft = Offset(0.15f, 0.65f),
            )
        val doubleCW = quad.rotate90CW().rotate90CW()
        val one80 = quad.rotate180()
        assertEquals(one80.topLeft, doubleCW.topLeft)
        assertEquals(one80.topRight, doubleCW.topRight)
        assertEquals(one80.bottomRight, doubleCW.bottomRight)
        assertEquals(one80.bottomLeft, doubleCW.bottomLeft)
    }

    @Test
    fun `quad toFlatList and fromFlatList roundtrip`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.1f, 0.2f),
                topRight = Offset(0.3f, 0.4f),
                bottomRight = Offset(0.5f, 0.6f),
                bottomLeft = Offset(0.7f, 0.8f),
            )
        val flat = quad.toFlatList()
        assertEquals(8, flat.size)
        val restored = QuadCorners.fromFlatList(flat)
        assertEquals(quad.topLeft, restored.topLeft)
        assertEquals(quad.topRight, restored.topRight)
        assertEquals(quad.bottomRight, restored.bottomRight)
        assertEquals(quad.bottomLeft, restored.bottomLeft)
    }

    // ─── BackImageCropResult tests ────────────────────────────────────────────

    @Test
    fun `BackImageCropResult with rect produces 4-value list`() {
        val result = BackImageCropResult(rect = Rect(0.1f, 0.2f, 0.3f, 0.4f))
        val flat = result.toNormalizedList()!!
        assertEquals(4, flat.size)
        assertEquals(0.1f, flat[0], 0.001f)
        assertEquals(0.2f, flat[1], 0.001f)
        assertEquals(0.3f, flat[2], 0.001f)
        assertEquals(0.4f, flat[3], 0.001f)
    }

    @Test
    fun `BackImageCropResult with quad produces 8-value list`() {
        val quad =
            QuadCorners(
                topLeft = Offset(0.1f, 0.2f),
                topRight = Offset(0.3f, 0.4f),
                bottomRight = Offset(0.5f, 0.6f),
                bottomLeft = Offset(0.7f, 0.8f),
            )
        val result = BackImageCropResult(quad = quad)
        val flat = result.toNormalizedList()!!
        assertEquals(8, flat.size)
        assertEquals(0.1f, flat[0], 0.001f)
        assertEquals(0.2f, flat[1], 0.001f)
    }

    @Test
    fun `BackImageCropResult with null returns null`() {
        val result = BackImageCropResult()
        assertEquals(null, result.toNormalizedList())
    }

    @Test
    fun `quad takes priority over rect in toNormalizedList`() {
        val result =
            BackImageCropResult(
                rect = Rect(0f, 0f, 1f, 1f),
                quad =
                    QuadCorners(
                        topLeft = Offset(0.1f, 0.2f),
                        topRight = Offset(0.3f, 0.4f),
                        bottomRight = Offset(0.5f, 0.6f),
                        bottomLeft = Offset(0.7f, 0.8f),
                    ),
            )
        val flat = result.toNormalizedList()!!
        assertEquals(8, flat.size) // quad takes priority
    }
}
