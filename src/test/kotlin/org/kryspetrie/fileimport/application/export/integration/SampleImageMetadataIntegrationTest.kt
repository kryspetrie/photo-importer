package org.kryspetrie.fileimport.application.export.integration

import com.petrielabs.metadataeditor.domain.TagWritePolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kryspetrie.fileimport.application.export.MetadataWritingService
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.infrastructure.adapter.AwtImageProcessingAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.FileSystemAdapter
import org.kryspetrie.fileimport.infrastructure.photoscan.FaceRegionTransformer
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.testsupport.ExifToolTestSupport

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleImageMetadataIntegrationTest {
    private lateinit var metadataService: MetadataWritingService

    @BeforeAll
    fun setUp() {
        ExifToolTestSupport.assumeExifToolAvailable()
        metadataService =
            ExifToolTestSupport.createMetadataWritingService(
                FaceRegionTransformer(),
                AwtImageProcessingAdapter(FileSystemAdapter(), PerspectiveCorrectionService()),
            )
    }

    @ParameterizedTest(name = "read metadata from {0}")
    @ValueSource(
        strings =
            [
                "samples/canon_eos_40d.CR2",
                "samples/canon_eos_r6.CR3",
                "samples/nikon_d2h.NEF",
                "samples/sony_ilce_7s.ARW",
                "samples/fujifilm_finepix_s5000.RAF",
                "samples/fujifilm_x_t5.RAF",
                "samples/leica_m8.DNG",
                "samples/panasonic_dmc_fz28.RW2",
                "samples/olympus_c5050z.ORF",
                "samples/pentax_k_m.PEF",
                "samples/exiftool_sample.jpg",
            ]
    )
    fun readsMakeOrModel(resourcePath: String) {
        val path = ExifToolTestSupport.copySampleResource(resourcePath)
        val doc = ExifToolTestSupport.createMetadataEditor().read(path)
        assertTrue(doc.tags.isNotEmpty(), "expected tags for $resourcePath")

        val hasCameraIdentity =
            listOf("Make", "Model", "CameraModelName", "UniqueCameraModel").any { key ->
                doc.tags.any { it.name.equals(key, ignoreCase = true) && it.value.isNotBlank() }
            }
        assertTrue(
            hasCameraIdentity || resourcePath.endsWith(".jpg"),
            "expected camera identity tags in $resourcePath",
        )
    }

    @Test
    fun writesDescriptionOnJpegSample() {
        val path = ExifToolTestSupport.copySampleResource("samples/exiftool_sample.jpg")
        metadataService.writeMetadataOnly(
            outputPath = FilePath(path.toString()),
            config = PhotoScanConfiguration(description = "Petrie integration test"),
        )

        val doc = ExifToolTestSupport.createMetadataEditor().read(path)
        val description =
            doc.tags.first {
                it.name.equals("ImageDescription", ignoreCase = true) ||
                    it.name.equals("Description", ignoreCase = true)
            }
        assertThat(description.value).contains("Petrie integration test")
    }
}

@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetadataPassthroughIntegrationTest {
    private lateinit var metadataService: MetadataWritingService

    @BeforeAll
    fun setUp() {
        ExifToolTestSupport.assumeExifToolAvailable()
        metadataService =
            ExifToolTestSupport.createMetadataWritingService(
                FaceRegionTransformer(),
                AwtImageProcessingAdapter(FileSystemAdapter(), PerspectiveCorrectionService()),
            )
    }

    @ParameterizedTest(name = "passthrough on {0}")
    @ValueSource(
        strings =
            [
                "samples/canon_eos_40d.CR2",
                "samples/canon_eos_r6.CR3",
                "samples/nikon_d2h.NEF",
                "samples/sony_ilce_7s.ARW",
                "samples/fujifilm_finepix_s5000.RAF",
                "samples/fujifilm_x_t5.RAF",
                "samples/leica_m8.DNG",
                "samples/panasonic_dmc_fz28.RW2",
                "samples/olympus_c5050z.ORF",
                "samples/pentax_k_m.PEF",
            ]
    )
    fun writingDescriptionDoesNotClearProtectedTags(resourcePath: String) {
        val path = ExifToolTestSupport.copySampleResource(resourcePath)
        val editor = ExifToolTestSupport.createMetadataEditor()
        val before = editor.read(path)
        val protectedBefore = TagWritePolicy.passthroughFingerprint(before.tags)
        assertTrue(protectedBefore.isNotEmpty(), "expected protected tags in $resourcePath")

        metadataService.writeMetadataOnly(
            outputPath = FilePath(path.toString()),
            config = PhotoScanConfiguration(description = "Passthrough integration test"),
        )

        val after = editor.read(path)
        val protectedAfter = TagWritePolicy.passthroughFingerprint(after.tags)
        for ((key, value) in protectedBefore) {
            assertEquals(value, protectedAfter[key], "protected tag changed/cleared: $key")
        }
        assertTrue(
            after.tags.any {
                it.name.equals("ImageDescription", ignoreCase = true) &&
                    it.value.contains("Passthrough integration test")
            }
        )
    }
}
