package org.kryspetrie.fileimport.application.export.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import com.petrielabs.metadataeditor.adapters.exiftool.ExifToolProcessRunner
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.application.metadata.MetadataEditJournalRepository
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.ImageRepositoryAdapter
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.testsupport.ExifToolTestSupport
import org.kryspetrie.fileimport.testsupport.RawPayloadTestSupport
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * End-to-end rotation tests through [MetadataEditService]: RAW files must receive orientation-only
 * updates without changing embedded image payloads.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RawMetadataRotationIntegrationTest {
    private lateinit var editService: MetadataEditService
    private lateinit var metadataService: MetadataWritingService

    @BeforeAll
    fun setUp() {
        ExifToolTestSupport.assumeExifToolAvailable()
        val fileSystem = FileSystemAdapter()
        val imageProcessing = AwtImageProcessingAdapter(fileSystem, PerspectiveCorrectionService())
        metadataService =
            ExifToolTestSupport.createMetadataWritingService(
                faceRegionTransformer = FaceRegionTransformer(),
                imageProcessing = imageProcessing,
            )
        val undoService =
            MetadataEditUndoService(
                MetadataEditJournalRepository(fileSystem),
                fileSystem,
                imageProcessing,
            )
        editService =
            MetadataEditService(
                metadataWritingService = metadataService,
                imageProcessing = imageProcessing,
                imageRepository = ImageRepositoryAdapter(TestDispatcherProvider()),
                fileSystem = fileSystem,
                undoService = undoService,
            )
    }

    @Test
    fun metadataWritingServiceSetsOrientationOnRawWithoutPixelChanges() {
        val path = ExifToolTestSupport.copySampleResource("samples/canon_eos_40d.CR2")
        val runner = ExifToolTestSupport.createProcessRunner()
        val beforeEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)

        metadataService.writeMetadataOnly(
            outputPath = FilePath(path.toString()),
            config = PhotoScanConfiguration(rotationDegrees = 90),
        )

        assertThat(readOrientationNumeric(path, runner)).isEqualTo(6)
        val afterEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)
        for ((tag, beforeBytes) in beforeEmbedded) {
            assertEquals(
                RawPayloadTestSupport.sha256(beforeBytes),
                RawPayloadTestSupport.sha256(afterEmbedded[tag] ?: ByteArray(0)),
                "Embedded $tag changed after orientation-only write",
            )
        }
    }

    @ParameterizedTest(name = "metadata-only rotation on {0}")
    @ValueSource(
        strings =
            [
                "samples/canon_eos_40d.CR2",
                "samples/nikon_d2h.NEF",
                "samples/sony_ilce_7s.ARW",
            ]
    )
    fun rotationUpdatesOrientationWithoutChangingPayload(resourcePath: String) {
        val path = ExifToolTestSupport.copySampleResource(resourcePath)
        val runner = ExifToolTestSupport.createProcessRunner()

        val beforeEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)
        val beforeStrip = RawPayloadTestSupport.extractLargestStrip(path, runner)
        assertTrue(beforeEmbedded.isNotEmpty() || beforeStrip != null) {
            "No image payload fingerprints available for $resourcePath"
        }

        runBlocking {
            val result =
                editService.saveFile(
                    file = path.toFile(),
                    config = PhotoScanConfiguration(rotationDegrees = 90),
                    outputMode = "OVERWRITE",
                    outputDirectory = "",
                )
            assertThat(result).isNotNull
        }

        assertThat(readOrientationNumeric(path, runner)).isEqualTo(6)

        val afterEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)
        val thumbnailTags = setOf("ThumbnailTIFF", "ThumbnailImage", "PreviewImage")
        for ((tag, beforeBytes) in beforeEmbedded) {
            if (tag in thumbnailTags) continue
            val afterBytes = afterEmbedded[tag]
            assertEquals(
                RawPayloadTestSupport.sha256(beforeBytes),
                RawPayloadTestSupport.sha256(afterBytes ?: ByteArray(0)),
                "Embedded $tag changed after orientation-only rotation for $resourcePath",
            )
        }

        if (beforeStrip != null) {
            val afterStrip = RawPayloadTestSupport.extractLargestStrip(path, runner)
            assertNotNull(afterStrip)
            assertEquals(
                RawPayloadTestSupport.sha256(beforeStrip),
                RawPayloadTestSupport.sha256(afterStrip!!),
                "Main sensor strip changed after orientation-only rotation for $resourcePath",
            )
        }
    }

    @Test
    fun jpegRotationResetsOrientationToNormalAfterPixelRotation() {
        val path = ExifToolTestSupport.copySampleResource("samples/exiftool_sample.jpg")
        val before = ImageIO.read(path.toFile()) ?: error("Could not read sample JPEG")
        val beforeWidth = before.width
        val beforeHeight = before.height

        runBlocking {
            editService.saveFile(
                file = path.toFile(),
                config = PhotoScanConfiguration(rotationDegrees = 90),
                outputMode = "OVERWRITE",
                outputDirectory = "",
            )
        }

        val after = ImageIO.read(path.toFile()) ?: error("Could not read rotated JPEG")
        assertThat(after.width).isEqualTo(beforeHeight)
        assertThat(after.height).isEqualTo(beforeWidth)

        val runner = ExifToolTestSupport.createProcessRunner()
        val orientation = readOrientationNumeric(path, runner)
        assertThat(orientation == null || orientation == 1)
            .describedAs("Expected normal orientation (1) after pixel rotation but got $orientation")
            .isTrue()
    }

    private fun readOrientationNumeric(path: Path, runner: ExifToolProcessRunner): Int? {
        val result = runner.run(listOf("-Orientation", "-n", "-s3", path.toString()))
        if (result.exitCode != 0 || result.output.isBlank()) return null
        return result.output.trim().toIntOrNull()
    }
}
