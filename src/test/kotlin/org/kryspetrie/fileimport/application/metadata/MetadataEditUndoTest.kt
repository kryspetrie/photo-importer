package org.kryspetrie.fileimport.application.metadata

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.MetadataEditEntry
import org.kryspetrie.fileimport.domain.model.MetadataEditJournal
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter

class MetadataEditJournalRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private val fileSystem = FileSystemAdapter()

    @Test
    fun `saveJournal and getJournal round-trip`() {
        val repository = MetadataEditJournalRepository(fileSystem)
        val entry =
            MetadataEditEntry(
                filePath = "/test/photo.jpg",
                backupPath = "/test/backup/photo.jpg",
                configSnapshot = PhotoScanConfiguration(description = "test"),
            )
        val journal =
            MetadataEditJournal(
                sourceFolderPath = "/test/photos",
                outputMode = "OVERWRITE",
                entries = listOf(entry),
            )

        val savedPath = repository.saveJournal(journal)
        assertNotNull(savedPath)

        val loaded = repository.getJournal(savedPath)
        assertNotNull(loaded)
        assertEquals(journal.id, loaded.id)
        assertEquals(journal.sourceFolderPath, loaded.sourceFolderPath)
        assertEquals(journal.outputMode, loaded.outputMode)
        assertEquals(1, loaded.entries.size)
        assertEquals("/test/photo.jpg", loaded.entries[0].filePath)
        assertEquals("/test/backup/photo.jpg", loaded.entries[0].backupPath)
        assertFalse(loaded.undone)
    }

    @Test
    fun `markUndone updates journal`() {
        val repository = MetadataEditJournalRepository(fileSystem)
        val journal =
            MetadataEditJournal(
                sourceFolderPath = "/test/photos",
                outputMode = "OVERWRITE",
            )
        val savedPath = repository.saveJournal(journal)

        repository.markUndone(savedPath, journal.copy(undone = true))

        val loaded = repository.getJournal(savedPath)
        assertNotNull(loaded)
        assertTrue(loaded!!.undone)
    }

    @Test
    fun `getJournal returns null for non-existent path`() {
        val repository = MetadataEditJournalRepository(fileSystem)
        val result = repository.getJournal("/nonexistent/path.json")
        assertNull(result)
    }
}

class MetadataEditUndoServiceTest {

    @TempDir
    lateinit var tempDir: File

    private val fileSystem = FileSystemAdapter()
    private val journalRepository = MetadataEditJournalRepository(fileSystem)

    private val backupDir =
        File(System.getProperty("user.home") + "/.petrie-importer/metadata-backups")

