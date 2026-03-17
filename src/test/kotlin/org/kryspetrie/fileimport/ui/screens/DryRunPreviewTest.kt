package org.kryspetrie.fileimport.ui.screens

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.kryspetrie.fileimport.domain.model.*
import org.kryspetrie.fileimport.domain.port.FileStructurePreview

@DisplayName("Dry Run Preview Logic")
class DryRunPreviewTest {

  @Nested
  @DisplayName("Reorganize preview filtering")
  inner class ReorganizePreviewFilteringTest {

    @Test
    @DisplayName("should filter mappings to only changed files")
    fun shouldFilterToChangedOnly() {
      val file1 = ImageFile(file = File("/photos/a.jpg"))
      val file2 = ImageFile(file = File("/photos/b.jpg"))
      val file3 = ImageFile(file = File("/photos/c.jpg"))

      val mappings =
          listOf(
              ReorganizeMapping(
                  file = file1,
                  currentPath = "/photos/a.jpg",
                  newPath = "/photos/2024/a.jpg",
                  newFileName = "a.jpg",
                  isChanged = true),
              ReorganizeMapping(
                  file = file2,
                  currentPath = "/photos/b.jpg",
                  newPath = "/photos/b.jpg",
                  newFileName = "b.jpg",
                  isChanged = false),
              ReorganizeMapping(
                  file = file3,
                  currentPath = "/photos/c.jpg",
                  newPath = "/photos/2024/c_renamed.jpg",
                  newFileName = "c_renamed.jpg",
                  isChanged = true))

      val changed = mappings.filter { it.isChanged }

      assertThat(changed).hasSize(2)
      assertThat(changed.map { it.file.fileName }).containsExactly("a.jpg", "c.jpg")
    }

    @Test
    @DisplayName("should produce empty list when no files changed")
    fun shouldProduceEmptyWhenNoChanges() {
      val file = ImageFile(file = File("/photos/a.jpg"))
      val mappings =
          listOf(
              ReorganizeMapping(
                  file = file,
                  currentPath = "/photos/a.jpg",
                  newPath = "/photos/a.jpg",
                  newFileName = "a.jpg",
                  isChanged = false))

      val changed = mappings.filter { it.isChanged }

      assertThat(changed).isEmpty()
    }

    @Test
    @DisplayName("should compute relative paths from root")
    fun shouldComputeRelativePaths() {
      val rootPath = "/photos"
      val mapping =
          ReorganizeMapping(
              file = ImageFile(file = File("/photos/subfolder/image.jpg")),
              currentPath = "/photos/subfolder/image.jpg",
              newPath = "/photos/2024/January/image.jpg",
              newFileName = "image.jpg",
              isChanged = true)

      val currentRel = mapping.currentPath.removePrefix(rootPath).removePrefix("/")
      val newRel = mapping.newPath.removePrefix(rootPath).removePrefix("/")

      assertThat(currentRel).isEqualTo("subfolder/image.jpg")
      assertThat(newRel).isEqualTo("2024/January/image.jpg")
    }

    @Test
    @DisplayName("should identify conflicts in preview")
    fun shouldIdentifyConflicts() {
      val file = ImageFile(file = File("/photos/a.jpg"))
      val mappings =
          listOf(
              ReorganizeMapping(
                  file = file,
                  currentPath = "/photos/a.jpg",
                  newPath = "/photos/2024/a.jpg",
                  newFileName = "a.jpg",
                  isChanged = true,
                  wouldConflict = true),
              ReorganizeMapping(
                  file = file,
                  currentPath = "/photos/b.jpg",
                  newPath = "/photos/2024/b.jpg",
                  newFileName = "b.jpg",
                  isChanged = true,
                  wouldConflict = false))

      val conflicts = mappings.filter { it.wouldConflict }

      assertThat(conflicts).hasSize(1)
      assertThat(conflicts.first().currentPath).isEqualTo("/photos/a.jpg")
    }
  }

