package org.kryspetrie.fileimport.infrastructure.download

import java.net.HttpURLConnection
import java.net.URL
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.application.TestDispatcherProvider
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort

/**
 * Tests for [HuggingFaceModelDownloadAdapter].
 *
 * Verifies model metadata, URL correctness (integration), and zip extraction.
 */
class HuggingFaceModelDownloadAdapterTest {

    private val testDispatcherProvider = TestDispatcherProvider()
    private val adapter = HuggingFaceModelDownloadAdapter(testDispatcherProvider)

    // ── Metadata Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Model metadata")
    inner class ModelMetadataTests {

        @Test
        @DisplayName("orientation model metadata has correct ID and name")
        fun orientationModelMetadata() {
            val models = adapter.availableForDownload()
            val orientationModel = models.find { it.id == ModelDownloadPort.ORIENTATION_MODEL_ID }

            assertThat(orientationModel).isNotNull()
            assertThat(orientationModel!!.name).contains("Orientation")
        }

        @Test
        @DisplayName("face embedding model metadata has correct ID and name")
        fun faceEmbeddingModelMetadata() {
            val models = adapter.availableForDownload()
            val embeddingModel = models.find { it.id == ModelDownloadPort.FACE_EMBEDDING_MODEL_ID }

            assertThat(embeddingModel).isNotNull()
            assertThat(embeddingModel!!.name).contains("Embedding")
        }

        @Test
        @DisplayName("bundled models are reported as downloaded")
        fun bundledModelsAreDownloaded() {
            assertThat(adapter.isModelDownloaded(ModelDownloadPort.FACE_MODEL_ID)).isTrue()
        }

        @Test
        @DisplayName("non-bundled models are not downloaded when file doesn't exist")
        fun nonBundledModelsNotDownloaded() {
            assertThat(adapter.isModelDownloaded(ModelDownloadPort.ORIENTATION_MODEL_ID)).isFalse()
            assertThat(adapter.isModelDownloaded(ModelDownloadPort.FACE_EMBEDDING_MODEL_ID)).isFalse()
        }

        @Test
        @DisplayName("model name contains size for downloadable models")
        fun modelNameContainsSize() {
            val name = adapter.modelName(ModelDownloadPort.ORIENTATION_MODEL_ID)
            assertThat(name).contains("MB")
        }

        @Test
        @DisplayName("delete bundled model returns false")
        fun deleteBundledModelReturnsFalse() {
            assertThat(adapter.deleteModel(ModelDownloadPort.FACE_MODEL_ID)).isFalse()
        }

        @Test
        @DisplayName("delete non-existent model returns false")
        fun deleteNonExistentModelReturnsFalse() {
            assertThat(adapter.deleteModel(ModelDownloadPort.ORIENTATION_MODEL_ID)).isFalse()
        }
    }

    // ── URL Correctness Integration Tests ───────────────────────────────

