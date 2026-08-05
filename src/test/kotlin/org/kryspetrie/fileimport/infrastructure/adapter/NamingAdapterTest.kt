package org.kryspetrie.fileimport.infrastructure.adapter

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

@DisplayName("NamingAdapter")
class NamingAdapterTest {
    private lateinit var adapter: NamingAdapter
    private lateinit var testImageFile: ImageFile

    @BeforeEach
    fun setup() {
        adapter = NamingAdapter()
        testImageFile =
            ImageFile(
                path = FilePath("/source/IMG_1234.jpg"),
                metadata =
                    ImageMetadata(
                        dateTimeOriginal = LocalDateTime.of(2024, 3, 15, 14, 30, 45),
                        make = "Canon",
                        model = "EOS R5",
                        lensModel = "RF 50mm F1.2 L USM",
                        iso = 400,
                        aperture = 1.2f,
                        focalLength = 50f,
                    ),
            )
    }

    @Nested
    @DisplayName("generateFolderPath")
    inner class GenerateFolderPath {
        @Test
        @DisplayName("should create folder with default date pattern")
        fun shouldCreateDefaultFolder() {
            // GIVEN
            val config = ImportConfiguration(folderPattern = "{yyyy-MM-dd}")

            // WHEN
            val folderPath = adapter.generateFolderPath(testImageFile, "/destination", config)

            // THEN
            assertThat(folderPath).isEqualTo("/destination/2024-03-15")
        }

        @Test
        @DisplayName("should create folder with year-month pattern")
        fun shouldCreateYearMonthFolder() {
            // GIVEN
            val config = ImportConfiguration(folderPattern = "{yyyy-MM}")

            // WHEN
            val folderPath = adapter.generateFolderPath(testImageFile, "/destination", config)

            // THEN
            assertThat(folderPath).isEqualTo("/destination/2024-03")
        }

        @Test
        @DisplayName("should return destination root when subfolders disabled")
        fun shouldReturnRootWhenNoSubfolders() {
            // GIVEN
            val config = ImportConfiguration(createSubfolders = false)

            // WHEN
            val folderPath = adapter.generateFolderPath(testImageFile, "/destination", config)

            // THEN
            assertThat(folderPath).isEqualTo("/destination")
        }
    }

    @Nested
    @DisplayName("generateFileName")
    inner class GenerateFileName {
        @Test
        @DisplayName("should preserve original filename")
        fun shouldPreserveOriginalName() {
            // GIVEN
            val config =
                ImportConfiguration(fileNamePattern = "{original}", preserveOriginalName = true)

            // WHEN
            val fileName = adapter.generateFileName(testImageFile, config, 1)

            // THEN
            assertThat(fileName).isEqualTo("IMG_1234.jpg")
        }

        @Test
        @DisplayName("should use date pattern")
        fun shouldUseDatePattern() {
            // GIVEN
            val config =
                ImportConfiguration(
                    fileNamePattern = "{yyyy}-{MM}-{dd}_{original}",
                    preserveOriginalName = false,
                )

            // WHEN
            val fileName = adapter.generateFileName(testImageFile, config, 1)

            // THEN
            assertThat(fileName).isEqualTo("2024-03-15_IMG_1234.jpg")
        }

        @Test
        @DisplayName("should use counter pattern")
        fun shouldUseCounterPattern() {
            // GIVEN
            val config =
                ImportConfiguration(
                    fileNamePattern = "Photo_{counter}",
                    preserveOriginalName = false,
                )

            // WHEN
            val fileName = adapter.generateFileName(testImageFile, config, 42)

            // THEN
            assertThat(fileName).isEqualTo("Photo_0042.jpg")
        }

        @Test
        @DisplayName("should include EXIF metadata in filename")
        fun shouldIncludeExifMetadata() {
            // GIVEN
            val config =
                ImportConfiguration(
                    fileNamePattern = "{original}_{iso}_{aperture}",
                    preserveOriginalName = false,
                )

            // WHEN
            val fileName = adapter.generateFileName(testImageFile, config, 1)

            // THEN
            assertThat(fileName).isEqualTo("IMG_1234_400_f1.2.jpg")
        }
    }