    @Test
    fun `createBackup creates a copy of the file`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())
        val original = File(tempDir, "photo.jpg")
        original.writeText("original content")

        val backupPath = undoService.createBackup(original.absolutePath)

        assertNotNull(backupPath)
        val backup = File(backupPath!!)
        assertTrue(backup.exists())
        assertEquals(original.readText(), backup.readText())

        // Cleanup
        backup.delete()
    }

    @Test
    fun `createBackup returns null for non-existent file`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())
        val result = undoService.createBackup("/nonexistent/file.jpg")
        assertNull(result)
    }

    @Test
    fun `undo restores OVERWRITE files from backups`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())

        // Setup: create original and backup files
        val original = File(tempDir, "photo.jpg")
        original.writeText("original content")
        val backup = File(tempDir, "backup_photo.jpg")
        backup.writeText("backup content")

        val entry =
            MetadataEditEntry(
                filePath = original.absolutePath,
                backupPath = backup.absolutePath,
                wasSavedNew = false,
                wasSuccessful = true,
            )
        val journal =
            MetadataEditJournal(
                sourceFolderPath = tempDir.absolutePath,
                outputMode = "OVERWRITE",
                entries = listOf(entry),
            )
        val journalPath = journalRepository.saveJournal(journal)

        // Modify original to simulate a metadata write
        original.writeText("modified content")
        assertEquals("modified content", original.readText())

        // Act: undo
        val restoredCount = undoService.undo(journalPath!!)

        // Assert: original restored from backup
        assertEquals(1, restoredCount)
        assertEquals("backup content", original.readText())

        // Verify journal marked as undone
        val updatedJournal = journalRepository.getJournal(journalPath)
        assertTrue(updatedJournal!!.undone)
    }

    @Test
    fun `undo deletes SAVE_NEW output files`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())

        val original = File(tempDir, "photo.jpg")
        original.writeText("original content")
        val backup = File(tempDir, "backup_photo.jpg")
        backup.writeText("backup content")
        val outputDir = File(tempDir, "output")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "photo.jpg")
        outputFile.writeText("new output content")

        val entry =
            MetadataEditEntry(
                filePath = original.absolutePath,
                backupPath = backup.absolutePath,
                wasSavedNew = true,
                outputFilePath = outputFile.absolutePath,
                wasSuccessful = true,
            )
        val journal =
            MetadataEditJournal(
                sourceFolderPath = tempDir.absolutePath,
                outputMode = "SAVE_NEW",
                entries = listOf(entry),
            )
        val journalPath = journalRepository.saveJournal(journal)

        // Act: undo
        val restoredCount = undoService.undo(journalPath!!)

        // Assert: output file deleted, original untouched
        assertEquals(1, restoredCount)
        assertFalse(outputFile.exists())
        assertEquals("original content", original.readText())
    }

    @Test
    fun `undo returns -1 for already-undone journal`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())

        val entry =
            MetadataEditEntry(
                filePath = "/test/photo.jpg",
                backupPath = "/test/backup/photo.jpg",
                wasSuccessful = true,
            )
        val journal =
            MetadataEditJournal(
                sourceFolderPath = "/test",
                outputMode = "OVERWRITE",
                entries = listOf(entry),
                undone = true,
            )
        val journalPath = journalRepository.saveJournal(journal)

        val result = undoService.undo(journalPath!!)
        assertEquals(-1, result)
    }

    @Test
    fun `undo returns -1 for non-existent journal`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())
        val result = undoService.undo("/nonexistent/path.json")
        assertEquals(-1, result)
    }

    @Test
    fun `cleanupOldBackups removes old files`() = runBlocking {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())

        backupDir.mkdirs()

        // Create an old backup file (8 days old)
        val oldFile = File(backupDir, "1000_old_photo.jpg")
        oldFile.writeText("old content")
        oldFile.setLastModified(System.currentTimeMillis() - 8 * 24 * 60 * 60 * 1000L)

        // Create a recent backup file
        val recentFile =
            File(backupDir, "${System.currentTimeMillis()}_recent_photo.jpg")
        recentFile.writeText("recent content")

        undoService.cleanupOldBackups(maxAgeMs = 7 * 24 * 60 * 60 * 1000L)

        assertFalse(oldFile.exists())
        assertTrue(recentFile.exists())

        // Cleanup
        recentFile.delete()
    }

    @Test
    fun `saveJournalPath creates valid journal`() {
        val undoService =
            MetadataEditUndoService(journalRepository, fileSystem, AwtTestImageProcessing())

        val entry =
            MetadataEditEntry(
                filePath = "/test/photo.jpg",
                backupPath = "/test/backup/photo.jpg",
                configSnapshot = PhotoScanConfiguration(description = "test undo"),
                wasSuccessful = true,
            )

        val journalPath =
            undoService.saveJournalPath(
                sourceFolderPath = "/test/photos",
                outputMode = "OVERWRITE",
                entries = listOf(entry),
            )

        assertNotNull(journalPath)

        // Read back the journal and verify
        val loaded = journalRepository.getJournal(journalPath!!)
        assertNotNull(loaded)
        assertEquals("/test/photos", loaded!!.sourceFolderPath)
        assertEquals("OVERWRITE", loaded.outputMode)
        assertEquals(1, loaded.entries.size)
        assertEquals("/test/photo.jpg", loaded.entries[0].filePath)
    }
}

/** Minimal ImageProcessingPort implementation for tests. */
private class AwtTestImageProcessing :
    org.kryspetrie.fileimport.domain.port.ImageProcessingPort {
    override fun readImage(path: FilePath) = null
    override fun writeJpegImage(
        image: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        path: FilePath,
        quality: Float,
    ) {}
    override fun cropAxisAligned(
        sourceImage: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        photo: org.kryspetrie.fileimport.domain.model.DetectedPhoto,
    ) = sourceImage
    override fun rotateImage(
        image: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        rotation: org.kryspetrie.fileimport.domain.model.RotationAngle,
    ) = image
    override fun compositeBackImage(
        frontImage: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        config: org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration,
    ) = frontImage
    override fun prepareBackImage(
        config: org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration,
        maxWidth: Int?,
        maxHeight: Int?,
    ): org.kryspetrie.fileimport.domain.model.ProcessedImage? = null
}