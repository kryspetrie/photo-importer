package org.kryspetrie.fileimport.application

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.iptc.IptcDirectory
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.apache.commons.imaging.Imaging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage

/**
 * Integration tests for XMP face region (MWG-RS) writing in PhotoScanExportService.
 *
 * Verifies that face regions are written as XMP metadata into exported JPEG files, and that
 * subjects are written as IPTC keywords alongside face region data.
 */
@DisplayName("XMP Face Region Export")
class XmpFaceRegionExportTest {

    private lateinit var service: PhotoScanExportService
    private lateinit var perspectiveService: PerspectiveCorrectionService

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        perspectiveService = PerspectiveCorrectionService()
        service = PhotoScanExportService(perspectiveService, FaceRegionTransformer())
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

        val destDir = File(tempDir, "xmp_test_${System.nanoTime()}")
        destDir.mkdirs()

        val result =
            service.exportSinglePhoto(
                img.toProcessedImage(),
                photo,
                destDir.absolutePath,
                "xmp_face_test",
                sourceFile = sourceFile?.let { FilePath(it.absolutePath) },
            )

        assertThat(result.success).isTrue()
        val exportedFile = File(result.destinationPath)
        assertThat(exportedFile).exists()

        return ImageMetadataReader.readMetadata(exportedFile)
    }

    private fun exportAndGetBytes(config: PhotoScanConfiguration): ByteArray {
        val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x40, 0x80, 0xC0)
        g.fillRect(0, 0, 200, 150)
        g.dispose()

        val photo =
            createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                .copy(applyPerspectiveCorrection = false, configuration = config)

        val destDir = File(tempDir, "xmp_bytes_test_${System.nanoTime()}")
        destDir.mkdirs()

        val result =
            service.exportSinglePhoto(
                img.toProcessedImage(),
                photo,
                destDir.absolutePath,
                "xmp_bytes_test",
            )

        assertThat(result.success).isTrue()
        val exportedFile = File(result.destinationPath)
        assertThat(exportedFile).exists()

        return exportedFile.readBytes()
    }

    @Nested
    @DisplayName("XMP face region writing")
    inner class XmpFaceRegionWriting {

        @Test
        @DisplayName("should write XMP data when faceRegions are present")
        fun shouldWriteXmpWhenFaceRegionsPresent() {
            val config =
                PhotoScanConfiguration(
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice",
                                type = "Face",
                                x = 0.3,
                                y = 0.4,
                                w = 0.15,
                                h = 0.20,
                            )
                        )
                )

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            assertThat(xmpData).isNotNull()
            assertThat(xmpData).contains("Alice")
            assertThat(xmpData).contains("mwg-rs")
            assertThat(xmpData).contains("0.300000")
            assertThat(xmpData).contains("0.400000")
        }

        @Test
        @DisplayName("should write multiple face regions in XMP")
        fun shouldWriteMultipleFaceRegions() {
            val config =
                PhotoScanConfiguration(
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice",
                                type = "Face",
                                x = 0.3,
                                y = 0.4,
                                w = 0.15,
                                h = 0.20,
                            ),
                            FaceRegion(
                                name = "Bob",
                                type = "Face",
                                x = 0.7,
                                y = 0.5,
                                w = 0.15,
                                h = 0.20,
                            ),
                        )
                )

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            assertThat(xmpData).isNotNull()
            assertThat(xmpData).contains("Alice")
            assertThat(xmpData).contains("Bob")
            assertThat(xmpData).contains("0.700000")
        }

        @Test
        @DisplayName("should NOT write MWG-RS XMP when no face regions are present")
        fun shouldNotWriteXmpWhenNoFaceRegions() {
            val config = PhotoScanConfiguration()

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            // No MWG-RS data should be written when there are no face regions
            // XMP may be null or not contain mwg-rs
            if (xmpData != null) {
                assertThat(xmpData).doesNotContain("mwg-rs")
            }
            // This is fine — null means no XMP was written, which is correct
        }

        @Test
        @DisplayName("should write face region type in XMP")
        fun shouldWriteFaceRegionType() {
            val config =
                PhotoScanConfiguration(
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Buddy",
                                type = "Pet",
                                x = 0.5,
                                y = 0.5,
                                w = 0.2,
                                h = 0.3,
                            )
                        )
                )

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            assertThat(xmpData).isNotNull()
            assertThat(xmpData).contains("Pet")
        }

        @Test
        @DisplayName("should include normalized coordinate format in XMP")
        fun shouldIncludeNormalizedCoordinates() {
            val config =
                PhotoScanConfiguration(
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Carol",
                                type = "Face",
                                x = 0.123456,
                                y = 0.789012,
                                w = 0.15,
                                h = 0.20,
                            )
                        )
                )

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            assertThat(xmpData).isNotNull()
            assertThat(xmpData).contains("0.123456")
            assertThat(xmpData).contains("0.789012")
            assertThat(xmpData).contains("normalized")
        }

        @Test
        @DisplayName("should escape special XML characters in face names")
        fun shouldEscapeXmlInFaceNames() {
            val config =
                PhotoScanConfiguration(
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice & Bob",
                                type = "Face",
                                x = 0.5,
                                y = 0.5,
                                w = 0.15,
                                h = 0.20,
                            )
                        )
                )

            val exportedBytes = exportAndGetBytes(config)
            val xmpData = Imaging.getXmpXml(exportedBytes)

            assertThat(xmpData).isNotNull()
            // The XML should have escaped the ampersand
            assertThat(xmpData).contains("Alice &amp; Bob")
        }
    }

    @Nested
    @DisplayName("IPTC keywords with face regions")
    inner class IptcKeywordsWithFaces {

        @Test
        @DisplayName("should write face region names as IPTC keywords")
        fun shouldWriteFaceNamesAsIptcKeywords() {
            val config =
                PhotoScanConfiguration(
                    subjects = "Alice",
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice",
                                type = "Face",
                                x = 0.3,
                                y = 0.4,
                                w = 0.15,
                                h = 0.20,
                            )
                        ),
                )

            val metadata = exportAndReadback(config)

            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptcDir).isNotNull
            val keywords = iptcDir!!.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            assertThat(keywords!!.toList()).contains("Alice")
        }

        @Test
        @DisplayName("should not duplicate face names in IPTC keywords if already in keywords")
        fun shouldNotDuplicateFaceNamesInKeywords() {
            val config =
                PhotoScanConfiguration(
                    keywords = "vacation, Alice",
                    subjects = "Alice",
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice",
                                type = "Face",
                                x = 0.3,
                                y = 0.4,
                                w = 0.15,
                                h = 0.20,
                            )
                        ),
                )

            val metadata = exportAndReadback(config)

            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptcDir).isNotNull
            val keywords = iptcDir!!.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            // Alice should appear exactly once (not duplicated)
            val aliceCount = keywords!!.toList().count { it == "Alice" }
            assertThat(aliceCount).isEqualTo(1)
        }

        @Test
        @DisplayName("should export valid JPEG when face regions and keywords are both present")
        fun shouldExportValidJpegWithFacesAndKeywords() {
            val config =
                PhotoScanConfiguration(
                    description = "Family photo",
                    keywords = "vacation, beach",
                    subjects = "Alice, Bob",
                    faceRegions =
                        listOf(
                            FaceRegion(
                                name = "Alice",
                                type = "Face",
                                x = 0.3,
                                y = 0.4,
                                w = 0.15,
                                h = 0.20,
                            ),
                            FaceRegion(
                                name = "Bob",
                                type = "Face",
                                x = 0.7,
                                y = 0.5,
                                w = 0.15,
                                h = 0.20,
                            ),
                        ),
                )

            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "full_export_test_${System.nanoTime()}")
            destDir.mkdirs()

            val result =
                service.exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "full_export",
                )

            assertThat(result.success).isTrue()
            val exportedFile = File(result.destinationPath)
            assertThat(exportedFile).exists()

            // Should be readable as a valid JPEG
            val readBack = ImageIO.read(exportedFile)
            assertThat(readBack).isNotNull()
            assertThat(readBack.width).isEqualTo(200)
            assertThat(readBack.height).isEqualTo(150)

            // Should be readable by metadata-extractor
            val metadata = ImageMetadataReader.readMetadata(exportedFile)
            assertThat(metadata).isNotNull()

            // IPTC keywords should include vacation, beach, Alice, Bob
            val iptcDir = metadata.getFirstDirectoryOfType(IptcDirectory::class.java)
            assertThat(iptcDir).isNotNull
            val keywords = iptcDir!!.getStringArray(IptcDirectory.TAG_KEYWORDS)
            assertThat(keywords).isNotNull
            assertThat(keywords!!.toList()).contains("vacation", "beach", "Alice", "Bob")
        }
    }
}
