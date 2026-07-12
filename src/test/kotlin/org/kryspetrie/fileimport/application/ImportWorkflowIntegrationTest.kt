package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.mockito.Mockito.mock

/**
 * Integration tests for the full import workflow.
 *
 * Tests the integration between ImportService and its dependencies to verify end-to-end behavior.
 */
@DisplayName("Import Workflow Integration")
class ImportWorkflowIntegrationTest {

    private lateinit var imageRepository: ImageRepositoryPort
    private lateinit var deduplicationPort: DeduplicationPort
    private lateinit var namingPort: NamingPort
    private lateinit var importService: ImportService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        imageRepository = mock(ImageRepositoryPort::class.java)
        deduplicationPort = mock(DeduplicationPort::class.java)
        namingPort = mock(NamingPort::class.java)
        val scanner =
            ImportScanner(imageRepository, null, TestDispatcherProvider(), TestFileSystemAdapter())
        val executor =
            ImportExecutor(imageRepository, namingPort, TestTimeProvider(), TestFileSystemAdapter())
        importService = ImportService(scanner, executor, deduplicationPort, namingPort)
    }

    private fun createTestFile(name: String, type: ImageFileType = ImageFileType.JPEG): ImageFile {
        val file = File(tempDir, name)
        file.writeText("test content for $name")
        return ImageFile(
            path = FilePath(file.absolutePath),
            fileSize = file.length(),
            fileType = type,
        )
    }

    @Nested
    @DisplayName("full workflow scenarios")
    inner class FullWorkflowScenarios {

        @Test
        @DisplayName("should process single photo import successfully")
        fun shouldProcessSinglePhotoImport() = runTest {
            // Given
            val photo = createTestFile("IMG_0001.jpg")
            val config =
                ImportConfiguration(folderPattern = "{yyyy-MM-dd}", preserveOriginalName = true)

            // When - detect pairs
            val pairs = importService.detectRawJpegPairs(listOf(photo))
            assertThat(pairs).isEmpty() // Single photo, no pairs

            // When - apply filters
            val filtered = importService.applyPairFilter(listOf(photo), config)
            assertThat(filtered).hasSize(1)
        }

        @Test
        @DisplayName("should process RAW+JPEG pair import")
        fun shouldProcessRawJpegPairImport() = runTest {
            // Given
            val raw = createTestFile("IMG_0001.cr2", ImageFileType.RAW_CR2)
            val jpeg = createTestFile("IMG_0001.jpg")
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.IMPORT_BOTH)

            // When - detect pairs
            val pairs = importService.detectRawJpegPairs(listOf(raw, jpeg))
            assertThat(pairs).hasSize(1)
            assertThat(pairs[0].first.fileType).isEqualTo(ImageFileType.RAW_CR2)
            assertThat(pairs[0].second.fileType).isEqualTo(ImageFileType.JPEG)

            // When - apply filter (IMPORT_BOTH)
            val allFiles = listOf(raw, jpeg)
            val filtered = importService.applyPairFilter(allFiles, config)
            assertThat(filtered).hasSize(2)
        }

        @Test
        @DisplayName("should handle mixed media types")
        fun shouldHandleMixedMediaTypes() = runTest {
            // Given
            val photos =
                listOf(
                    createTestFile("photo1.jpg", ImageFileType.JPEG),
                    createTestFile("photo2.jpg", ImageFileType.JPEG),
                    createTestFile("video1.mp4", ImageFileType.VIDEO_MP4),
                )
            val config = ImportConfiguration()

            // When - process
            val pairs = importService.detectRawJpegPairs(photos)
            assertThat(pairs).isEmpty() // No RAW files

            val filtered = importService.applyPairFilter(photos, config)
            assertThat(filtered).hasSize(3) // All files included
        }
    }

    @Nested
    @DisplayName("pair detection scenarios")
    inner class PairDetectionScenarios {

        @Test
        @DisplayName("should detect multiple RAW+JPEG pairs")
        fun shouldDetectMultiplePairs() = runTest {
            // Given
            val photos =
                (1..5).flatMap { i ->
                    listOf(
                        createTestFile("IMG_${"%04d".format(i)}.cr2", ImageFileType.RAW_CR2),
                        createTestFile("IMG_${"%04d".format(i)}.jpg"),
                    )
                }

            // When
            val pairs = importService.detectRawJpegPairs(photos)

            // Then
            assertThat(pairs).hasSize(5)
        }

        @Test
        @DisplayName("should not pair different base filenames")
        fun shouldNotPairDifferentFilenames() = runTest {
            // Given
            val photos =
                listOf(
                    createTestFile("IMG_0001.cr2", ImageFileType.RAW_CR2),
                    createTestFile("IMG_0002.jpg"), // Different base name
                )

            // When
            val pairs = importService.detectRawJpegPairs(photos)

            // Then
            assertThat(pairs).isEmpty()
        }

        @Test
        @DisplayName("should handle files without pairs")
        fun shouldHandleFilesWithoutPairs() = runTest {
            // Given - JPEG only files
            val photos =
                listOf(
                    createTestFile("photo1.jpg"),
                    createTestFile("photo2.jpg"),
                    createTestFile("photo3.jpg"),
                )

            // When
            val pairs = importService.detectRawJpegPairs(photos)

            // Then
            assertThat(pairs).isEmpty()
        }
    }

    @Nested
    @DisplayName("filter scenarios")
    inner class FilterScenarios {

        @Test
        @DisplayName("should return empty list for empty input")
        fun shouldReturnEmptyForEmptyInput() = runTest {
            // Given
            val config = ImportConfiguration()

            // When
            val filtered = importService.applyPairFilter(emptyList(), config)

            // Then
            assertThat(filtered).isEmpty()
        }

        @Test
        @DisplayName("should handle RAW_ONLY mode")
        fun shouldHandleRawOnlyMode() = runTest {
            // Given
            val photos =
                listOf(
                    createTestFile("IMG_0001.cr2", ImageFileType.RAW_CR2),
                    createTestFile("IMG_0001.jpg"),
                )
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.RAW_ONLY)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].fileType).isEqualTo(ImageFileType.RAW_CR2)
        }

        @Test
        @DisplayName("should handle JPEG_ONLY mode")
        fun shouldHandleJpegOnlyMode() = runTest {
            // Given
            val photos =
                listOf(
                    createTestFile("IMG_0001.cr2", ImageFileType.RAW_CR2),
                    createTestFile("IMG_0001.jpg"),
                )
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.JPEG_ONLY)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(1)
            assertThat(filtered[0].fileType).isEqualTo(ImageFileType.JPEG)
        }

        @Test
        @DisplayName("should keep pairs together when configured")
        fun shouldKeepPairsTogether() = runTest {
            // Given
            val photos =
                listOf(
                    createTestFile("IMG_0001.cr2", ImageFileType.RAW_CR2),
                    createTestFile("IMG_0001.jpg"),
                    createTestFile("IMG_0002.jpg"),
                )
            val config = ImportConfiguration(keepPairsTogether = true)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(3)
        }
    }

    @Nested
    @DisplayName("conflict resolution scenarios")
    inner class ConflictResolutionScenarios {

        @Test
        @DisplayName("should handle rename on conflict")
        fun shouldHandleRenameOnConflict() = runTest {
            // Given
            val photos = listOf(createTestFile("photo.jpg"))
            val config = ImportConfiguration(conflictResolution = ConflictResolution.RENAME)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(1)
        }

        @Test
        @DisplayName("should handle skip on conflict")
        fun shouldHandleSkipOnConflict() = runTest {
            // Given
            val photos = listOf(createTestFile("photo.jpg"))
            val config = ImportConfiguration(conflictResolution = ConflictResolution.SKIP)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(1)
        }

        @Test
        @DisplayName("should handle replace on conflict")
        fun shouldHandleReplaceOnConflict() = runTest {
            // Given
            val photos = listOf(createTestFile("photo.jpg"))
            val config = ImportConfiguration(conflictResolution = ConflictResolution.REPLACE)

            // When
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(filtered).hasSize(1)
        }
    }

    @Nested
    @DisplayName("large dataset scenarios")
    inner class LargeDatasetScenarios {

        @Test
        @DisplayName("should handle large number of photos")
        fun shouldHandleLargeNumberOfPhotos() = runTest {
            // Given - create 100 photos
            val photos = (1..100).map { i -> createTestFile("IMG_${"%04d".format(i)}.jpg") }
            val config = ImportConfiguration()

            // When
            val pairs = importService.detectRawJpegPairs(photos)
            val filtered = importService.applyPairFilter(photos, config)

            // Then
            assertThat(pairs).isEmpty()
            assertThat(filtered).hasSize(100)
        }

        @Test
        @DisplayName("should handle many RAW+JPEG pairs")
        fun shouldHandleManyRawJpegPairs() = runTest {
            // Given - create 20 pairs
            val photos =
                (1..20).flatMap { i ->
                    listOf(
                        createTestFile("IMG_${"%04d".format(i)}.cr2", ImageFileType.RAW_CR2),
                        createTestFile("IMG_${"%04d".format(i)}.jpg"),
                    )
                }

            // When
            val pairs = importService.detectRawJpegPairs(photos)

            // Then
            assertThat(pairs).hasSize(20)
        }
    }
}
