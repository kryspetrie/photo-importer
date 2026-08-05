package org.kryspetrie.fileimport.application.export.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.testsupport.ExifToolTestSupport
import org.kryspetrie.fileimport.testsupport.RawPayloadTestSupport

/**
 * Verifies RAW metadata writes through [MetadataWritingService] leave image payloads unchanged.
 * Sample files come from photo-metadata-editor's metadata-test-fixtures module.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RawMetadataWriteIntegrationTest {
    private lateinit var metadataService: MetadataWritingService

    data class Sample(val resourcePath: String, val expectMainStrip: Boolean)

    @BeforeAll
    fun setUp() {
        ExifToolTestSupport.assumeExifToolAvailable()
        metadataService =
            ExifToolTestSupport.createMetadataWritingService(
                faceRegionTransformer = FaceRegionTransformer(),
                imageProcessing =
                    org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter(
                        org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter(),
                        org.kryspetrie.fileimport.infrastructure.photoscan
                            .PerspectiveCorrectionService(),
                    ),
            )
    }

    fun samples() =
        listOf(
            Sample("samples/canon_eos_40d.CR2", expectMainStrip = true),
            Sample("samples/canon_eos_r6.CR3", expectMainStrip = false),
            Sample("samples/nikon_d2h.NEF", expectMainStrip = true),
            Sample("samples/sony_ilce_7s.ARW", expectMainStrip = true),
            Sample("samples/fujifilm_finepix_s5000.RAF", expectMainStrip = false),
            Sample("samples/fujifilm_x_t5.RAF", expectMainStrip = true),
            Sample("samples/leica_m8.DNG", expectMainStrip = false),
            Sample("samples/panasonic_dmc_fz28.RW2", expectMainStrip = false),
            Sample("samples/olympus_c5050z.ORF", expectMainStrip = false),
            Sample("samples/pentax_k_m.PEF", expectMainStrip = true),
        )

    @ParameterizedTest(name = "safe write on {0}")
    @MethodSource("samples")
    fun writesMetadataWithoutChangingImagePayload(sample: Sample) {
        val path = ExifToolTestSupport.copySampleResource(sample.resourcePath)
        val runner = ExifToolTestSupport.createProcessRunner()

        val beforeEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)
        val beforeStrip = RawPayloadTestSupport.extractLargestStrip(path, runner)
        val beforeJpegPixels =
            beforeEmbedded.mapValues { (_, bytes) ->
                bytes.takeIf(RawPayloadTestSupport::isJpeg)?.let {
                    RawPayloadTestSupport.jpegPixelsOnly(it, runner)
                }
            }

        assumeTrue(beforeEmbedded.isNotEmpty() || beforeStrip != null) {
            "No image payload fingerprints available for ${sample.resourcePath}"
        }

        val config =
            PhotoScanConfiguration(
                description = "RAW integration test",
                keywords = "integration-test",
                faceRegions =
                    listOf(FaceRegion(name = "Test Face", x = 0.25, y = 0.25, w = 0.2, h = 0.3)),
            )

        metadataService.writeMetadataOnly(
            outputPath = FilePath(path.toString()),
            config = config,
            preRotationWidth = 6000,
            preRotationHeight = 4000,
        )

        val editor = ExifToolTestSupport.createMetadataEditor()
        val doc = editor.read(path)
        assertTrue(
            doc.tags.any {
                it.name.equals("ImageDescription", ignoreCase = true) &&
                    it.value.contains("RAW integration test")
            },
            "Description not updated for ${sample.resourcePath}",
        )
        assertTrue(
            doc.tags.any {
                it.name.equals("RegionName", ignoreCase = true) && it.value == "Test Face"
            },
            "Face RegionName missing for ${sample.resourcePath}",
        )

        val afterEmbedded = RawPayloadTestSupport.extractEmbeddedPayloads(path, runner)
        for ((tag, beforeBytes) in beforeEmbedded) {
            val afterBytes = afterEmbedded[tag]
            if (beforeBytes.contentEquals(afterBytes)) continue

            val beforePixels = beforeJpegPixels[tag]
            if (
                beforePixels != null &&
                    afterBytes != null &&
                    RawPayloadTestSupport.isJpeg(afterBytes)
            ) {
                val afterPixels = RawPayloadTestSupport.jpegPixelsOnly(afterBytes, runner)
                assertEquals(
                    RawPayloadTestSupport.sha256(beforePixels),
                    RawPayloadTestSupport.sha256(afterPixels),
                    "Embedded $tag pixel bitstream changed for ${sample.resourcePath}",
                )
            } else {
                assertEquals(
                    RawPayloadTestSupport.sha256(beforeBytes),
                    RawPayloadTestSupport.sha256(afterBytes ?: ByteArray(0)),
                    "Embedded $tag changed for ${sample.resourcePath}",
                )
            }
        }

        if (sample.expectMainStrip) {
            assertNotNull(beforeStrip, "Expected main strip for ${sample.resourcePath}")
            val afterStrip = RawPayloadTestSupport.extractLargestStrip(path, runner)
            assertNotNull(afterStrip, "Missing main strip after write for ${sample.resourcePath}")
            assertEquals(
                RawPayloadTestSupport.sha256(beforeStrip!!),
                RawPayloadTestSupport.sha256(afterStrip!!),
                "Main sensor strip changed for ${sample.resourcePath}",
            )
        }
    }
}
