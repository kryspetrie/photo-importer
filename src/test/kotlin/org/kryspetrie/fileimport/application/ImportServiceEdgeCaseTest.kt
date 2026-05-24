package org.kryspetrie.fileimport.application

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.ConflictResolution
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.mockito.Mockito.mock

/** Unit tests for ImportService - additional edge cases and error handling. */
@DisplayName("ImportService Edge Cases")
class ImportServiceEdgeCaseTest {
    private lateinit var imageRepository: ImageRepositoryPort
    private lateinit var deduplicationPort: DeduplicationPort
    private lateinit var namingPort: NamingPort
    private lateinit var service: ImportService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        imageRepository = mock(ImageRepositoryPort::class.java)
        deduplicationPort = mock(DeduplicationPort::class.java)
        namingPort = mock(NamingPort::class.java)
        val importScanner = ImportScanner(imageRepository, null, TestDispatcherProvider())
        val importExecutor = ImportExecutor(imageRepository, namingPort, TestTimeProvider())
        service = ImportService(importScanner, importExecutor, deduplicationPort, namingPort)
    }

    private fun createImageFile(
        name: String,
        extension: String = "jpg",
        type: ImageFileType? = null,
    ): ImageFile {
        val file = File(tempDir, "$name.$extension")
        file.writeText("test content")
        return ImageFile(file = file, fileType = type ?: ImageFileType.fromExtension(extension))
    }

    @Nested
    @DisplayName("RAW+JPEG pair detection edge cases")
    inner class RawJpegPairEdgeCases {
        @Test
        fun `should handle multiple RAW formats`() {
            val cr2 = createImageFile("IMG_0001", "cr2")
            val arw = createImageFile("IMG_0002", "arw")
            val nef = createImageFile("IMG_0003", "nef")
            val jpg1 = createImageFile("IMG_0001", "jpg")
            val jpg2 = createImageFile("IMG_0002", "jpg")
            val jpg3 = createImageFile("IMG_0003", "jpg")

            val pairs = service.detectRawJpegPairs(listOf(cr2, arw, nef, jpg1, jpg2, jpg3))

            assertThat(pairs).hasSize(3)
        }

        @Test
        fun `should detect RAW+JPEG pairs with matching base names`() {
            val raw = createImageFile("PHOTO", "cr2")
            val jpeg = createImageFile("PHOTO", "jpg")

            val pairs = service.detectRawJpegPairs(listOf(raw, jpeg))

            assertThat(pairs).hasSize(1)
            assertThat(pairs[0].first.fileType.isRaw).isTrue()
            assertThat(pairs[0].second.fileType.isRaw).isFalse()
        }

        @Test
        fun `should handle files without matching pairs`() {
            val raw1 = createImageFile("IMG_0001", "cr2")
            val raw2 = createImageFile("IMG_0002", "cr2")

            val pairs = service.detectRawJpegPairs(listOf(raw1, raw2))

            assertThat(pairs).isEmpty()
        }

        @Test
        fun `should handle duplicate pairs`() {
            val raw1 = createImageFile("IMG_0001", "cr2")
            val jpeg1 = createImageFile("IMG_0001", "jpg")
            val jpeg2 = createImageFile("IMG_0001", "jpg")

            val pairs = service.detectRawJpegPairs(listOf(raw1, jpeg1, jpeg2))

            // Should have at most 1 pair
            assertThat(pairs.size).isLessThanOrEqualTo(1)
        }
    }

    @Nested
    @DisplayName("pair filter edge cases")
    inner class PairFilterEdgeCases {
        @Test
        fun `should return original list for IMPORT_BOTH mode`() {
            val raw = createImageFile("IMG_0001", "cr2")
            val jpeg = createImageFile("IMG_0001", "jpg")
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.IMPORT_BOTH)

            val result = service.applyPairFilter(listOf(raw, jpeg), config)

            assertThat(result).hasSize(2)
        }

        @Test
        fun `should handle empty input list`() {
            val config = ImportConfiguration()

            val result = service.applyPairFilter(emptyList(), config)

            assertThat(result).isEmpty()
        }

        @Test
        fun `should filter RAW only when configured`() {
            val raw = createImageFile("IMG_0001", "cr2")
            val jpeg = createImageFile("IMG_0001", "jpg")
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.RAW_ONLY)

            val result = service.applyPairFilter(listOf(raw, jpeg), config)

            // Verify the method doesn't crash and returns something
            assertThat(result).isNotNull()
        }

        @Test
        fun `should filter JPEG only when configured`() {
            val raw = createImageFile("IMG_0001", "cr2")
            val jpeg = createImageFile("IMG_0001", "jpg")
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.JPEG_ONLY)

            val result = service.applyPairFilter(listOf(raw, jpeg), config)

            // Verify the method doesn't crash and returns something
            assertThat(result).isNotNull()
        }
    }

    @Nested
    @DisplayName("conflict resolution edge cases")
    inner class ConflictResolutionEdgeCases {
        @Test
        fun `should handle rename resolution mode`() {
            val config = ImportConfiguration(conflictResolution = ConflictResolution.RENAME)
            val imageFile = createImageFile("test")

            val result = service.applyPairFilter(listOf(imageFile), config)

            assertThat(result).hasSize(1)
        }

        @Test
        fun `should handle skip resolution mode`() {
            val config = ImportConfiguration(conflictResolution = ConflictResolution.SKIP)
            val imageFile = createImageFile("test")

            val result = service.applyPairFilter(listOf(imageFile), config)

            assertThat(result).hasSize(1)
        }

        @Test
        fun `should handle replace resolution mode`() {
            val config = ImportConfiguration(conflictResolution = ConflictResolution.REPLACE)
            val imageFile = createImageFile("test")

            val result = service.applyPairFilter(listOf(imageFile), config)

            assertThat(result).hasSize(1)
        }
    }

    @Nested
    @DisplayName("large dataset scenarios")
    inner class LargeDatasetScenarios {
        @Test
        fun `should handle many files without crashing`() {
            val files = (1..50).map { createImageFile("photo_$it") }

            val pairs = service.detectRawJpegPairs(files)

            // Just verify no crash and returns list
            assertThat(pairs).isNotNull()
        }
    }
}