  @Nested
  @DisplayName("Import preview structure")
  inner class ImportPreviewTest {

    @Test
    @DisplayName("should compute distinct source folders from previews")
    fun shouldComputeSourceFolders() {
      val previews =
          listOf(
              makePreview("/src/folderA/a.jpg", "/dest/2024/a.jpg", "/dest/2024"),
              makePreview("/src/folderA/b.jpg", "/dest/2024/b.jpg", "/dest/2024"),
              makePreview("/src/folderB/c.jpg", "/dest/2025/c.jpg", "/dest/2025"))

      val sourceFolders = previews.map { it.sourceFile.file.parent ?: "" }.distinct().sorted()

      assertThat(sourceFolders).hasSize(2)
      assertThat(sourceFolders).containsExactly("/src/folderA", "/src/folderB")
    }

    @Test
    @DisplayName("should compute distinct destination folders")
    fun shouldComputeDestFolders() {
      val previews =
          listOf(
              makePreview("/src/a.jpg", "/dest/2024/a.jpg", "/dest/2024"),
              makePreview("/src/b.jpg", "/dest/2024/b.jpg", "/dest/2024"),
              makePreview("/src/c.jpg", "/dest/2025/c.jpg", "/dest/2025"))

      val destFolders = previews.map { it.folderPath }.distinct().sorted()

      assertThat(destFolders).hasSize(2)
      assertThat(destFolders).containsExactly("/dest/2024", "/dest/2025")
    }

    @Test
    @DisplayName("should count conflicts in previews")
    fun shouldCountConflicts() {
      val previews =
          listOf(
              makePreview("/src/a.jpg", "/dest/a.jpg", "/dest", wouldConflict = true),
              makePreview("/src/b.jpg", "/dest/b.jpg", "/dest", wouldConflict = false),
              makePreview("/src/c.jpg", "/dest/c.jpg", "/dest", wouldConflict = true))

      val conflictCount = previews.count { it.wouldConflict }

      assertThat(conflictCount).isEqualTo(2)
    }

    @Test
    @DisplayName("should compute relative paths from source and destination roots")
    fun shouldComputeRelativePathsForPreview() {
      val sourcePath = "/media/camera"
      val destinationPath = "/photos/imported"

      val preview =
          makePreview(
              "/media/camera/DCIM/100CANON/IMG_001.jpg",
              "/photos/imported/2024/January/IMG_001.jpg",
              "/photos/imported/2024/January")

      val sourceRelative = preview.sourceFile.filePath.removePrefix(sourcePath).removePrefix("/")
      val destRelative = preview.destinationPath.removePrefix(destinationPath).removePrefix("/")

      assertThat(sourceRelative).isEqualTo("DCIM/100CANON/IMG_001.jpg")
      assertThat(destRelative).isEqualTo("2024/January/IMG_001.jpg")
    }

    @Test
    @DisplayName("should handle empty preview list")
    fun shouldHandleEmptyPreviews() {
      val previews = emptyList<FileStructurePreview>()

      val sourceFolders = previews.map { it.sourceFile.file.parent ?: "" }.distinct()
      val destFolders = previews.map { it.folderPath }.distinct()

      assertThat(sourceFolders).isEmpty()
      assertThat(destFolders).isEmpty()
    }
  }

  @Nested
  @DisplayName("ReorganizePreview summary")
  inner class PreviewSummaryTest {

    @Test
    @DisplayName("should correctly report counts")
    fun shouldReportCounts() {
      val preview =
          ReorganizePreview(
              mappings = emptyList(),
              totalFiles = 150,
              changedFiles = 42,
              conflictCount = 3,
              newFolderCount = 7)

      assertThat(preview.totalFiles).isEqualTo(150)
      assertThat(preview.changedFiles).isEqualTo(42)
      assertThat(preview.conflictCount).isEqualTo(3)
      assertThat(preview.newFolderCount).isEqualTo(7)
    }

    @Test
    @DisplayName("should indicate no changes needed")
    fun shouldIndicateNoChanges() {
      val preview =
          ReorganizePreview(
              mappings = emptyList(),
              totalFiles = 50,
              changedFiles = 0,
              conflictCount = 0,
              newFolderCount = 0)

      assertThat(preview.changedFiles).isEqualTo(0)
    }
  }

  private fun makePreview(
      sourcePath: String,
      destPath: String,
      folderPath: String,
      wouldConflict: Boolean = false
  ): FileStructurePreview {
    return FileStructurePreview(
        sourceFile = ImageFile(file = File(sourcePath)),
        destinationPath = destPath,
        folderPath = folderPath,
        fileName = File(destPath).name,
        wouldConflict = wouldConflict)
  }
}
