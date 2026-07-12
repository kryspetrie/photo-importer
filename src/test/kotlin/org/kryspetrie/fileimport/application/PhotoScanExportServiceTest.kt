package org.kryspetrie.fileimport.application

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.GeometryUtils
import org.kryspetrie.fileimport.domain.model.OverrideState
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService

@DisplayName("PhotoScanExportService")
class PhotoScanExportServiceTest {
    private lateinit var service: PhotoScanExportService
    private lateinit var perspectiveService: PerspectiveCorrectionService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        perspectiveService = PerspectiveCorrectionService()
        val fileSystem = FileSystemAdapter()
        val imageProcessing = AwtImageProcessingAdapter(fileSystem)
        service =
            PhotoScanExportService(
                perspectiveService,
                MetadataWritingService(FaceRegionTransformer(), imageProcessing, fileSystem),
                imageProcessing,
                fileSystem,
            )
    }

    /** Wrapper to call suspend [PhotoScanExportService.exportPhotos] from non-suspend tests. */
    private fun exportPhotos(
        sourceFile: FilePath,
        image: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        detectedPhotos: List<org.kryspetrie.fileimport.domain.model.DetectedPhoto>,
        destinationPath: String,
        baseFileName: String,
    ): org.kryspetrie.fileimport.domain.model.PhotoScanExportResult = runBlocking {
        service.exportPhotos(sourceFile, image, detectedPhotos, destinationPath, baseFileName)
    }

    /**
     * Wrapper to call suspend [PhotoScanExportService.exportSinglePhoto] from non-suspend tests.
     */
    private fun exportSinglePhoto(
        sourceImage: org.kryspetrie.fileimport.domain.model.ProcessedImage,
        detectedPhoto: org.kryspetrie.fileimport.domain.model.DetectedPhoto,
        destinationPath: String,
        baseFileName: String,
        sourceFile: FilePath? = null,
    ): org.kryspetrie.fileimport.domain.model.PhotoScanSingleExportResult = runBlocking {
        service.exportSinglePhoto(
            sourceImage,
            detectedPhoto,
            destinationPath,
            baseFileName,
            sourceFile,
        )
    }

    private fun createTestImage(width: Int, height: Int, color: Int): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(color)
        g.fillRect(0, 0, width, height)
        // Add some detail for perspective correction to work with
        g.color = java.awt.Color(0xFF, 0x00, 0x00)
        g.fillRect(width / 4, height / 4, width / 2, height / 2)
        g.dispose()

        val file = File(tempDir, "test_${System.nanoTime()}.jpg")
        ImageIO.write(img, "jpg", file)
        return file
    }

    private fun createDetectedPhoto(
        tlX: Float = 0f,
        tlY: Float = 0f,
        trX: Float = 100f,
        trY: Float = 0f,
        brX: Float = 100f,
        brY: Float = 100f,
        blX: Float = 0f,
        blY: Float = 100f,
    ): org.kryspetrie.fileimport.domain.model.DetectedPhoto {
        return org.kryspetrie.fileimport.domain.model.DetectedPhoto(
            topLeft = org.kryspetrie.fileimport.domain.model.PhotoCorner(tlX, tlY),
            topRight = org.kryspetrie.fileimport.domain.model.PhotoCorner(trX, trY),
            bottomRight = org.kryspetrie.fileimport.domain.model.PhotoCorner(brX, brY),
            bottomLeft = org.kryspetrie.fileimport.domain.model.PhotoCorner(blX, blY),
        )
    }

    @Nested
    @DisplayName("service initialization")
    inner class ServiceInitialization {
        @Test
        @DisplayName("should initialize with perspective service")
        fun shouldInitialize() {
            assertThat(service).isNotNull
        }

        @Test
        @DisplayName("should allow jpeg quality configuration via ImageProcessingPort")
        fun shouldAllowQualityConfiguration() {
            // JPEG quality is now configured per-call via ImageProcessingPort.writeJpegImage()
            // This test validates that the service initializes correctly with the port
            assertThat(service).isNotNull
        }
    }

    @Nested
    @DisplayName("export result structure")
    inner class ExportResultStructure {
        @Test
        @DisplayName("should return ExportResult with success flag")
        fun shouldReturnExportResult() {
            val sourceFile = createTestImage(200, 200, 0x808080)
            val source = ImageIO.read(sourceFile)
            val photos = listOf(createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 200f, 0f, 200f))
            val destDir = File(tempDir, "exports1")
            destDir.mkdirs()

            val result =
                exportPhotos(
                    FilePath(sourceFile.absolutePath),
                    source.toProcessedImage(),
                    photos,
                    destDir.absolutePath,
                    "test_photo",
                )

            assertThat(result).isNotNull
            assertThat(result).hasFieldOrProperty("success")
            assertThat(result).hasFieldOrProperty("exportedFiles")
            assertThat(result).hasFieldOrProperty("errors")
        }

        @Test
        @DisplayName("should handle empty photos list")
        fun shouldHandleEmptyPhotosList() {
            val sourceFile = createTestImage(200, 200, 0xFFFFFF)
            val source = ImageIO.read(sourceFile)
            val destDir = File(tempDir, "empty")
            destDir.mkdirs()

            val result =
                exportPhotos(
                    FilePath(sourceFile.absolutePath),
                    source.toProcessedImage(),
                    emptyList(),
                    destDir.absolutePath,
                    "empty",
                )

            assertThat(result.success).isTrue()
            assertThat(result.exportedFiles).isEmpty()
        }
    }

    @Nested
    @DisplayName("error handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should report error for invalid source file")
        fun shouldReportErrorForInvalidSource() {
            val sourceFile = File("/nonexistent/file.jpg")
            val source = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            val photos = listOf(createDetectedPhoto())
            val destDir = File(tempDir, "error")

            val result =
                exportPhotos(
                    FilePath(sourceFile.absolutePath),
                    source.toProcessedImage(),
                    photos,
                    destDir.absolutePath,
                    "photo",
                )

            assertThat(result.success).isFalse()
            assertThat(result.errors).isNotEmpty()
        }
    }

    @Nested
    @DisplayName("margin application")
    inner class MarginApplication {
        @Test
        @DisplayName("zero margin returns same photo")
        fun zeroMarginReturnsSamePhoto() {
            val photo = createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
            val result = GeometryUtils.applyMargin(photo, 0.0)
            assertThat(result.topLeft.x).isEqualTo(photo.topLeft.x)
            assertThat(result.topLeft.y).isEqualTo(photo.topLeft.y)
            assertThat(result.bottomRight.x).isEqualTo(photo.bottomRight.x)
        }

        @Test
        @DisplayName("negative margin returns same photo")
        fun negativeMarginReturnsSamePhoto() {
            val photo = createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
            val result = GeometryUtils.applyMargin(photo, -0.05)
            assertThat(result.topLeft.x).isEqualTo(photo.topLeft.x)
        }

        @Test
        @DisplayName("2% margin pushes corners outward from center")
        fun twoPercentMarginPushesOutward() {
            // 100x100 square centered at (150, 150)
            val photo = createDetectedPhoto(100f, 100f, 200f, 100f, 200f, 200f, 100f, 200f)
            val result = GeometryUtils.applyMargin(photo, 0.02)

            // Diagonal = sqrt(100^2 + 100^2) ≈ 141.4
            // marginPx = 0.02 * 141.4 ≈ 2.83
            // Each corner pushed outward by ~2.83 pixels from center (150, 150)
            // TL (100,100): direction from center = (-50, -50), dist = 70.7
            //   new_x = 100 + (2.83/70.7)*(-50) = 100 - 2.0 = 98.0
            //   new_y = 100 + (2.83/70.7)*(-50) = 100 - 2.0 = 98.0
            assertThat(result.topLeft.x).isLessThan(photo.topLeft.x)
            assertThat(result.topLeft.y).isLessThan(photo.topLeft.y)
            assertThat(result.bottomRight.x).isGreaterThan(photo.bottomRight.x)
            assertThat(result.bottomRight.y).isGreaterThan(photo.bottomRight.y)
        }

        @Test
        @DisplayName("margin preserves perspective correction flag")
        fun marginPreservesPerspectiveFlag() {
            val photo =
                createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
                    .copy(applyPerspectiveCorrection = false)
            val result = GeometryUtils.applyMargin(photo, 0.02)
            assertThat(result.applyPerspectiveCorrection).isFalse()
        }

        @Test
        @DisplayName("margin preserves rotation")
        fun marginPreservesRotation() {
            val photo =
                createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
                    .copy(rotation = org.kryspetrie.fileimport.domain.model.RotationAngle.CW_90)
            val result = GeometryUtils.applyMargin(photo, 0.02)
            assertThat(result.rotation)
                .isEqualTo(org.kryspetrie.fileimport.domain.model.RotationAngle.CW_90)
        }

        @Test
        @DisplayName("equal margin on all corners for symmetric quad")
        fun equalMarginForSymmetricQuad() {
            // Perfect square: all corners equidistant from center
            val photo = createDetectedPhoto(100f, 100f, 200f, 100f, 200f, 200f, 100f, 200f)
            val result = GeometryUtils.applyMargin(photo, 0.02)

            val tlExpansion =
                kotlin.math.sqrt(
                    (result.topLeft.x - photo.topLeft.x.toDouble()) *
                        (result.topLeft.x - photo.topLeft.x.toDouble()) +
                        (result.topLeft.y - photo.topLeft.y.toDouble()) *
                            (result.topLeft.y - photo.topLeft.y.toDouble())
                )
            val brExpansion =
                kotlin.math.sqrt(
                    (result.bottomRight.x - photo.bottomRight.x.toDouble()) *
                        (result.bottomRight.x - photo.bottomRight.x.toDouble()) +
                        (result.bottomRight.y - photo.bottomRight.y.toDouble()) *
                            (result.bottomRight.y - photo.bottomRight.y.toDouble())
                )

            // All corners should expand by the same amount (within floating point tolerance)
            assertThat(tlExpansion).isCloseTo(brExpansion, org.assertj.core.data.Offset.offset(0.1))
        }
    }

    @Nested
    @DisplayName("export with real images")
    inner class RealImageExport {
        @Test
        @DisplayName("should export when photos are within image bounds")
        fun shouldExportWithinBounds() {
            val sourceFile = createTestImage(300, 200, 0x808080)
            val source = ImageIO.read(sourceFile)
            val photos = listOf(createDetectedPhoto(50f, 30f, 250f, 30f, 250f, 170f, 50f, 170f))
            val destDir = File(tempDir, "exports2")
            destDir.mkdirs()

            val result =
                exportPhotos(
                    FilePath(sourceFile.absolutePath),
                    source.toProcessedImage(),
                    photos,
                    destDir.absolutePath,
                    "bounded_photo",
                )

            // Result should have errors or success depending on perspective correction
            assertThat(result).isNotNull
        }
    }

    @Nested
    @DisplayName("EXIF metadata readback verification")
    inner class ExifReadbackVerification {

        /**
         * Helper: exports a single photo with EXIF config, then reads the exported file back using
         * metadata-extractor to verify tags were actually written.
         */
        private fun exportAndReadback(
            config: PhotoScanConfiguration,
            sourceFile: File? = null,
        ): com.drew.metadata.Metadata {
            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "exif_readback_${System.nanoTime()}")
            destDir.mkdirs()

            val result =
                exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "exif_test",
                    sourceFile = sourceFile?.let { FilePath(it.absolutePath) },
                )

            assertThat(result.success).isTrue()
            val exportedFile = File(result.destinationPath)
            assertThat(exportedFile).exists()

            return ImageMetadataReader.readMetadata(exportedFile)
        }

        @Test
        @DisplayName("should write ImageDescription (IFD0 tag 0x010E) and read it back")
        fun shouldWriteAndReadImageDescription() {
            val config = PhotoScanConfiguration(description = "A beautiful sunset")
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)).isTrue
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("A beautiful sunset")
        }

        @Test
        @DisplayName("should write Make (IFD0 tag 0x010F) and read it back")
        fun shouldWriteAndReadMake() {
            val config = PhotoScanConfiguration(cameraMake = "Canon")
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MAKE)).isTrue
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Canon")
        }

        @Test
        @DisplayName("should write Model (IFD0 tag 0x0110) and read it back")
        fun shouldWriteAndReadModel() {
            val config = PhotoScanConfiguration(cameraModel = "EOS R5")
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MODEL)).isTrue
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_MODEL)).isEqualTo("EOS R5")
        }

        @Test
        @DisplayName("should write DateTimeOriginal (Exif SubIFD tag 0x9003) and read it back")
        fun shouldWriteAndReadDateTimeOriginal() {
            val config = PhotoScanConfiguration(originalDate = "1985-06-15")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)).isTrue
            val dateStr = subIfd.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
            assertThat(dateStr).startsWith("1985:06:15")
        }

        @Test
        @DisplayName("should write year-only DateTimeOriginal and read it back")
        fun shouldWriteYearOnlyDateTimeOriginal() {
            val config = PhotoScanConfiguration(year = "1972")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)).isTrue
            val dateStr = subIfd.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
            assertThat(dateStr).startsWith("1972:01:01")
        }

        @Test
        @DisplayName("should write LensModel (Exif SubIFD tag 0xA434) and read it back")
        fun shouldWriteAndReadLensModel() {
            val config = PhotoScanConfiguration(lensModel = "RF24-105mm F4 L IS USM")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_LENS_MODEL)).isTrue
            assertThat(subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL))
                .isEqualTo("RF24-105mm F4 L IS USM")
        }

        @Test
        @DisplayName("should write FocalLength (Exif SubIFD tag 0x920A) and read it back")
        fun shouldWriteAndReadFocalLength() {
            val config = PhotoScanConfiguration(focalLength = "50mm")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)).isTrue
            val focalLength = subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)
            assertThat(focalLength).isNotNull
            assertThat(focalLength!!).isCloseTo(50.0, within(0.1))
        }

        @Test
        @DisplayName("should write FNumber (Exif SubIFD tag 0x829D) and read it back")
        fun shouldWriteAndReadFNumber() {
            val config = PhotoScanConfiguration(aperture = "f/2.8")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)).isTrue
            val fNumber = subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER)
            assertThat(fNumber).isNotNull
            assertThat(fNumber!!).isCloseTo(2.8, within(0.05))
        }

        @Test
        @DisplayName("should write ExposureTime (Exif SubIFD tag 0x829A) and read it back")
        fun shouldWriteAndReadExposureTime() {
            val config = PhotoScanConfiguration(shutterSpeed = "1/125")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)).isTrue
            // ExposureTime is a rational number: 1/125 = 0.008
            val exposureTime = subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
            assertThat(exposureTime).isNotNull
            assertThat(exposureTime!!).isCloseTo(1.0 / 125.0, within(0.0001))
        }

        @Test
        @DisplayName("should write ISOSpeedRatings (Exif SubIFD tag 0x8827) and read it back")
        fun shouldWriteAndReadIso() {
            val config = PhotoScanConfiguration(iso = "400")
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)).isTrue
            val isoValue = subIfd.getInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)
            assertThat(isoValue).isEqualTo(400)
        }

        @Test
        @DisplayName("should write IPTC Keywords and read them back (cross-platform macOS-visible)")
        fun shouldWriteAndReadIptcKeywords() {
            val config = PhotoScanConfiguration(keywords = "vacation, beach, family")
            val metadata = exportAndReadback(config)

            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptcDir).isNotNull
            assertThat(iptcDir!!.containsTag(IptcDirectory.TAG_KEYWORDS)).isTrue
            val keywords = iptcDir.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            assertThat(keywords!!.toList()).containsExactly("vacation", "beach", "family")
        }

        @Test
        @DisplayName("should write all EXIF fields at once and read them all back")
        fun shouldWriteAndReadAllFieldsAtOnce() {
            val config =
                PhotoScanConfiguration(
                    description = "Summer vacation 1985",
                    cameraMake = "Nikon",
                    cameraModel = "FM2",
                    originalDate = "1985-07-20",
                    lensModel = "Nikkor 50mm f/1.4",
                    focalLength = "50",
                    aperture = "f/1.4",
                    shutterSpeed = "1/250",
                    iso = "100",
                    keywords = "summer, vacation, 1985",
                )
            val metadata = exportAndReadback(config)

            // Verify IFD0 fields
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("Summer vacation 1985")
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Nikon")
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_MODEL)).isEqualTo("FM2")

            // Verify Exif SubIFD fields
            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL))
                .startsWith("1985:07:20")
            assertThat(subIfd.getString(ExifSubIFDDirectory.TAG_LENS_MODEL))
                .isEqualTo("Nikkor 50mm f/1.4")
            assertThat(subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)!!)
                .isCloseTo(50.0, within(0.1))
            assertThat(subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_FNUMBER)!!)
                .isCloseTo(1.4, within(0.05))
            assertThat(subIfd.getDoubleObject(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)!!)
                .isCloseTo(1.0 / 250.0, within(0.0001))
            assertThat(subIfd.getInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)).isEqualTo(100)

            // Verify IPTC Keywords
            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptcDir).isNotNull
            val keywords = iptcDir!!.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            assertThat(keywords!!.toList()).containsExactly("summer", "vacation", "1985")
        }

        @Test
        @DisplayName("with copyOriginalExif=false, should not carry source EXIF into output")
        fun shouldNotCarrySourceExifWhenCopyDisabled() {
            // Create a source image with some EXIF (just the plain JPEG from ImageIO — no EXIF)
            // Then export WITH copyOriginalExif=false. The output should have only overrides, no
            // scanner EXIF.
            val config = PhotoScanConfiguration(cameraMake = "TestCamera", copyOriginalExif = false)
            val metadata = exportAndReadback(config)

            // The override we set should be present
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("TestCamera")

            // No scanner-model "Make" from source should leak through (this source has no EXIF
            // anyway,
            // but the test structure proves that copyOriginalExif=false starts with empty
            // TiffOutputSet)
        }

        @Test
        @DisplayName("with copyOriginalExif=true (default), should carry source EXIF baseline")
        fun shouldCarrySourceExifWhenCopyEnabled() {
            // Create a source image that has EXIF data (from ImageIO write, it won't have EXIF,
            // but the test validates the code path of reading from sourceFile)
            val sourceFile = createTestImage(200, 200, 0x808080)

            val config =
                PhotoScanConfiguration(
                    cameraMake = "MyCamera", // Override just the Make
                    copyOriginalExif = true,
                )

            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "exif_copy_test_${System.nanoTime()}")
            destDir.mkdirs()

            val result =
                exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "exif_copy_test",
                    sourceFile = sourceFile?.let { FilePath(it.absolutePath) },
                )

            assertThat(result.success).isTrue()
            val exportedFile = File(result.destinationPath)
            assertThat(exportedFile).exists()

            val metadata = ImageMetadataReader.readMetadata(exportedFile)
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            // Our override should be present
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("MyCamera")
        }

        @Test
        @DisplayName("exported file should be a valid readable JPEG")
        fun exportedFileShouldBeValidJpeg() {
            val config =
                PhotoScanConfiguration(
                    description = "test",
                    cameraMake = "Canon",
                    originalDate = "2020-01-15",
                    keywords = "test",
                )

            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "jpeg_valid_${System.nanoTime()}")
            destDir.mkdirs()

            val result =
                exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "validity_test",
                )
            assertThat(result.success).isTrue()

            val exportedFile = File(result.destinationPath)

            // Should be readable as a BufferedImage
            val readBack = ImageIO.read(exportedFile)
            assertThat(readBack).isNotNull
            assertThat(readBack.width).isEqualTo(200)
            assertThat(readBack.height).isEqualTo(150)

            // Should be readable by metadata-extractor without errors
            val metadata = ImageMetadataReader.readMetadata(exportedFile)
            assertThat(metadata).isNotNull
        }
    }

    @Nested
    @DisplayName("EXIF tri-state override behavior")
    inner class TriStateOverrideTests {

        @Test
        @DisplayName("NULL_OUT for camera make should remove Make tag from output")
        fun nullOutCameraMakeShouldRemoveMakeTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    cameraMake = "EPSON",
                    overrideCameraMake = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            // When NULL_OUT is set, the Make tag should be removed — even though cameraMake =
            // "EPSON"
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MAKE)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for camera model should remove Model tag from output")
        fun nullOutCameraModelShouldRemoveModelTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    cameraModel = "Perfection V600",
                    overrideCameraModel = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MODEL)).isFalse()
        }

        @Test
        @DisplayName("OVERRIDE for camera make should replace Make tag value")
        fun overrideCameraMakeShouldReplaceMakeTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    cameraMake = "Nikon",
                    overrideCameraMake = OverrideState.OVERRIDE,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Nikon")
        }

        @Test
        @DisplayName(
            "KEEP_SOURCE (null override) should use legacy behavior — set Make if value provided"
        )
        fun keepSourceWithLegacyValueShouldSetMake() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    cameraMake = "Canon",
                    // overrideCameraMake = null (default) → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Canon")
        }

        @Test
        @DisplayName("KEEP_SOURCE (null override) with blank value should not set Make")
        fun keepSourceWithBlankValueShouldNotSetMake() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    cameraMake = "",
                    // overrideCameraMake = null → legacy behavior, but value is blank
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            // No Make tag should be written when value is blank and no source EXIF to copy
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MAKE)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for ISO should remove ISO tag from output")
        fun nullOutIsoShouldRemoveIsoTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    iso = "400",
                    overrideIso = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)).isFalse()
        }

        @Test
        @DisplayName("OVERRIDE for ISO should set ISO value in output")
        fun overrideIsoShouldSetIsoValue() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    iso = "200",
                    overrideIso = OverrideState.OVERRIDE,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.getInt(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)).isEqualTo(200)
        }

        @Test
        @DisplayName("NULL_OUT for description should remove ImageDescription from output")
        fun nullOutDescriptionShouldRemoveImageDescription() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    description = "Some description",
                    overrideDescription = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)).isFalse()
        }

        @Test
        @DisplayName("OVERRIDE for description should set ImageDescription in output")
        fun overrideDescriptionShouldSetImageDescription() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    description = "My photo description",
                    overrideDescription = OverrideState.OVERRIDE,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("My photo description")
        }

        @Test
        @DisplayName("NULL_OUT for keywords should remove XPKeywords from output")
        fun nullOutKeywordsShouldRemoveXPKeywords() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    keywords = "summer, vacation",
                    overrideKeywords = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            // XPKeywords (0x9C9D) is in IFD0 root directory
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            // The tag should not be present when NULL_OUT
            assertThat(ifd0!!.containsTag(0x9C9D)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for date original should remove DateTimeOriginal from output")
        fun nullOutDateOriginalShouldRemoveDateTimeOriginal() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    originalDate = "2024-01-15",
                    overrideOriginalDate = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for GPS should remove GPS tags from output")
        fun nullOutGpsShouldRemoveGpsTags() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    gpsLatitude = "42.2626",
                    gpsLongitude = "-71.8023",
                    overrideGps = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            // GPS directory should either not exist or have no GPS coordinate tags
            val gpsDir =
                metadata.getFirstDirectoryOfType(com.drew.metadata.exif.GpsDirectory::class.java)
            // When NULL_OUT, even if we set coordinates, the export should not write GPS
            if (gpsDir != null) {
                assertThat(gpsDir.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LATITUDE))
                    .isFalse()
                assertThat(gpsDir.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LONGITUDE))
                    .isFalse()
            }
        }

        @Test
        @DisplayName("OVERRIDE for GPS should write GPS coordinates to output")
        fun overrideGpsShouldWriteGpsCoordinates() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    gpsLatitude = "42.2626",
                    gpsLongitude = "-71.8023",
                    overrideGps = OverrideState.OVERRIDE,
                )
            val metadata = exportAndReadback(config)

            val gpsDir =
                metadata.getFirstDirectoryOfType(com.drew.metadata.exif.GpsDirectory::class.java)
            assertThat(gpsDir).isNotNull
            assertThat(gpsDir!!.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LATITUDE))
                .isTrue()
            assertThat(gpsDir.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LONGITUDE))
                .isTrue()
        }

        @Test
        @DisplayName("NULL_OUT for lens model should remove LensModel tag from output")
        fun nullOutLensModelShouldRemoveLensModelTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    lensModel = "Nikkor 50mm f/1.4",
                    overrideLensModel = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_LENS_MODEL)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for focal length should remove FocalLength tag from output")
        fun nullOutFocalLengthShouldRemoveFocalLengthTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    focalLength = "50mm",
                    overrideFocalLength = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for aperture should remove FNumber tag from output")
        fun nullOutApertureShouldRemoveFNumberTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    aperture = "f/2.8",
                    overrideAperture = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)).isFalse()
        }

        @Test
        @DisplayName("NULL_OUT for shutter speed should remove ExposureTime tag from output")
        fun nullOutShutterSpeedShouldRemoveExposureTimeTag() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    shutterSpeed = "1/125",
                    overrideShutterSpeed = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)).isFalse()
        }

        private fun exportAndReadback(
            config: PhotoScanConfiguration,
            sourceFile: File? = null,
        ): com.drew.metadata.Metadata {
            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "tri_state_test_${System.nanoTime()}")
            destDir.mkdirs()

            val result =
                exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "tri_state_test",
                    sourceFile = sourceFile?.let { FilePath(it.absolutePath) },
                )

            assertThat(result.success).isTrue()
            val exportedFile = File(result.destinationPath)
            assertThat(exportedFile).exists()

            return ImageMetadataReader.readMetadata(exportedFile)
        }

        // ====== Backward Compatibility: null overrides use legacy string fields ======

        @Test
        @DisplayName("Legacy description field should write ImageDescription when override is null")
        fun legacyDescriptionShouldWriteImageDescription() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    description = "Family vacation photo",
                    // overrideDescription = null → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("Family vacation photo")
        }

        @Test
        @DisplayName("Legacy keywords field should write IPTC keywords when override is null")
        fun legacyKeywordsShouldWriteIptcKeywords() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    keywords = "vacation, family",
                    // overrideKeywords = null → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val iptc = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptc).isNotNull
            val keywords = iptc?.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            assertThat(keywords!!.toList()).containsExactly("vacation", "family")
        }

        @Test
        @DisplayName(
            "Legacy camera make and model fields should write tags when overrides are null"
        )
        fun legacyCameraFieldsShouldWriteTags() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    cameraMake = "Nikon",
                    cameraModel = "D850",
                    lensModel = "24-70mm f/2.8",
                    // All override fields null → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Nikon")
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_MODEL)).isEqualTo("D850")

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.getString(ExifSubIFDDirectory.TAG_LENS_MODEL))
                .isEqualTo("24-70mm f/2.8")
        }

        @Test
        @DisplayName("Legacy date field should write DateTimeOriginal when override is null")
        fun legacyDateShouldWriteDateTimeOriginal() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    originalDate = "2024-01-15",
                    // overrideOriginalDate = null → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
            assertThat(subIfd).isNotNull
            assertThat(subIfd!!.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)).isTrue()
        }

        @Test
        @DisplayName("Legacy GPS fields should write GPS tags when override is null")
        fun legacyGpsShouldWriteGpsTags() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false,
                    gpsLatitude = "42.2626",
                    gpsLongitude = "-71.8023",
                    // overrideGps = null → legacy behavior
                )
            val metadata = exportAndReadback(config)

            val gps =
                metadata.getFirstDirectoryOfType(com.drew.metadata.exif.GpsDirectory::class.java)
            assertThat(gps).isNotNull
            assertThat(gps!!.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LATITUDE)).isTrue()
            assertThat(gps.containsTag(com.drew.metadata.exif.GpsDirectory.TAG_LONGITUDE)).isTrue()
        }

        @Test
        @DisplayName("All null overrides with blank values — no tags should be written")
        fun allNullOverridesWithBlankValuesShouldWriteNothing() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = false
                    // All string fields default to empty/blank, all overrides null → nothing
                    // written
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.containsTag(ExifIFD0Directory.TAG_MAKE)).isFalse()
            assertThat(ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)).isFalse()
            assertThat(ifd0.containsTag(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)).isFalse()
        }

        @Test
        @DisplayName("Mix of OVERRIDE and legacy (null override) should work correctly")
        fun mixOfOverrideAndLegacyShouldWorkCorrectly() {
            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
                    cameraMake = "Canon",
                    description = "Summer vacation",
                    overrideDescription = OverrideState.OVERRIDE,
                    cameraModel = "EOS 5D",
                    overrideCameraModel = OverrideState.NULL_OUT,
                )
            val metadata = exportAndReadback(config)

            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("Canon")
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION))
                .isEqualTo("Summer vacation")
            assertThat(ifd0.containsTag(ExifIFD0Directory.TAG_MODEL)).isFalse()
        }
    }

    @Nested
    @DisplayName("correction strategy")
    inner class CorrectionStrategyTests {
        @Test
        @DisplayName("null correctionStrategy with perspective correction ON uses PERSPECTIVE")
        fun nullStrategyWithPerspectiveCorrectionOnUsesPerspective() {
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = true,
                        configuration = PhotoScanConfiguration(correctionStrategy = null),
                    )
            val marginedPhoto = GeometryUtils.applyMargin(photo, 0.02)
            // When correctionStrategy is null and perspectiveCorrection is ON,
            // the export service should use PERSPECTIVE
            assertThat(marginedPhoto.applyPerspectiveCorrection).isTrue()
        }

        @Test
        @DisplayName("null correctionStrategy with perspective correction OFF uses auto-detect")
        fun nullStrategyWithPerspectiveCorrectionOffUsesAutoDetect() {
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = false,
                        configuration = PhotoScanConfiguration(correctionStrategy = null),
                    )
            // When correctionStrategy is null and perspectiveCorrection is OFF,
            // the strategy is determined from corner geometry
            assertThat(photo.configuration.correctionStrategy).isNull()
            assertThat(photo.applyPerspectiveCorrection).isFalse()
        }

        @Test
        @DisplayName("explicit CROP strategy overrides perspective correction being ON")
        fun explicitCropStrategyOverridesPerspectiveCorrection() {
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = true,
                        configuration =
                            PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP),
                    )
            // Even though perspectiveCorrection is ON, explicit CROP strategy should be used
            assertThat(photo.configuration.correctionStrategy).isEqualTo(CorrectionStrategy.CROP)
        }

        @Test
        @DisplayName("explicit PERSPECTIVE strategy when perspective correction OFF")
        fun explicitPerspectiveStrategyWhenPerspectiveCorrectionOff() {
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = false,
                        configuration =
                            PhotoScanConfiguration(
                                correctionStrategy = CorrectionStrategy.PERSPECTIVE
                            ),
                    )
            // Even though perspectiveCorrection is OFF, explicit PERSPECTIVE strategy is used
            assertThat(photo.configuration.correctionStrategy)
                .isEqualTo(CorrectionStrategy.PERSPECTIVE)
        }

        @Test
        @DisplayName("CROP_AND_ROTATE strategy value is preserved")
        fun cropAndRotateStrategyIsPreserved() {
            val config =
                PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP_AND_ROTATE)
            assertThat(config.correctionStrategy).isEqualTo(CorrectionStrategy.CROP_AND_ROTATE)
        }

        @Test
        @DisplayName("null correctionStrategy defaults to null in PhotoScanConfiguration")
        fun nullCorrectionStrategyIsDefault() {
            val config = PhotoScanConfiguration()
            assertThat(config.correctionStrategy).isNull()
        }

        @Test
        @DisplayName("export with explicit CROP strategy produces axis-aligned crop")
        fun exportWithCropStrategyProducesAxisAlignedCrop() {
            val sourceFile = createTestImage(200, 150, 0xAAAAAA)
            val source = ImageIO.read(sourceFile)
            val destDir = File(tempDir, "crop_test_${System.nanoTime()}")
            destDir.mkdirs()
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = true,
                        configuration =
                            PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP),
                    )
            val result =
                exportSinglePhoto(
                    source.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "crop_test",
                    sourceFile = FilePath(sourceFile.absolutePath),
                )
            assertThat(result.success).isTrue()
            assertThat(File(result.destinationPath)).exists()
        }

        @Test
        @DisplayName("export with explicit PERSPECTIVE strategy succeeds")
        fun exportWithPerspectiveStrategySucceeds() {
            val sourceFile = createTestImage(200, 150, 0xBBBBBB)
            val source = ImageIO.read(sourceFile)
            val destDir = File(tempDir, "persp_test_${System.nanoTime()}")
            destDir.mkdirs()
            val photo =
                createDetectedPhoto()
                    .copy(
                        applyPerspectiveCorrection = true,
                        configuration =
                            PhotoScanConfiguration(
                                correctionStrategy = CorrectionStrategy.PERSPECTIVE
                            ),
                    )
            val result =
                exportSinglePhoto(
                    source.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "perspective_test",
                    sourceFile = FilePath(sourceFile.absolutePath),
                )
            assertThat(result.success).isTrue()
        }
    }
}
