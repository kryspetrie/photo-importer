package org.kryspetrie.fileimport.application

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.DuplicateType
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@DisplayName("DuplicateScannerService")
class DuplicateScannerServiceTest {
    private lateinit var imageRepository: ImageRepositoryPort
    private lateinit var deduplicationPort: DeduplicationPort
    private lateinit var service: DuplicateScannerService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        imageRepository = mock(ImageRepositoryPort::class.java)
        deduplicationPort = mock(DeduplicationPort::class.java)
        service = DuplicateScannerService(
            imageRepository, deduplicationPort, null, TestTimeProvider(), TestDispatcherProvider()
        )
    }

    @Nested
    @DisplayName("scanForDuplicates")
    inner class ScanForDuplicates {
        @Test
        @DisplayName("should throw for non-existent folder")
        fun shouldThrowForMissingFolder() = runTest {
            val exception = runCatching {
                service.scanForDuplicates("/nonexistent", DeduplicationSettings())
            }
            assertThat(exception.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        @DisplayName("should return empty for folder with fewer than 2 files")
        fun shouldReturnEmptyForSingleFile() = runTest {
            val f = File(tempDir, "single.jpg")
            f.writeText("data")
            whenever(imageRepository.scanDirectory(any(), any()))
                .thenReturn(listOf(ImageFile(file = f)))

            val result = service.scanForDuplicates(tempDir.absolutePath, DeduplicationSettings())

            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("resolveGroup")
    inner class ResolveGroup {
        @Test
        @DisplayName("should keep highest resolution when configured")
        fun shouldKeepHighestRes() = runTest {
            val f1 = File(tempDir, "hi_res.jpg")
            f1.writeText("high")
            val f2 = File(tempDir, "lo_res.jpg")
            f2.writeText("low")
            val img1 =
                ImageFile(
                    file = f1,
                    metadata = ImageMetadata(imageWidth = 4000, imageHeight = 3000),
                )
            val img2 =
                ImageFile(
                    file = f2,
                    metadata = ImageMetadata(imageWidth = 1920, imageHeight = 1080),
                )
            val group = DuplicateInfo(img1, listOf(img2), DuplicateType.EXACT_HASH)

            val trashDir = File(tempDir, "trash")
            trashDir.mkdirs()
            val removed =
                service.resolveGroup(group, DuplicateAction.KEEP_HIGHEST_RES, trashDir.absolutePath)

            assertThat(removed).isEqualTo(1)
            assertThat(f1.exists()).isTrue()
            assertThat(File(trashDir, "lo_res.jpg").exists()).isTrue()
        }

        @Test
        @DisplayName("should keep RAW over JPEG when configured")
        fun shouldKeepRawOverJpeg() = runTest {
            val rawFile = File(tempDir, "photo.cr2")
            rawFile.writeText("raw")
            val jpegFile = File(tempDir, "photo.jpg")
            jpegFile.writeText("jpeg")
            val rawImg = ImageFile(file = rawFile, fileType = ImageFileType.RAW_CR2)
            val jpegImg = ImageFile(file = jpegFile, fileType = ImageFileType.JPEG)
            val group = DuplicateInfo(jpegImg, listOf(rawImg), DuplicateType.EXACT_HASH)

            val trashDir = File(tempDir, "trash")
            trashDir.mkdirs()
            service.resolveGroup(group, DuplicateAction.KEEP_RAW_OVER_JPEG, trashDir.absolutePath)

            assertThat(rawFile.exists()).isTrue()
            // JPEG should be moved to trash
            assertThat(File(trashDir, "photo.jpg").exists()).isTrue()
        }

        @Test
        @DisplayName("should delete files when no trash folder specified")
        fun shouldDeleteWithoutTrash() = runTest {
            val f1 = File(tempDir, "keep.jpg")
            f1.writeText("keep")
            val f2 = File(tempDir, "delete.jpg")
            f2.writeText("delete")
            val img1 =
                ImageFile(
                    file = f1,
                    metadata = ImageMetadata(imageWidth = 4000, imageHeight = 3000),
                )
            val img2 =
                ImageFile(file = f2, metadata = ImageMetadata(imageWidth = 800, imageHeight = 600))
            val group = DuplicateInfo(img1, listOf(img2), DuplicateType.EXACT_HASH)

            service.resolveGroup(group, DuplicateAction.KEEP_HIGHEST_RES, null)

            assertThat(f1.exists()).isTrue()
            assertThat(f2.exists()).isFalse()
        }
    }

    @Nested
    @DisplayName("resolveAll")
    inner class ResolveAll {
        @Test
        @DisplayName("should resolve all groups and report progress")
        fun shouldResolveAllGroups() = runTest {
            val f1 = File(tempDir, "a.jpg")
            f1.writeText("a")
            val f2 = File(tempDir, "b.jpg")
            f2.writeText("b")
            val f3 = File(tempDir, "c.jpg")
            f3.writeText("c")
            val f4 = File(tempDir, "d.jpg")
            f4.writeText("d")
            val img1 =
                ImageFile(
                    file = f1,
                    metadata = ImageMetadata(imageWidth = 4000, imageHeight = 3000),
                )
            val img2 =
                ImageFile(file = f2, metadata = ImageMetadata(imageWidth = 800, imageHeight = 600))
            val img3 =
                ImageFile(
                    file = f3,
                    metadata = ImageMetadata(imageWidth = 5000, imageHeight = 4000),
                )
            val img4 =
                ImageFile(file = f4, metadata = ImageMetadata(imageWidth = 640, imageHeight = 480))

            val groups =
                listOf(
                    DuplicateInfo(img1, listOf(img2), DuplicateType.EXACT_HASH),
                    DuplicateInfo(img3, listOf(img4), DuplicateType.EXACT_HASH),
                )

            val progressUpdates = mutableListOf<Pair<Int, Int>>()
            val totalRemoved =
                service.resolveAll(groups, DuplicateAction.KEEP_HIGHEST_RES) { cur, tot ->
                    progressUpdates.add(cur to tot)
                }

            assertThat(totalRemoved).isEqualTo(2)
            assertThat(progressUpdates).containsExactly(1 to 2, 2 to 2)
        }
    }
}
