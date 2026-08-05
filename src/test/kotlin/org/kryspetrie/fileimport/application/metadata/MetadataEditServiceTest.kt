package org.kryspetrie.fileimport.application.metadata

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.application.TestFileSystemAdapter
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.ProcessedImage
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("MetadataEditService")
class MetadataEditServiceTest {

    @TempDir lateinit var tempDir: File

    private val metadataWritingService = mock<MetadataWritingService>()
    private val imageProcessing = mock<ImageProcessingPort>()
    private val imageRepository = mock<ImageRepositoryPort>()
    private val fileSystem = TestFileSystemAdapter()
    private val undoService = mock<MetadataEditUndoService>()

    private lateinit var service: MetadataEditService

    @BeforeEach
    fun setUp() {
        runBlocking { whenever(undoService.createBackup(any())).thenReturn("/tmp/backup.jpg") }
        service =
            MetadataEditService(
                metadataWritingService = metadataWritingService,
                imageProcessing = imageProcessing,
                imageRepository = imageRepository,
                fileSystem = fileSystem,
                undoService = undoService,
            )
    }

    @Nested
    @DisplayName("rotationDegreesToAngle")
    inner class RotationDegreesToAngleTests {
        @Test
        fun mapsCardinalDegreesToRotationAngle() {
            assertThat(service.rotationDegreesToAngle(0)).isEqualTo(RotationAngle.NONE)
            assertThat(service.rotationDegreesToAngle(90)).isEqualTo(RotationAngle.CW_90)
            assertThat(service.rotationDegreesToAngle(180)).isEqualTo(RotationAngle.CW_180)
            assertThat(service.rotationDegreesToAngle(270)).isEqualTo(RotationAngle.CCW_90)
        }

        @Test
        fun normalizesNegativeAndOverflowDegrees() {
            assertThat(service.rotationDegreesToAngle(-90)).isEqualTo(RotationAngle.CCW_90)
            assertThat(service.rotationDegreesToAngle(450)).isEqualTo(RotationAngle.CW_90)
        }
    }

    @Nested
    @DisplayName("saveFile rotation routing")
    inner class SaveFileRotationRoutingTests {
        @Test
        fun jpegRotationPhysicallyRotatesPixelsAndResetsOrientation() = runBlocking {
            val jpeg = createJpegFile()
            val sourceImage = TestProcessedImage(width = 120, height = 80)
            val rotatedImage = TestProcessedImage(width = 80, height = 120)

            whenever(imageProcessing.readImage(FilePath(jpeg.absolutePath))).thenReturn(sourceImage)
            whenever(imageProcessing.rotateImage(sourceImage, RotationAngle.CW_90))
                .thenReturn(rotatedImage)

            val result =
                service.saveFile(
                    file = jpeg,
                    config = PhotoScanConfiguration(rotationDegrees = 90),
                    outputMode = "OVERWRITE",
                    outputDirectory = "",
                )

            assertThat(result).isNotNull
            verify(imageProcessing).rotateImage(sourceImage, RotationAngle.CW_90)
            verify(metadataWritingService)
                .writeImageWithMetadata(
                    image = eq(rotatedImage),
                    outputPath = any(),
                    config = eq(PhotoScanConfiguration(rotationDegrees = 0)),
                    sourcePath = any(),
                    detectedPhoto = eq(null),
                    marginFraction = eq(0.02),
                    sourceImage = eq(null),
                    preRotationWidth = eq(120),
                    preRotationHeight = eq(80),
                    jpegQuality = eq(0.95f),
                    physicalPixelRotationApplied = eq(true),
                )
            verify(metadataWritingService, never())
                .writeMetadataOnly(
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                    any(),
                )
        }

        @Test
        fun tiffRotationUsesPixelPath() = runBlocking {
            val tiff = File(tempDir, "scan.tif").apply { writeBytes(byteArrayOf(0)) }
            val sourceImage = TestProcessedImage(200, 100)
            whenever(imageProcessing.readImage(FilePath(tiff.absolutePath))).thenReturn(sourceImage)
            whenever(imageProcessing.rotateImage(any(), any())).thenReturn(sourceImage)

            service.saveFile(
                file = tiff,
                config = PhotoScanConfiguration(rotationDegrees = 180),
                outputMode = "OVERWRITE",
                outputDirectory = "",
            )

            verify(metadataWritingService)
                .writeImageWithMetadata(
                    image = any(),
                    outputPath = any(),
                    config = eq(PhotoScanConfiguration(rotationDegrees = 0)),
                    sourcePath = any(),
                    detectedPhoto = eq(null),
                    marginFraction = eq(0.02),
                    sourceImage = eq(null),
                    preRotationWidth = eq(200),
                    preRotationHeight = eq(100),
                    jpegQuality = eq(0.95f),
                    physicalPixelRotationApplied = eq(true),
                )
        }

        @Test
        fun rawRotationUsesMetadataOnlyPathWithoutDecodingImage() = runBlocking {
            val raw = File(tempDir, "photo.CR2").apply { writeBytes(byteArrayOf(0x01, 0x02)) }
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 6000, imageHeight = 4000))

            val result =
                service.saveFile(
                    file = raw,
                    config = PhotoScanConfiguration(rotationDegrees = 90),
                    outputMode = "OVERWRITE",
                    outputDirectory = "",
                )

