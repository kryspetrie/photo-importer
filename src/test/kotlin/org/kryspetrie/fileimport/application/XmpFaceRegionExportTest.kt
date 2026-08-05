package org.kryspetrie.fileimport.application

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.iptc.IptcDirectory
import com.petrielabs.metadataeditor.domain.MetadataTag
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.testsupport.ExifToolTestSupport

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
        ExifToolTestSupport.assumeExifToolAvailable()
        perspectiveService = PerspectiveCorrectionService()
        val fileSystem = FileSystemAdapter()
        val imageProcessing = AwtImageProcessingAdapter(fileSystem, perspectiveService)
        service =
            PhotoScanExportService(
                perspectiveService,
                ExifToolTestSupport.createMetadataWritingService(
                    FaceRegionTransformer(),
                    imageProcessing,
                ),
                imageProcessing,
                fileSystem,
            )
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
            exportSinglePhoto(
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

    private fun exportAndGetFile(config: PhotoScanConfiguration): File {
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
            exportSinglePhoto(img.toProcessedImage(), photo, destDir.absolutePath, "xmp_bytes_test")

        assertThat(result.success).isTrue()
        val exportedFile = File(result.destinationPath)
        assertThat(exportedFile).exists()
        return exportedFile
    }

    private fun readExportedTags(config: PhotoScanConfiguration): List<MetadataTag> {
        val exportedFile = exportAndGetFile(config)
        return ExifToolTestSupport.createMetadataEditor()
            .read(Paths.get(exportedFile.absolutePath))
            .tags
    }

    private fun tagValues(tags: List<MetadataTag>, name: String): List<String> =
        tags.filter { it.name.equals(name, ignoreCase = true) }.map { it.value }

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

            val tags = readExportedTags(config)

            assertThat(tagValues(tags, "RegionName")).contains("Alice")
            assertThat(tagValues(tags, "RegionType")).contains("Face")
            assertThat(tags.any { it.name.contains("RegionArea", ignoreCase = true) }).isTrue()
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

            val tags = readExportedTags(config)

            assertThat(tagValues(tags, "RegionName").joinToString()).contains("Alice")
            assertThat(tagValues(tags, "RegionName").joinToString()).contains("Bob")
        }

        @Test
        @DisplayName("should NOT write MWG-RS XMP when no face regions are present")
        fun shouldNotWriteXmpWhenNoFaceRegions() {
            val config = PhotoScanConfiguration()
            val tags = readExportedTags(config)
            assertThat(tagValues(tags, "RegionName")).isEmpty()
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

            val tags = readExportedTags(config)
            assertThat(tagValues(tags, "RegionType")).contains("Pet")
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

            val tags = readExportedTags(config)
            val areaValues =
                tags.filter { it.name.contains("RegionArea", ignoreCase = true) }.map { it.value }
            assertThat(areaValues.any { it.contains("0.123456") || it.contains("0.123") }).isTrue()
            assertThat(areaValues.any { it.contains("0.789012") || it.contains("0.789") }).isTrue()
        }

        @Test
        @DisplayName("should preserve special characters in face names")
        fun shouldPreserveSpecialCharactersInFaceNames() {
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

            val tags = readExportedTags(config)
            assertThat(tagValues(tags, "RegionName")).contains("Alice & Bob")
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
            // ExifTool writes comma-separated keywords as one IPTC value
            val keywordParts = keywords!!.joinToString(", ").split(",").map { it.trim() }
            assertThat(keywordParts.count { it == "Alice" }).isEqualTo(1)
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
                exportSinglePhoto(
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
            val keywordText = keywords!!.joinToString(", ")
            assertThat(keywordText).contains("vacation")
            assertThat(keywordText).contains("beach")
            assertThat(keywordText).contains("Alice")
            assertThat(keywordText).contains("Bob")
        }
    }

    @Nested
    @DisplayName("MWG RegionInfo with pre-existing XMP")
    inner class MwgWithPreExistingXmp {

        @Test
        @DisplayName("writing face regions preserves prior non-MWG XMP tags from source")
        fun writingFacesPreservesExistingXmp() {
            // GIVEN — a JPEG that already has XMP-dc Description / Creator / Subject
            val sourceFile = File(tempDir, "pre_xmp_${System.nanoTime()}.jpg")
            val seed = BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
            ImageIO.write(seed, "jpg", sourceFile)
            val editor = ExifToolTestSupport.createMetadataEditor()
            val writeResult =
                editor.write(
                    com.petrielabs.metadataeditor.domain.WriteMetadataCommand(
                        path = Paths.get(sourceFile.absolutePath),
                        changes =
                            mapOf(
                                "XMP-dc:Description" to "Original album caption",
                                "XMP-dc:Creator" to "Original Photographer",
                                "XMP-dc:Subject" to "heirloom, family",
                            ),
                        allowProtectedWrites = false,
                    )
                )
            assertThat(writeResult)
                .isInstanceOf(
                    com.petrielabs.metadataeditor.domain.MetadataWriteResult.Success::class.java
                )

            val config =
                PhotoScanConfiguration(
                    copyOriginalExif = true,
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

            val img = BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = java.awt.Color(0x40, 0x80, 0xC0)
            g.fillRect(0, 0, 200, 150)
            g.dispose()

            val photo =
                createDetectedPhoto(0f, 0f, 200f, 0f, 200f, 150f, 0f, 150f)
                    .copy(applyPerspectiveCorrection = false, configuration = config)

            val destDir = File(tempDir, "mwg_merge_${System.nanoTime()}")
            destDir.mkdirs()

            // WHEN — export crops pixels and writes MWG RegionInfo (+ copies source XMP)
            val result =
                exportSinglePhoto(
                    img.toProcessedImage(),
                    photo,
                    destDir.absolutePath,
                    "mwg_merge",
                    sourceFile = FilePath(sourceFile.absolutePath),
                )

            // THEN
            assertThat(result.success).withFailMessage { result.error ?: "export failed" }.isTrue()
            val tags =
                ExifToolTestSupport.createMetadataEditor()
                    .read(Paths.get(result.destinationPath))
                    .tags

            assertThat(tagValues(tags, "RegionName")).contains("Alice")
            assertThat(tagValues(tags, "Description").joinToString())
                .contains("Original album caption")
            assertThat(tagValues(tags, "Creator").joinToString()).contains("Original Photographer")
            assertThat(tagValues(tags, "Subject").joinToString()).contains("heirloom")
        }
    }
}
