package org.kryspetrie.fileimport.application

import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ReorganizeMapping
import org.kryspetrie.fileimport.domain.model.ReorganizeMode
import org.kryspetrie.fileimport.domain.model.ReorganizePreview
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.ThumbnailExtractorPort
import org.kryspetrie.fileimport.infrastructure.thumbnails.FolderThumbnailCacheAdapter
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@DisplayName("ReorganizeService")
class ReorganizeServiceTest {
    private lateinit var imageRepository: ImageRepositoryPort
    private lateinit var namingPort: NamingPort
    private lateinit var journalRepository: ReorganizeJournalRepository
    private lateinit var fileOperationExecutor: FileOperationExecutor
    private lateinit var folderThumbnailCache: FolderThumbnailCacheAdapter
    private lateinit var service: ReorganizeService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        imageRepository = mock(ImageRepositoryPort::class.java)
        namingPort = mock(NamingPort::class.java)
        val dispatcherProvider = TestDispatcherProvider()
        val fileSystem = TestFileSystemAdapter()
        journalRepository = ReorganizeJournalRepository(fileSystem)
        fileOperationExecutor = FileOperationExecutor(dispatcherProvider, fileSystem)
        folderThumbnailCache =
            FolderThumbnailCacheAdapter(
                fileSystem = fileSystem,
                thumbnailExtractor = mock(ThumbnailExtractorPort::class.java),
                dispatcherProvider = dispatcherProvider,
            )
        service =
            ReorganizeService(
                imageRepository,
                namingPort,
                TestTimeProvider(),
                dispatcherProvider,
                journalRepository,
                fileOperationExecutor,
                fileSystem,
                folderThumbnailCache,
            )
    }

    @Nested
    @DisplayName("scanAndPreview")
    inner class ScanAndPreview {
        @Test
        @DisplayName("should throw for non-existent folder")
        fun shouldThrowForMissingFolder() = runTest {
            val exception = runCatching {
                service.scanAndPreview("/nonexistent", ImportConfiguration())
            }
            assertThat(exception.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("should return empty preview for empty folder")
        fun shouldReturnEmptyForEmptyFolder() = runTest {
            whenever(imageRepository.scanDirectory(any(), eq(true))).thenReturn(emptyList())

            val preview = service.scanAndPreview(tempDir.absolutePath, ImportConfiguration())

            assertThat(preview.totalFiles).isEqualTo(0)
            assertThat(preview.mappings).isEmpty()
        }

        @Test
        @DisplayName("should generate correct mappings with metadata")
        fun shouldGenerateMappingsWithMetadata() = runTest {
            val file = File(tempDir, "IMG_001.jpg")
            file.writeText("photo data")
            val imageFile = ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
            val metadata = ImageMetadata(dateTimeOriginal = LocalDateTime.of(2024, 6, 15, 10, 30))
            @Suppress("UnusedPrivateProperty")
            val enrichedFile = imageFile.copy(metadata = metadata)

            whenever(imageRepository.scanDirectory(any(), eq(true))).thenReturn(listOf(imageFile))
            whenever(imageRepository.getMetadata(any())).thenReturn(metadata)
            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn("${tempDir.absolutePath}/2024-06-15")
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("IMG_001.jpg")

            val preview = service.scanAndPreview(tempDir.absolutePath, ImportConfiguration())

            assertThat(preview.totalFiles).isEqualTo(1)
            assertThat(preview.mappings).hasSize(1)
            assertThat(preview.mappings[0].newPath).contains("2024-06-15/IMG_001.jpg")
        }

        @Test
        @DisplayName("renameOnly keeps files in current directory")
        fun renameOnlyKeepsCurrentDirectory() = runTest {
            val nestedDir = File(tempDir, "nested")
            nestedDir.mkdirs()
            val file = File(nestedDir, "IMG_001.jpg")
            file.writeText("photo data")
            val imageFile = ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
            val metadata = ImageMetadata(dateTimeOriginal = LocalDateTime.of(2024, 6, 15, 10, 30))

            whenever(imageRepository.scanDirectory(any(), eq(true))).thenReturn(listOf(imageFile))
            whenever(imageRepository.getMetadata(any())).thenReturn(metadata)
            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn("${tempDir.absolutePath}/2024-06-15")
            whenever(namingPort.generateFileName(any(), any(), any()))
                .thenReturn("2024-06-15_IMG_001.jpg")

            val preview =
                service.scanAndPreview(
                    tempDir.absolutePath,
                    ImportConfiguration(createSubfolders = true),
                    renameOnly = true,
                )

            assertThat(preview.mappings).hasSize(1)
            assertThat(preview.mappings[0].newPath)
                .startsWith("${nestedDir.absolutePath}/2024-06-15_IMG_001.jpg")
        }
    }

    @Nested
    @DisplayName("execute")
    inner class Execute {
        @Test
        @DisplayName("should move files according to preview")
        fun shouldMoveFiles() = runTest {
            val sourceFile = File(tempDir, "original/IMG_001.jpg")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("photo data")
            val imageFile =
                ImageFile(path = FilePath(sourceFile.absolutePath), fileSize = sourceFile.length())

            val destPath = "${tempDir.absolutePath}/2024/IMG_001.jpg"
            val mapping =
                ReorganizeMapping(
                    file = imageFile,
                    currentPath = sourceFile.absolutePath,
                    newPath = destPath,
                    newFileName = "IMG_001.jpg",
                    isChanged = true,
                )
            val preview =
                ReorganizePreview(
                    mappings = listOf(mapping),
                    totalFiles = 1,
                    changedFiles = 1,
                    conflictCount = 0,
                    newFolderCount = 1,
                )

            val result = service.execute(preview)

            assertThat(result.movedCount).isEqualTo(1)
            assertThat(File(destPath).exists()).isTrue()
            assertThat(sourceFile.exists()).isFalse()
            assertThat(result.journalPath).isNotNull()
        }

        @Test
        @DisplayName("should remove stale thumbnails after move")
        fun shouldRemoveStaleThumbnailsAfterMove() = runTest {
            val sourceFile = File(tempDir, "original/IMG_001.jpg")
            sourceFile.parentFile.mkdirs()
            val image =
                java.awt.image.BufferedImage(120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB)
            javax.imageio.ImageIO.write(image, "jpg", sourceFile)
            val imageFile =
                ImageFile(path = FilePath(sourceFile.absolutePath), fileSize = sourceFile.length())

            val destPath = "${tempDir.absolutePath}/2024/IMG_001.jpg"
            folderThumbnailCache.getThumbnail(
                FilePath(sourceFile.absolutePath),
                tempDir.absolutePath,
                maxPx = 80,
            )
            assertThat(File(tempDir, ".thumbs/original/IMG_001.jpg")).exists()

            val mapping =
                ReorganizeMapping(
                    file = imageFile,
                    currentPath = sourceFile.absolutePath,
                    newPath = destPath,
                    newFileName = "IMG_001.jpg",
                    isChanged = true,
                )
            val preview =
                ReorganizePreview(
                    mappings = listOf(mapping),
                    totalFiles = 1,
                    changedFiles = 1,
                    conflictCount = 0,
                    newFolderCount = 1,
                    libraryRoot = tempDir.absolutePath,
                )

            service.execute(preview)

            assertThat(File(destPath).exists()).isTrue()
            assertThat(File(tempDir, ".thumbs/original/IMG_001.jpg")).doesNotExist()
        }

        @Test
        @DisplayName("should skip when destination already exists")
        fun shouldSkipExistingDest() = runTest {
            val sourceFile = File(tempDir, "src/photo.jpg")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("source")
            val destFile = File(tempDir, "dest/photo.jpg")
            destFile.parentFile.mkdirs()
            destFile.writeText("already exists")

            val imageFile =
                ImageFile(path = FilePath(sourceFile.absolutePath), fileSize = sourceFile.length())
            val mapping =
                ReorganizeMapping(
                    file = imageFile,
                    currentPath = sourceFile.absolutePath,
                    newPath = destFile.absolutePath,
                    newFileName = "photo.jpg",
                    isChanged = true,
                )
            val preview =
                ReorganizePreview(
                    mappings = listOf(mapping),
                    totalFiles = 1,
                    changedFiles = 1,
                    conflictCount = 0,
                    newFolderCount = 0,
                )

            val result = service.execute(preview)

            assertThat(result.skippedCount).isEqualTo(1)
            assertThat(destFile.readText()).isEqualTo("already exists")
        }

        @Test
        @DisplayName("should report no changes for unchanged mappings")
        fun shouldNotMoveUnchanged() = runTest {
            val file = File(tempDir, "photo.jpg")
            file.writeText("data")
            val imageFile = ImageFile(path = FilePath(file.absolutePath), fileSize = file.length())
            val mapping =
                ReorganizeMapping(
                    file = imageFile,
                    currentPath = file.absolutePath,
                    newPath = file.absolutePath,
                    newFileName = "photo.jpg",
                    isChanged = false,
                )
            val preview =
                ReorganizePreview(
                    mappings = listOf(mapping),
                    totalFiles = 1,
                    changedFiles = 0,
                    conflictCount = 0,
                    newFolderCount = 0,
                )

            val result = service.execute(preview)

            assertThat(result.movedCount).isEqualTo(0)
            assertThat(result.renamedCount).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("undo")
    inner class Undo {
        @Test
        @DisplayName("MOVE undo restores file to original path")
        fun moveUndoRestoresOriginalPath() = runTest {
            val sourceFile = File(tempDir, "original/IMG_001.jpg")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("photo data")
            val imageFile =
                ImageFile(path = FilePath(sourceFile.absolutePath), fileSize = sourceFile.length())
            val destPath = "${tempDir.absolutePath}/2024/IMG_001.jpg"
            val preview =
                ReorganizePreview(
                    mappings =
                        listOf(
                            ReorganizeMapping(
                                file = imageFile,
                                currentPath = sourceFile.absolutePath,
                                newPath = destPath,
                                newFileName = "IMG_001.jpg",
                                isChanged = true,
                            )
                        ),
                    totalFiles = 1,
                    changedFiles = 1,
                    conflictCount = 0,
                    newFolderCount = 1,
                    libraryRoot = tempDir.absolutePath,
                )

            val executeResult = service.execute(preview)
            val journalPath = requireNotNull(executeResult.journalPath)
            assertThat(File(destPath).exists()).isTrue()
            assertThat(sourceFile.exists()).isFalse()

            val undoResult = service.undo(journalPath)

            assertThat(undoResult.errorCount).isEqualTo(0)
            assertThat(undoResult.movedCount).isEqualTo(1)
            assertThat(sourceFile.exists()).isTrue()
            assertThat(sourceFile.readText()).isEqualTo("photo data")
            assertThat(File(destPath).exists()).isFalse()
            assertThat(service.getJournal(journalPath)?.undone).isTrue()
        }

        @Test
        @DisplayName("COPY undo deletes copied file and leaves original")
        fun copyUndoDeletesCopy() = runTest {
            val sourceFile = File(tempDir, "original/IMG_001.jpg")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("photo data")
            val imageFile =
                ImageFile(path = FilePath(sourceFile.absolutePath), fileSize = sourceFile.length())
            val destPath = "${tempDir.absolutePath}/2024/IMG_001.jpg"
            val preview =
                ReorganizePreview(
                    mappings =
                        listOf(
                            ReorganizeMapping(
                                file = imageFile,
                                currentPath = sourceFile.absolutePath,
                                newPath = destPath,
                                newFileName = "IMG_001.jpg",
                                isChanged = true,
                                mode = ReorganizeMode.COPY,
                            )
                        ),
                    totalFiles = 1,
                    changedFiles = 1,
                    conflictCount = 0,
                    newFolderCount = 1,
                    operationMode = ReorganizeMode.COPY,
                    libraryRoot = tempDir.absolutePath,
                )

            val executeResult = service.execute(preview)
            val journalPath = requireNotNull(executeResult.journalPath)
            assertThat(File(destPath).exists()).isTrue()
            assertThat(sourceFile.exists()).isTrue()

            val undoResult = service.undo(journalPath)

            assertThat(undoResult.errorCount).isEqualTo(0)
            assertThat(sourceFile.exists()).isTrue()
            assertThat(File(destPath).exists()).isFalse()
            assertThat(service.getJournal(journalPath)?.undone).isTrue()
        }

        @Test
        @DisplayName("undo throws when journal path is missing")
        fun undoThrowsForMissingJournal() = runTest {
            val exception = runCatching {
                service.undo("${tempDir.absolutePath}/missing-journal.json")
            }
            assertThat(exception.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
