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
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@DisplayName("ImportService")
class ImportServiceTest {
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
        type: ImageFileType = ImageFileType.JPEG,
        metadata: ImageMetadata? = null,
    ): ImageFile {
        val file = File(tempDir, "$name.$extension")
        file.writeText("test content for $name")
        return ImageFile(file = file, metadata = metadata, hash = "hash_$name", fileType = type)
    }

    @Nested
    @DisplayName("RAW+JPEG pair detection")
    inner class RawJpegPairs {
        @Test
        @DisplayName("should detect RAW+JPEG pairs with same base filename")
        fun shouldDetectPairs() {
            val raw = createImageFile("IMG_1234", "cr2", ImageFileType.RAW_CR2)
            val jpeg = createImageFile("IMG_1234", "jpg", ImageFileType.JPEG)
            val standalone = createImageFile("IMG_5678", "jpg", ImageFileType.JPEG)

            val pairs = service.detectRawJpegPairs(listOf(raw, jpeg, standalone))

            assertThat(pairs).hasSize(1)
            assertThat(pairs[0].first.fileType).isEqualTo(ImageFileType.RAW_CR2)
            assertThat(pairs[0].second.fileType).isEqualTo(ImageFileType.JPEG)
        }

        @Test
        @DisplayName("should return empty list when no pairs exist")
        fun shouldReturnEmptyWhenNoPairs() {
            val img1 = createImageFile("IMG_1234", "jpg", ImageFileType.JPEG)
            val img2 = createImageFile("IMG_5678", "jpg", ImageFileType.JPEG)

            val pairs = service.detectRawJpegPairs(listOf(img1, img2))

            assertThat(pairs).isEmpty()
        }

        @Test
        @DisplayName("should filter JPEG when RAW_ONLY mode")
        fun shouldFilterJpegInRawOnlyMode() {
            val raw = createImageFile("PHOTO", "arw", ImageFileType.RAW_ARW)
            val jpeg = createImageFile("PHOTO", "jpg", ImageFileType.JPEG)
            val standalone = createImageFile("OTHER", "jpg", ImageFileType.JPEG)
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.RAW_ONLY)

            val result = service.applyPairFilter(listOf(raw, jpeg, standalone), config)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.fileType })
                .containsExactlyInAnyOrder(ImageFileType.RAW_ARW, ImageFileType.JPEG)
            assertThat(
                    result.none {
                        it.file.nameWithoutExtension == "PHOTO" && it.fileType == ImageFileType.JPEG
                    }
                )
                .isTrue()
        }

        @Test
        @DisplayName("should filter RAW when JPEG_ONLY mode")
        fun shouldFilterRawInJpegOnlyMode() {
            val raw = createImageFile("PHOTO", "nef", ImageFileType.RAW_NEF)
            val jpeg = createImageFile("PHOTO", "jpg", ImageFileType.JPEG)
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.JPEG_ONLY)

            val result = service.applyPairFilter(listOf(raw, jpeg), config)

            assertThat(result).hasSize(1)
            assertThat(result[0].fileType).isEqualTo(ImageFileType.JPEG)
        }

        @Test
        @DisplayName("should return all when IMPORT_BOTH mode")
        fun shouldReturnAllInImportBothMode() {
            val raw = createImageFile("IMG", "cr3", ImageFileType.RAW_CR3)
            val jpeg = createImageFile("IMG", "jpg", ImageFileType.JPEG)
            val config = ImportConfiguration(rawJpegPairMode = RawJpegPairMode.IMPORT_BOTH)

            val result = service.applyPairFilter(listOf(raw, jpeg), config)

            assertThat(result).hasSize(2)
        }
    }

    @Nested
    @DisplayName("Already-transferred filtering")
    inner class TransferredFiltering {
        @Test
        @DisplayName("should filter images whose hashes exist in destination")
        fun shouldFilterByHash() {
            val img1 = createImageFile("a").copy(hash = "hash_a")
            val img2 = createImageFile("b").copy(hash = "hash_b")
            val destHashes = setOf("hash_a")
            val config = ImportConfiguration(detectTransferredByHash = true)

            val result = service.filterAlreadyTransferred(listOf(img1, img2), destHashes, config)

            assertThat(result).hasSize(1)
            assertThat(result[0].hash).isEqualTo("hash_b")
        }

        @Test
        @DisplayName("should return all when hash detection disabled")
        fun shouldReturnAllWhenDisabled() {
            val img = createImageFile("a").copy(hash = "hash_a")
            val destHashes = setOf("hash_a")
            val config =
                ImportConfiguration(
                    detectTransferredByHash = false,
                    detectTransferredByExif = false,
                )

            val result = service.filterAlreadyTransferred(listOf(img), destHashes, config)

            assertThat(result).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Import execution")
    inner class ImportExecution {
        @Test
        @DisplayName("should execute import and copy files")
        fun shouldExecuteImport() = runTest {
            val sourceFile = File(tempDir, "source.jpg")
            sourceFile.writeText("image data")
            val image = ImageFile(file = sourceFile, hash = "abc")
            val config = ImportConfiguration(verifyAfterCopy = false)
            val destDir = File(tempDir, "dest")
            destDir.mkdirs()

            whenever(namingPort.generateFolderPath(any(), eq(destDir.absolutePath), any()))
                .thenReturn(destDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("source.jpg")
            whenever(imageRepository.copyFile(any(), any(), any())).thenReturn(true)

            val result = service.executeImport(listOf(image), destDir.absolutePath, config)

            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.errorCount).isEqualTo(0)
            assertThat(result.totalFiles).isEqualTo(1)
        }

        @Test
        @DisplayName("should handle copy failure gracefully")
        fun shouldHandleCopyFailure() = runTest {
            val sourceFile = File(tempDir, "fail.jpg")
            sourceFile.writeText("data")
            val image = ImageFile(file = sourceFile)
            val config = ImportConfiguration(verifyAfterCopy = false)

            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn(tempDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("fail.jpg")
            whenever(imageRepository.copyFile(any(), any(), any())).thenReturn(false)

            val result = service.executeImport(listOf(image), tempDir.absolutePath, config)

            assertThat(result.successCount).isEqualTo(0)
            assertThat(result.errorCount).isEqualTo(1)
        }

        @Test
        @DisplayName("should skip on conflict when configured")
        fun shouldSkipOnConflict() = runTest {
            val destFile = File(tempDir, "existing.jpg")
            destFile.writeText("existing")
            val sourceFile = File(tempDir, "src/existing.jpg")
            sourceFile.parentFile.mkdirs()
            sourceFile.writeText("source")
            val image = ImageFile(file = sourceFile)
            val config =
                ImportConfiguration(
                    conflictResolution = ConflictResolution.SKIP,
                    verifyAfterCopy = false,
                )

            whenever(namingPort.generateFolderPath(any(), any(), any()))
                .thenReturn(tempDir.absolutePath)
            whenever(namingPort.generateFileName(any(), any(), any())).thenReturn("existing.jpg")

            val result = service.executeImport(listOf(image), tempDir.absolutePath, config)

            assertThat(result.skippedCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("scanSource")
    inner class ScanSource {
        @Test
        @DisplayName("should throw for non-existent directory")
        fun shouldThrowForMissingDir() = runTest {
            val exception = runCatching { service.scanSource("/nonexistent/path") }
            assertThat(exception.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
