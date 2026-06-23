package org.kryspetrie.fileimport.ui.screens.wizard

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ExportResult")
class ExportResultTest {

    private val testFile = File("/tmp/test.jpg")

    // ── ProcessedPhoto.isError ─────────────────────────────────────────────

    @Nested
    @DisplayName("ProcessedPhoto isError")
    inner class ProcessedPhotoIsErrorTest {

        @Test
        @DisplayName("returns true when outputPath starts with ERROR:")
        fun isErrorReturnsTrueForErrorPath() {
            val photo = ProcessedPhoto(testFile, "ERROR: Disk full", 0 to 0, emptyList())
            assertThat(photo.isError).isTrue()
        }

        @Test
        @DisplayName("returns false for normal output path")
        fun isErrorReturnsFalseForNormalPath() {
            val photo = ProcessedPhoto(testFile, "/output/photo.jpg", 800 to 600, emptyList())
            assertThat(photo.isError).isFalse()
        }

        @Test
        @DisplayName("returns false for empty output path")
        fun isErrorReturnsFalseForEmptyPath() {
            val photo = ProcessedPhoto(testFile, "", 0 to 0, emptyList())
            assertThat(photo.isError).isFalse()
        }
    }

    // ── ProcessedPhoto → ExportResult round-trip ────────────────────────────

    @Nested
    @DisplayName("ProcessedPhoto → ExportResult conversion")
    inner class ProcessedPhotoToExportResultTest {

        @Test
        @DisplayName("error photo converts to Failure")
        fun errorPhotoConvertsToFailure() {
            val photo = ProcessedPhoto(testFile, "ERROR: Disk full", 0 to 0, listOf("rotation"))
            val result = photo.toExportResult()

            assertThat(result).isInstanceOf(ExportResult.Failure::class.java)
            val failure = result as ExportResult.Failure
            assertThat(failure.originalFile).isEqualTo(testFile)
            assertThat(failure.errorMessage).isEqualTo("Disk full")
            assertThat(failure.correctionsApplied).containsExactly("rotation")
        }

        @Test
        @DisplayName("success photo converts to Success")
        fun successPhotoConvertsToSuccess() {
            val photo = ProcessedPhoto(testFile, "/output/photo.jpg", 800 to 600, listOf("rotation", "crop"))
            val result = photo.toExportResult()

            assertThat(result).isInstanceOf(ExportResult.Success::class.java)
            val success = result as ExportResult.Success
            assertThat(success.originalFile).isEqualTo(testFile)
            assertThat(success.outputPath).isEqualTo("/output/photo.jpg")
            assertThat(success.dimensions).isEqualTo(800 to 600)
            assertThat(success.correctionsApplied).containsExactly("rotation", "crop")
        }

        @Test
        @DisplayName("ERROR: prefix is stripped in Failure.errorMessage")
        fun errorPrefixStrippedInFailure() {
            val photo = ProcessedPhoto(testFile, "ERROR: Something went wrong", 0 to 0, emptyList())
            val result = photo.toExportResult() as ExportResult.Failure

            assertThat(result.errorMessage).isEqualTo("Something went wrong")
        }
    }

    // ── ExportResult → ProcessedPhoto round-trip ────────────────────────────

    @Nested
    @DisplayName("ExportResult → ProcessedPhoto conversion")
    inner class ExportResultToProcessedPhotoTest {

        @Test
        @DisplayName("Success converts to ProcessedPhoto with original path and dimensions")
        fun successConvertsToProcessedPhoto() {
            val success = ExportResult.Success(
                originalFile = testFile,
                outputPath = "/output/photo.jpg",
                dimensions = 800 to 600,
                correctionsApplied = listOf("crop"),
            )
            val photo = success.toProcessedPhoto()

            assertThat(photo.originalFile).isEqualTo(testFile)
            assertThat(photo.outputPath).isEqualTo("/output/photo.jpg")
            assertThat(photo.dimensions).isEqualTo(800 to 600)
            assertThat(photo.correctionsApplied).containsExactly("crop")
            assertThat(photo.isError).isFalse()
        }

        @Test
        @DisplayName("Failure converts to ProcessedPhoto with ERROR: prefixed path and zero dimensions")
        fun failureConvertsToProcessedPhoto() {
            val failure = ExportResult.Failure(
                originalFile = testFile,
                errorMessage = "Disk full",
                correctionsApplied = listOf("rotation"),
            )
            val photo = failure.toProcessedPhoto()

            assertThat(photo.originalFile).isEqualTo(testFile)
            assertThat(photo.outputPath).isEqualTo("ERROR: Disk full")
            assertThat(photo.dimensions).isEqualTo(0 to 0)
            assertThat(photo.correctionsApplied).containsExactly("rotation")
            assertThat(photo.isError).isTrue()
        }
    }

    // ── Full round-trip: ProcessedPhoto → ExportResult → ProcessedPhoto ────

    @Nested
    @DisplayName("Round-trip conversion")
    inner class RoundTripTest {

        @Test
        @DisplayName("success round-trip preserves all fields")
        fun successRoundTripPreservesAllFields() {
            val original = ProcessedPhoto(testFile, "/output/photo.jpg", 800 to 600, listOf("crop", "rotation"))
            val result = original.toExportResult()
            val restored = result.toProcessedPhoto()

            assertThat(restored.originalFile).isEqualTo(original.originalFile)
            assertThat(restored.outputPath).isEqualTo(original.outputPath)
            assertThat(restored.dimensions).isEqualTo(original.dimensions)
            assertThat(restored.correctionsApplied).isEqualTo(original.correctionsApplied)
        }

        @Test
        @DisplayName("failure round-trip preserves semantic meaning")
        fun failureRoundTripPreservesSemanticMeaning() {
            val original = ProcessedPhoto(testFile, "ERROR: Disk full", 0 to 0, listOf("rotation"))
            val result = original.toExportResult()
            val restored = result.toProcessedPhoto()

            assertThat(restored.isError).isTrue()
            assertThat(restored.originalFile).isEqualTo(original.originalFile)
            assertThat(restored.correctionsApplied).isEqualTo(original.correctionsApplied)
            // The error message is preserved through the round-trip
            assertThat(restored.outputPath).contains("ERROR:")
            assertThat(restored.outputPath).contains("Disk full")
        }
    }
}