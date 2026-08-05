package org.kryspetrie.fileimport.ui.screens.wizard

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ResolveImportBatchFilesTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun `file-by-filename returns null even when siblings exist`() {
        val a = File(tempDir, "a.jpg").also { it.writeBytes(byteArrayOf(1)) }
        File(tempDir, "b.jpg").writeBytes(byteArrayOf(2))
        File(tempDir, "c.jpg").writeBytes(byteArrayOf(3))

        assertNull(resolveImportBatchFiles(a))
    }

    @Test
    fun `directory with multiple images returns sorted list`() {
        val a = File(tempDir, "a.jpg").also { it.writeBytes(byteArrayOf(1)) }
        val b = File(tempDir, "b.jpg").also { it.writeBytes(byteArrayOf(2)) }

        assertEquals(listOf(a, b), resolveImportBatchFiles(tempDir))
    }

    @Test
    fun `directory with a single image is not a batch`() {
        File(tempDir, "only.jpg").writeBytes(byteArrayOf(1))

        assertNull(resolveImportBatchFiles(tempDir))
    }

    @Test
    fun `null source returns null`() {
        assertNull(resolveImportBatchFiles(null))
    }
}