    @Nested
    @DisplayName("video placeholder resolution")
    inner class VideoPlaceholders {
        @Test
        @DisplayName("should resolve type placeholder for video")
        fun shouldResolveTypeForVideo() {
            val videoFile =
                ImageFile(
                    path = FilePath("/source/VID_001.mp4"),
                    fileType = ImageFileType.VIDEO_MP4,
                    metadata =
                        ImageMetadata(
                            dateTimeOriginal = LocalDateTime.of(2024, 3, 15, 14, 30, 45),
                            durationSeconds = 90.0,
                            frameRate = 30.0,
                            videoCodec = "H.264",
                        ),
                )
            val config = ImportConfiguration(folderPattern = "{yyyy}/{type}")
            val folder = adapter.generateFolderPath(videoFile, "/dest", config)
            assertThat(folder).isEqualTo("/dest/2024/Videos")
        }

        @Test
        @DisplayName("should resolve type placeholder for photo")
        fun shouldResolveTypeForPhoto() {
            val config = ImportConfiguration(folderPattern = "{yyyy}/{type}")
            val folder = adapter.generateFolderPath(testImageFile, "/dest", config)
            assertThat(folder).isEqualTo("/dest/2024/Photos")
        }

        @Test
        @DisplayName("should resolve duration in filename")
        fun shouldResolveDuration() {
            val videoFile =
                ImageFile(
                    path = FilePath("/source/VID.mp4"),
                    fileType = ImageFileType.VIDEO_MP4,
                    metadata =
                        ImageMetadata(
                            dateTimeOriginal = LocalDateTime.of(2024, 1, 1, 0, 0),
                            durationSeconds = 90.0,
                        ),
                )
            val config =
                ImportConfiguration(
                    fileNamePattern = "{original}_{duration}",
                    preserveOriginalName = false,
                )
            val name = adapter.generateFileName(videoFile, config, 1)
            assertThat(name).isEqualTo("VID_1m30s.mp4")
        }

        @Test
        @DisplayName("should resolve fps and codec in filename")
        fun shouldResolveFpsAndCodec() {
            val videoFile =
                ImageFile(
                    path = FilePath("/source/VID.mp4"),
                    fileType = ImageFileType.VIDEO_MP4,
                    metadata =
                        ImageMetadata(
                            dateTimeOriginal = LocalDateTime.of(2024, 1, 1, 0, 0),
                            frameRate = 60.0,
                            videoCodec = "H.265",
                        ),
                )
            val config =
                ImportConfiguration(
                    fileNamePattern = "{original}_{fps}fps_{codec}",
                    preserveOriginalName = false,
                )
            val name = adapter.generateFileName(videoFile, config, 1)
            assertThat(name).isEqualTo("VID_60fps_H.265.mp4")
        }
    }

    @Nested
    @DisplayName("previewFileStructure")
    inner class PreviewFileStructure {
        @Test
        @DisplayName("should generate preview for multiple files")
        fun shouldGeneratePreview() {
            // GIVEN
            val config = ImportConfiguration()
            val images =
                listOf(
                    testImageFile,
                    testImageFile.copy(id = "test-2", path = FilePath("/source/IMG_1235.jpg")),
                )

            // WHEN
            val previews = adapter.previewFileStructure(images, "/destination", config)

            // THEN
            assertThat(previews).hasSize(2)
            assertThat(previews[0].folderPath).isEqualTo("/destination/2024-03-15")
            assertThat(previews[0].fileName).isEqualTo("IMG_1234.jpg")
        }
    }

    @Nested
    @DisplayName("resolveConflict")
    inner class ResolveConflict {
        @Test
        @DisplayName("should not loop when pattern lacks counter and file exists")
        fun shouldDisambiguateWithoutCounter() {
            val tempFile = java.io.File.createTempFile("naming_test_", ".jpg")
            tempFile.deleteOnExit()
            val existing = java.io.File(tempFile.parent, "duplicate_name.jpg")
            existing.writeText("existing")
            existing.deleteOnExit()

            val config =
                ImportConfiguration(
                    fileNamePattern = "{original}",
                    preserveOriginalName = true,
                    createSubfolders = false,
                )
            val image =
                ImageFile(
                    path = FilePath("/source/duplicate_name.jpg"),
                    metadata = ImageMetadata(dateTimeOriginal = LocalDateTime.of(2024, 1, 1, 0, 0)),
                )

            val resolved = adapter.resolveConflict(image, tempFile.parent, config)

            assertThat(resolved).isNotEqualTo(existing.absolutePath)
            assertThat(java.io.File(resolved).name).contains("_")
        }
    }
}
