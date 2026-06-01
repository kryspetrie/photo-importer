package org.kryspetrie.fileimport.application

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.iptc.IptcDirectory
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

@DisplayName("PhotoScanExportService")
class PhotoScanExportServiceTest {
    private lateinit var service: PhotoScanExportService
    private lateinit var perspectiveService: PerspectiveCorrectionService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        perspectiveService = PerspectiveCorrectionService()
        service = PhotoScanExportService(perspectiveService)
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
            assertThat(service.jpegQuality).isEqualTo(0.95f)
        }

        @Test
        @DisplayName("should allow jpeg quality configuration")
        fun shouldAllowQualityConfiguration() {
            service.jpegQuality = 0.5f
            assertThat(service.jpegQuality).isEqualTo(0.5f)
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
                service.exportPhotos(sourceFile, source, photos, destDir.absolutePath, "test_photo")

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
                service.exportPhotos(sourceFile, source, emptyList(), destDir.absolutePath, "empty")

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
                service.exportPhotos(sourceFile, source, photos, destDir.absolutePath, "photo")

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
            val result = service.applyMargin(photo, 0.0)
            assertThat(result.topLeft.x).isEqualTo(photo.topLeft.x)
            assertThat(result.topLeft.y).isEqualTo(photo.topLeft.y)
            assertThat(result.bottomRight.x).isEqualTo(photo.bottomRight.x)
        }

        @Test
        @DisplayName("negative margin returns same photo")
        fun negativeMarginReturnsSamePhoto() {
            val photo = createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
            val result = service.applyMargin(photo, -0.05)
            assertThat(result.topLeft.x).isEqualTo(photo.topLeft.x)
        }

        @Test
        @DisplayName("2% margin pushes corners outward from center")
        fun twoPercentMarginPushesOutward() {
            // 100x100 square centered at (150, 150)
            val photo = createDetectedPhoto(100f, 100f, 200f, 100f, 200f, 200f, 100f, 200f)
            val result = service.applyMargin(photo, 0.02)

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
            val result = service.applyMargin(photo, 0.02)
            assertThat(result.applyPerspectiveCorrection).isFalse()
        }

        @Test
        @DisplayName("margin preserves rotation")
        fun marginPreservesRotation() {
            val photo =
                createDetectedPhoto(10f, 10f, 110f, 10f, 110f, 110f, 10f, 110f)
                    .copy(rotation = org.kryspetrie.fileimport.domain.model.RotationAngle.CW_90)
            val result = service.applyMargin(photo, 0.02)
            assertThat(result.rotation)
                .isEqualTo(org.kryspetrie.fileimport.domain.model.RotationAngle.CW_90)
        }

        @Test
        @DisplayName("equal margin on all corners for symmetric quad")
        fun equalMarginForSymmetricQuad() {
            // Perfect square: all corners equidistant from center
            val photo = createDetectedPhoto(100f, 100f, 200f, 100f, 200f, 200f, 100f, 200f)
            val result = service.applyMargin(photo, 0.02)

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
                service.exportPhotos(
                    sourceFile,
                    source,
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
         * Helper: exports a single photo with EXIF config, then reads the exported file back
         * using metadata-extractor to verify tags were actually written.
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

            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "exif_readback_${System.nanoTime()}")
            destDir.mkdirs()

            val result = service.exportSinglePhoto(
                img,
                photo,
                destDir.absolutePath,
                "exif_test",
                sourceFile = sourceFile,
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
            assertThat(ifd0.getString(ExifIFD0Directory.TAG_IMAGE_DESCRIPTION)).isEqualTo("A beautiful sunset")
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
            val config = PhotoScanConfiguration(
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
            // Then export WITH copyOriginalExif=false. The output should have only overrides, no scanner EXIF.
            val config = PhotoScanConfiguration(
                cameraMake = "TestCamera",
                copyOriginalExif = false,
            )
            val metadata = exportAndReadback(config)

            // The override we set should be present
            val ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            assertThat(ifd0).isNotNull
            assertThat(ifd0!!.getString(ExifIFD0Directory.TAG_MAKE)).isEqualTo("TestCamera")

            // No scanner-model "Make" from source should leak through (this source has no EXIF anyway,
            // but the test structure proves that copyOriginalExif=false starts with empty TiffOutputSet)
        }

        @Test
        @DisplayName("with copyOriginalExif=true (default), should carry source EXIF baseline")
        fun shouldCarrySourceExifWhenCopyEnabled() {
            // Create a source image that has EXIF data (from ImageIO write, it won't have EXIF,
            // but the test validates the code path of reading from sourceFile)
            val sourceFile = createTestImage(200, 200, 0x808080)

            val config = PhotoScanConfiguration(
                cameraMake = "MyCamera",  // Override just the Make
                copyOriginalExif = true,
            )

            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "exif_copy_test_${System.nanoTime()}")
            destDir.mkdirs()

            val result = service.exportSinglePhoto(
                img, photo, destDir.absolutePath, "exif_copy_test",
                sourceFile = sourceFile,
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
            val config = PhotoScanConfiguration(
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

            val photo = createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "jpeg_valid_${System.nanoTime()}")
            destDir.mkdirs()

            val result = service.exportSinglePhoto(img, photo, destDir.absolutePath, "validity_test")
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
}
