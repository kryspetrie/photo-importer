package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@DisplayName("ImportHistoryEntry")
class ImportHistoryTest {

  @Test
  @DisplayName("should have unique IDs")
  fun shouldHaveUniqueIds() {
    val e1 =
        ImportHistoryEntry(
            sourcePath = "/src",
            destinationPath = "/dest",
            totalFiles = 1,
            successCount = 1,
            errorCount = 0,
            skippedCount = 0,
            duplicateCount = 0)
    val e2 =
        ImportHistoryEntry(
            sourcePath = "/src",
            destinationPath = "/dest",
            totalFiles = 1,
            successCount = 1,
            errorCount = 0,
            skippedCount = 0,
            duplicateCount = 0)

    assertThat(e1.id).isNotEqualTo(e2.id)
  }

  @Test
  @DisplayName("should have auto-generated timestamp")
  fun shouldHaveTimestamp() {
    val before = System.currentTimeMillis()
    val entry =
        ImportHistoryEntry(
            sourcePath = "/src",
            destinationPath = "/dest",
            totalFiles = 10,
            successCount = 10,
            errorCount = 0,
            skippedCount = 0,
            duplicateCount = 0)
    val after = System.currentTimeMillis()

    assertThat(entry.timestamp).isBetween(before, after)
  }

  @Test
  @DisplayName("should store all fields correctly")
  fun shouldStoreAllFields() {
    val entry =
        ImportHistoryEntry(
            sourcePath = "/camera/dcim",
            destinationPath = "/photos/2024",
            profileName = "Canon Camera",
            totalFiles = 100,
            successCount = 95,
            errorCount = 3,
            skippedCount = 2,
            duplicateCount = 5,
            totalBytes = 1024 * 1024 * 500L,
            durationMs = 30000)

    assertThat(entry.sourcePath).isEqualTo("/camera/dcim")
    assertThat(entry.destinationPath).isEqualTo("/photos/2024")
    assertThat(entry.profileName).isEqualTo("Canon Camera")
    assertThat(entry.totalFiles).isEqualTo(100)
    assertThat(entry.successCount).isEqualTo(95)
    assertThat(entry.errorCount).isEqualTo(3)
    assertThat(entry.skippedCount).isEqualTo(2)
    assertThat(entry.duplicateCount).isEqualTo(5)
    assertThat(entry.totalBytes).isEqualTo(524288000L)
    assertThat(entry.durationMs).isEqualTo(30000)
  }
}