            assertThat(result).isNotNull
            verify(imageProcessing, never()).readImage(any())
            verify(metadataWritingService)
                .writeMetadataOnly(
                    outputPath = any(),
                    config = eq(PhotoScanConfiguration(rotationDegrees = 90)),
                    sourcePath = any(),
                    detectedPhoto = eq(null),
                    marginFraction = eq(0.02),
                    sourceImage = eq(null),
                    preRotationWidth = eq(6000),
                    preRotationHeight = eq(4000),
                    physicalPixelRotationApplied = eq(false),
                )
            verify(metadataWritingService, never())
                .writeImageWithMetadata(
                    any(),
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
        }

        @Test
        fun metadataOnlyEditsSkipImageDecodingForJpeg() = runBlocking {
            val jpeg = createJpegFile()
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 640, imageHeight = 480))

            service.saveFile(
                file = jpeg,
                config = PhotoScanConfiguration(description = "caption only"),
                outputMode = "OVERWRITE",
                outputDirectory = "",
            )

            verify(imageProcessing, never()).readImage(any())
            verify(metadataWritingService)
                .writeMetadataOnly(
                    outputPath = any(),
                    config = eq(PhotoScanConfiguration(description = "caption only")),
                    sourcePath = any(),
                    detectedPhoto = eq(null),
                    marginFraction = eq(0.02),
                    sourceImage = eq(null),
                    preRotationWidth = eq(640),
                    preRotationHeight = eq(480),
                    physicalPixelRotationApplied = eq(false),
                )
        }
    }

    @Nested
    @DisplayName("saveFile output modes")
    inner class SaveFileOutputModeTests {
        @Test
        fun overwriteAbortsWhenBackupFails() = runBlocking {
            whenever(undoService.createBackup(any())).thenReturn(null)
            val jpeg = createJpegFile()
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 120, imageHeight = 80))

            val result =
                service.saveFile(
                    file = jpeg,
                    config = PhotoScanConfiguration(description = "x"),
                    outputMode = "OVERWRITE",
                    outputDirectory = "",
                )

            assertThat(result).isNull()
            verify(metadataWritingService, never())
                .writeMetadataOnly(
                    any(),
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                    any(),
                )
        }

        @Test
        fun overwriteRecordsBackupPathAndWasSavedNewFalse() = runBlocking {
            val jpeg = createJpegFile()
            whenever(undoService.createBackup(any())).thenReturn("/tmp/backup_photo.jpg")
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 120, imageHeight = 80))

            val result =
                service.saveFile(
                    file = jpeg,
                    config = PhotoScanConfiguration(description = "caption"),
                    outputMode = "OVERWRITE",
                    outputDirectory = "",
                )

            assertThat(result).isNotNull
            assertThat(result!!.entry.backupPath).isEqualTo("/tmp/backup_photo.jpg")
            assertThat(result.entry.wasSavedNew).isFalse()
            assertThat(result.entry.outputFilePath).isEmpty()
            assertThat(result.entry.filePath).isEqualTo(jpeg.absolutePath)
            verify(undoService).createBackup(jpeg.absolutePath)
        }

        @Test
        fun saveNewUsesParentWhenOutputDirectoryBlank() = runBlocking {
            val jpeg = createJpegFile()
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 120, imageHeight = 80))

            val result =
                service.saveFile(
                    file = jpeg,
                    config = PhotoScanConfiguration(description = "caption"),
                    outputMode = "SAVE_NEW",
                    outputDirectory = "",
                )

            assertThat(result).isNotNull
            assertThat(result!!.entry.wasSavedNew).isTrue()
            assertThat(result.entry.backupPath).isEmpty()
            assertThat(result.entry.outputFilePath)
                .isEqualTo(File(jpeg.parent, jpeg.name).absolutePath)
            verify(undoService, never()).createBackup(any())
            verify(metadataWritingService)
                .writeMetadataOnly(any(), any(), anyOrNull(), anyOrNull(), any(), any())
        }

        @Test
        fun saveNewWritesUnderProvidedOutputDirectory() = runBlocking {
            val jpeg = createJpegFile()
            val outDir = File(tempDir, "exports").apply { mkdirs() }
            whenever(imageRepository.getMetadata(any()))
                .thenReturn(ImageMetadata(imageWidth = 120, imageHeight = 80))

            val result =
                service.saveFile(
                    file = jpeg,
                    config = PhotoScanConfiguration(keywords = "a,b"),
                    outputMode = "SAVE_NEW",
                    outputDirectory = outDir.absolutePath,
                )

            assertThat(result).isNotNull
            assertThat(result!!.entry.wasSavedNew).isTrue()
            assertThat(result.entry.outputFilePath).isEqualTo(File(outDir, jpeg.name).absolutePath)
            assertThat(File(outDir, jpeg.name).exists()).isTrue()
        }
    }

    private fun createJpegFile(): File {
        val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.RED
        graphics.fillRect(0, 0, 120, 80)
        graphics.dispose()
        val file = File(tempDir, "photo.jpg")
        ImageIO.write(image, "jpg", file)
        return file
    }

    private class TestProcessedImage(override val width: Int, override val height: Int) :
        ProcessedImage
}