    @Nested
    @DisplayName("Download URL correctness")
    inner class UrlCorrectnessTests {

        @Test
        @DisplayName("orientation model URL resolves to redirect or OK (not 404)")
        fun orientationModelUrlResolves() {
            val url = URL(
                "https://huggingface.co/Chuckame/deep-image-orientation-angle-detection" +
                    "/resolve/main/deep-image-orientation-angle-detection.onnx"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false

            try {
                val responseCode = connection.responseCode
                val acceptable = responseCode == HttpURLConnection.HTTP_OK ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307
                assertThat(acceptable)
                    .withFailMessage("Expected 302 (redirect) or 200, got $responseCode")
                    .isTrue()
            } finally {
                connection.disconnect()
            }
        }

        @Test
        @DisplayName("old orientation model URL (model.onnx) returns 404")
        fun oldOrientationModelUrlReturns404() {
            // Verify the old incorrect URL returns 404, confirming the fix was needed
            val url = URL(
                "https://huggingface.co/Chuckame/deep-image-orientation-angle-detection" +
                    "/resolve/main/model.onnx"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false

            try {
                val responseCode = connection.responseCode
                assertThat(responseCode).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND)
            } finally {
                connection.disconnect()
            }
        }

        @Test
        @DisplayName("face embedding model URL (Hailo S3) resolves to 200")
        fun faceEmbeddingModelUrlResolves() {
            val url = URL(
                "https://hailo-model-zoo.s3.eu-west-2.amazonaws.com/FaceRecognition/" +
                    "arcface/arcface_mobilefacenet/pretrained/2022-08-24/arcface_mobilefacenet.zip"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true

            try {
                val responseCode = connection.responseCode
                assertThat(responseCode).isEqualTo(HttpURLConnection.HTTP_OK)
            } finally {
                connection.disconnect()
            }
        }
    }

    // ── Zip Extraction Tests ────────────────────────────────────────────

    @Nested
    @DisplayName("Zip extraction")
    inner class ZipExtractionTests {

        @Test
        @DisplayName("extractZipEntry extracts the correct file from a zip archive")
        fun extractZipEntryExtractsCorrectFile() {
            // Create a minimal test zip with an entry named "mbf.onnx"
            val tempDir = System.getProperty("java.io.tmpdir")
            val zipFile = java.io.File(tempDir, "test-model-${System.currentTimeMillis()}.zip")
            val targetFile = java.io.File(tempDir, "test-extracted-${System.currentTimeMillis()}.onnx")

            try {
                // Create a zip file with an entry "mbf.onnx" containing test data
                java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                    val entry = java.util.zip.ZipEntry("mbf.onnx")
                    zipOut.putNextEntry(entry)
                    zipOut.write("fake onnx model data for testing".toByteArray())
                    zipOut.closeEntry()
                }

                // Extract the entry
                adapter.extractZipEntry(zipFile, "mbf.onnx", targetFile)

                // Verify the extracted file exists and has the correct content
                assertThat(targetFile.exists()).isTrue()
                assertThat(targetFile.readText()).isEqualTo("fake onnx model data for testing")
            } finally {
                zipFile.delete()
                targetFile.delete()
            }
        }

        @Test
        @DisplayName("extractZipEntry throws when entry name not found in zip")
        fun extractZipEntryThrowsOnMissingEntry() {
            val tempDir = System.getProperty("java.io.tmpdir")
            val zipFile = java.io.File(tempDir, "test-model-missing-${System.currentTimeMillis()}.zip")
            val targetFile = java.io.File(tempDir, "test-extracted-missing-${System.currentTimeMillis()}.onnx")

            try {
                // Create a zip file with a different entry name
                java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                    val entry = java.util.zip.ZipEntry("other_model.onnx")
                    zipOut.putNextEntry(entry)
                    zipOut.write("other data".toByteArray())
                    zipOut.closeEntry()
                }

                // Attempt to extract a non-existent entry
                var caught = false
                try {
                    adapter.extractZipEntry(zipFile, "mbf.onnx", targetFile)
                } catch (e: IllegalStateException) {
                    caught = true
                    assertThat(e.message).contains("not found in zip archive")
                }
                assertThat(caught).isTrue()
            } finally {
                zipFile.delete()
                targetFile.delete()
            }
        }

        @Test
        @DisplayName("extractZipEntry throws when zipEntryName is null")
        fun extractZipEntryThrowsOnNullEntryName() {
            val tempDir = System.getProperty("java.io.tmpdir")
            val zipFile = java.io.File(tempDir, "test-model-null-${System.currentTimeMillis()}.zip")
            val targetFile = java.io.File(tempDir, "test-extracted-null-${System.currentTimeMillis()}.onnx")

            try {
                // Create a minimal zip file
                java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                    val entry = java.util.zip.ZipEntry("dummy.onnx")
                    zipOut.putNextEntry(entry)
                    zipOut.write("data".toByteArray())
                    zipOut.closeEntry()
                }

                var caught = false
                try {
                    adapter.extractZipEntry(zipFile, null, targetFile)
                } catch (e: IllegalStateException) {
                    caught = true
                    assertThat(e.message).contains("zipEntryName must be specified")
                }
                assertThat(caught).isTrue()
            } finally {
                zipFile.delete()
                targetFile.delete()
            }
        }
    }
}