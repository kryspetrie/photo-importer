package org.kryspetrie.fileimport.domain.model

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@DisplayName("ReorganizeOperation models")
class ReorganizeOperationTest {

  @Test
  @DisplayName("ReorganizeMapping should derive relative paths")
  fun mappingShouldDeriveRelativePaths() {
    val file = File("/photos/2024/IMG_001.jpg")
    val imageFile = ImageFile(file = file)
    val mapping =
        ReorganizeMapping(
            file = imageFile,
            currentPath = "/photos/2024/IMG_001.jpg",
            newPath = "/photos/2024-01/IMG_001_renamed.jpg",
            newFileName = "IMG_001_renamed.jpg")

    assertThat(mapping.currentRelativePath).isEqualTo("IMG_001.jpg")
    assertThat(mapping.newRelativePath).isEqualTo("IMG_001_renamed.jpg")
  }

  @Test
  @DisplayName("ReorganizePreview should track changes")
  fun previewShouldTrackChanges() {
    val file = File("/photos/photo.jpg")
    val imageFile = ImageFile(file = file)
    val changed =
        ReorganizeMapping(
            file = imageFile,
            currentPath = "/a/photo.jpg",
            newPath = "/b/photo.jpg",
            newFileName = "photo.jpg",
            isChanged = true)
    val unchanged =
        ReorganizeMapping(
            file = imageFile,
            currentPath = "/c/photo.jpg",
            newPath = "/c/photo.jpg",
            newFileName = "photo.jpg",
            isChanged = false)
    val preview =
        ReorganizePreview(
            mappings = listOf(changed, unchanged),
            totalFiles = 2,
            changedFiles = 1,
            conflictCount = 0,
            newFolderCount = 1)

    assertThat(preview.totalFiles).isEqualTo(2)
    assertThat(preview.changedFiles).isEqualTo(1)
  }

  @Test
  @DisplayName("ReorganizeResult should report results")
  fun resultShouldReportResults() {
    val result =
        ReorganizeResult(
            movedCount = 5,
            renamedCount = 3,
            skippedCount = 1,
            errorCount = 2,
            errors = listOf("Error 1", "Error 2"),
            journalPath = "/journal.json")

    assertThat(result.movedCount).isEqualTo(5)
    assertThat(result.renamedCount).isEqualTo(3)
    assertThat(result.errors).hasSize(2)
    assertThat(result.journalPath).isNotNull()
  }

  @Test
  @DisplayName("ReorganizePhase should have all phases")
  fun phaseShouldHaveAllPhases() {
    assertThat(ReorganizePhase.entries)
        .containsExactly(
            ReorganizePhase.SCANNING,
            ReorganizePhase.PREVIEWING,
            ReorganizePhase.EXECUTING,
            ReorganizePhase.COMPLETE,
            ReorganizePhase.ROLLING_BACK)
  }

  @Test
  @DisplayName("JournalEntry should record paths")
  fun journalEntryShouldRecordPaths() {
    val entry = JournalEntry(originalPath = "/old/photo.jpg", newPath = "/new/photo.jpg")
    assertThat(entry.originalPath).isEqualTo("/old/photo.jpg")
    assertThat(entry.newPath).isEqualTo("/new/photo.jpg")
  }

  @Test
  @DisplayName("ReorganizeJournal should have timestamp")
  fun journalShouldHaveTimestamp() {
    val before = System.currentTimeMillis()
    val journal =
        ReorganizeJournal(rootFolder = "/photos", moves = listOf(JournalEntry("/a", "/b")))
    val after = System.currentTimeMillis()

    assertThat(journal.timestamp).isBetween(before, after)
    assertThat(journal.rootFolder).isEqualTo("/photos")
    assertThat(journal.moves).hasSize(1)
  }
}
