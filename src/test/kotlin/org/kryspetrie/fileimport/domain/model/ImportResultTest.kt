package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@DisplayName("ImportResult")
class ImportResultTest {

  @Test
  @DisplayName("should calculate duration")
  fun shouldCalculateDuration() {
    val result =
        ImportResult(
            totalFiles = 10,
            successCount = 10,
            errorCount = 0,
            duplicateCount = 0,
            skippedCount = 0,
            deletedSourceCount = 0,
            startTime = 1000,
            endTime = 5000)

    assertThat(result.duration).isEqualTo(4000)
  }

  @Test
  @DisplayName("should detect completion")
  fun shouldDetectCompletion() {
    assertThat(
            ImportResult(
                    totalFiles = 1,
                    successCount = 1,
                    errorCount = 0,
                    duplicateCount = 0,
                    skippedCount = 0,
                    deletedSourceCount = 0,
                    endTime = 1000)
                .isComplete)
        .isTrue()

    assertThat(
            ImportResult(
                    totalFiles = 1,
                    successCount = 0,
                    errorCount = 0,
                    duplicateCount = 0,
                    skippedCount = 0,
                    deletedSourceCount = 0)
                .isComplete)
        .isFalse()
  }

  @Test
  @DisplayName("should calculate success rate")
  fun shouldCalculateSuccessRate() {
    val result =
        ImportResult(
            totalFiles = 10,
            successCount = 8,
            errorCount = 2,
            duplicateCount = 0,
            skippedCount = 0,
            deletedSourceCount = 0)

    assertThat(result.successRate).isEqualTo(0.8f)
  }

  @Test
  @DisplayName("should handle zero files for success rate")
  fun shouldHandleZeroFiles() {
    val result =
        ImportResult(
            totalFiles = 0,
            successCount = 0,
            errorCount = 0,
            duplicateCount = 0,
            skippedCount = 0,
            deletedSourceCount = 0)

    assertThat(result.successRate).isEqualTo(0f)
  }

  @Nested
  @DisplayName("ImportProgress")
  inner class ImportProgressTest {
    @Test
    @DisplayName("should calculate progress percent")
    fun shouldCalculateProgress() {
      val progress = ImportProgress(currentIndex = 5, totalFiles = 10)
      assertThat(progress.progressPercent).isEqualTo(0.5f)
    }

    @Test
    @DisplayName("should calculate bytes progress")
    fun shouldCalculateBytesProgress() {
      val progress = ImportProgress(copiedBytes = 500, totalBytes = 1000)
      assertThat(progress.bytesProgressPercent).isEqualTo(0.5f)
    }

    @Test
    @DisplayName("should handle zero total safely")
    fun shouldHandleZeroTotal() {
      val progress = ImportProgress()
      assertThat(progress.progressPercent).isEqualTo(0f)
      assertThat(progress.bytesProgressPercent).isEqualTo(0f)
    }
  }
}
