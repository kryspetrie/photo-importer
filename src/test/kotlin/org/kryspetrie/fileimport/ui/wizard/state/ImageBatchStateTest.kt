package org.kryspetrie.fileimport.ui.wizard.state

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageBatchStateTest {

    private fun createTempFiles(count: Int): List<File> {
        val files = mutableListOf<File>()
        for (i in 0 until count) {
            val file = File.createTempFile("batch_test_$i", ".jpg")
            file.deleteOnExit()
            files.add(file)
        }
        return files
    }

    @Test
    fun `peekNextBatchFile returns next file without advancing`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        // Current index is 0
        assertEquals(0, state.currentImageIndex.value)
        assertEquals(files[1], state.peekNextBatchFile())
        assertEquals(0, state.currentImageIndex.value) // Should not advance
    }

    @Test
    fun `peekNextBatchFile returns null at end of batch`() {
        val state = ImageBatchState()
        val files = createTempFiles(2)
        state.initializeBatch(files)

        state.advanceToNextBatchFile() // Move to index 1
        assertNull(state.peekNextBatchFile()) // No more files after index 1
    }

    @Test
    fun `peekNextNonSkippedBatchFile skips over skipped files`() {
        val state = ImageBatchState()
        val files = createTempFiles(5)
        state.initializeBatch(files)

        // Mark files at index 1 and 2 as skipped
        state.markBatchIndexSkipped(1)
        state.markBatchIndexSkipped(2)

        // Current index is 0, so next non-skipped should be index 3
        assertEquals(files[3], state.peekNextNonSkippedBatchFile())
    }

    @Test
    fun `peekNextNonSkippedBatchFile returns null when all remaining are skipped`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        // Mark all remaining files as skipped
        state.markBatchIndexSkipped(1)
        state.markBatchIndexSkipped(2)

        assertNull(state.peekNextNonSkippedBatchFile())
    }

    @Test
    fun `peekNextNonSkippedBatchFile returns next file when none are skipped`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        // No skips, so next non-skipped = next file
        assertEquals(files[1], state.peekNextNonSkippedBatchFile())
    }

    @Test
    fun `hasMoreNonSkippedBatchImages filters skipped files`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        // Initially has more non-skipped images
        assertTrue(state.hasMoreNonSkippedBatchImages)

        // Skip remaining files
        state.markBatchIndexSkipped(1)
        state.markBatchIndexSkipped(2)

        assertFalse(state.hasMoreNonSkippedBatchImages)
    }

    @Test
    fun `hasMoreNonSkippedBatchImages is false for single file batch`() {
        val state = ImageBatchState()
        val files = createTempFiles(1)
        state.initializeBatch(files)

        // Single-file "batch" is rejected — not multi-source mode
        assertFalse(state.isBatchMode)
        assertFalse(state.canOfferSkipPhoto)
        assertFalse(state.hasMoreNonSkippedBatchImages)
    }

    @Test
    fun `initializeBatch with fewer than two files does not enter batch mode`() {
        val state = ImageBatchState()
        val pair = createTempFiles(2)
        state.initializeBatch(pair)
        assertTrue(state.isBatchMode)
        assertTrue(state.canOfferSkipPhoto)

        state.initializeBatch(createTempFiles(1))
        assertFalse(state.isBatchMode)
        assertFalse(state.canOfferSkipPhoto)
        assertEquals(0, state.batchTotal)
    }

    @Test
    fun `skipNextBatchFile advances index and marks as skipped`() {
        val state = ImageBatchState()
        val files = createTempFiles(5)
        state.initializeBatch(files)

        // Skip the next file (index 1)
        val result = state.skipNextBatchFile()

        // After skipping, current index advances to the skipped index
        assertEquals(1, state.currentImageIndex.value)
        // The file after the skipped one should be returned
        assertEquals(files[2], result)
        // Index 1 should be in the skipped set
        assertTrue(state.skippedBatchIndices.value.contains(1))
    }

    @Test
    fun `markBatchIndexSkipped adds to skipped set`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        state.markBatchIndexSkipped(1)
        state.markBatchIndexSkipped(2)

        assertEquals(setOf(1, 2), state.skippedBatchIndices.value.toSet())
    }

    @Test
    fun `skippedBatchFiles returns correct file set`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        state.markBatchIndexSkipped(1)

        val skippedFiles = state.skippedBatchFiles
        assertEquals(1, skippedFiles.size)
        assertEquals(files[1], skippedFiles.first())
    }

    @Test
    fun `unmarkBatchIndexSkipped removes from skipped set`() {
        val state = ImageBatchState()
        val files = createTempFiles(3)
        state.initializeBatch(files)

        state.markBatchIndexSkipped(1)
        assertTrue(state.skippedBatchIndices.value.contains(1))

        state.unmarkBatchIndexSkipped(1)
        assertFalse(state.skippedBatchIndices.value.contains(1))
    }
}
