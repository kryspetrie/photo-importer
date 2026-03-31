package org.kryspetrie.fileimport.domain.model

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PhotoScanState")
class PhotoScanStateTest {
  private lateinit var state: PhotoScanState

  @BeforeEach
  fun setup() {
    state = PhotoScanState()
  }

  private fun createDetectedPhoto(
      id: String = "test-id",
      tlX: Float = 0f,
      tlY: Float = 0f,
      trX: Float = 100f,
      trY: Float = 0f,
      brX: Float = 100f,
      brY: Float = 100f,
      blX: Float = 0f,
      blY: Float = 100f
  ): DetectedPhoto {
    return DetectedPhoto(
        id = id,
        topLeft = PhotoCorner(tlX, tlY),
        topRight = PhotoCorner(trX, trY),
        bottomRight = PhotoCorner(brX, brY),
        bottomLeft = PhotoCorner(blX, blY))
  }

  private fun createScannedImage(
      id: String = "scan-1",
      file: File = File("/test/image.jpg"),
      detectedPhotos: List<DetectedPhoto> = emptyList()
  ): ScannedImage {
    return ScannedImage(id = id, file = file, detectedPhotos = detectedPhotos)
  }

  @Nested
  @DisplayName("initialization")
  inner class Initialization {
    @Test
    @DisplayName("should start with empty images list")
    fun shouldStartWithEmptyImages() {
      assertThat(state.images.value).isEmpty()
    }

    @Test
    @DisplayName("should start at index 0")
    fun shouldStartAtIndexZero() {
      assertThat(state.currentIndex.value).isEqualTo(0)
    }

    @Test
    @DisplayName("should start in detecting step")
    fun shouldStartInDetectingStep() {
      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.DETECTING)
    }

    @Test
    @DisplayName("should have null selected photo initially")
    fun shouldHaveNullSelectedPhoto() {
      assertThat(state.selectedPhotoId.value).isNull()
    }

    @Test
    @DisplayName("should have null selected corner initially")
    fun shouldHaveNullSelectedCorner() {
      assertThat(state.selectedCorner.value).isNull()
    }

    @Test
    @DisplayName("should have zero export progress")
    fun shouldHaveZeroExportProgress() {
      assertThat(state.exportProgress.value.totalPhotos).isEqualTo(0)
      assertThat(state.exportProgress.value.processedPhotos).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("initialize")
  inner class Initialize {
    @Test
    @DisplayName("should populate images from file list")
    fun shouldPopulateImagesFromFiles() {
      val files = listOf(File("/test/img1.jpg"), File("/test/img2.jpg"))

      state.initialize(files)

      assertThat(state.images.value).hasSize(2)
    }

    @Test
    @DisplayName("should reset other state on initialize")
    fun shouldResetOtherState() {
      state.currentIndex.value = 5
      state.processedCount.value = 10
      val files = listOf(File("/test/img1.jpg"))

      state.initialize(files)

      assertThat(state.currentIndex.value).isEqualTo(0)
      assertThat(state.processedCount.value).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("setCurrentImageDetected")
  inner class SetCurrentImageDetected {
    @Test
    @DisplayName("should update detected photos for current image")
    fun shouldUpdateDetectedPhotos() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"), createDetectedPhoto("photo-2"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)

      state.setCurrentImageDetected(image, photos)

      assertThat(state.currentPhotos.value).hasSize(2)
    }

    @Test
    @DisplayName("should transition to corner editing")
    fun shouldTransitionToCornerEditing() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)

      state.setCurrentImageDetected(image, emptyList())

      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.CORNER_EDITING)
    }
  }

  @Nested
  @DisplayName("updatePhotoConfiguration")
  inner class UpdatePhotoConfiguration {
    @Test
    @DisplayName("should update photo configuration")
    fun shouldUpdatePhotoConfiguration() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)
      val config = PhotoScanConfiguration()

      state.updatePhotoConfiguration("photo-1", config)

      assertThat(state.currentPhotos.value[0].configuration).isEqualTo(config)
    }

    @Test
    @DisplayName("should not update other photos")
    fun shouldNotUpdateOtherPhotos() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"), createDetectedPhoto("photo-2"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)

      state.updatePhotoConfiguration("photo-1", PhotoScanConfiguration())

      assertThat(state.currentPhotos.value[1].id).isEqualTo("photo-2")
    }
  }

  @Nested
  @DisplayName("updatePhotoCorner")
  inner class UpdatePhotoCorner {
    @Test
    @DisplayName("should update top-left corner")
    fun shouldUpdateTopLeftCorner() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1", 0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)

      state.updatePhotoCorner("photo-1", CornerType.TOP_LEFT, 10f, 20f)

      assertThat(state.currentPhotos.value[0].topLeft.x).isEqualTo(10f)
      assertThat(state.currentPhotos.value[0].topLeft.y).isEqualTo(20f)
    }

    @Test
    @DisplayName("should update bottom-right corner")
    fun shouldUpdateBottomRightCorner() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)

      state.updatePhotoCorner("photo-1", CornerType.BOTTOM_RIGHT, 150f, 120f)

      assertThat(state.currentPhotos.value[0].bottomRight.x).isEqualTo(150f)
      assertThat(state.currentPhotos.value[0].bottomRight.y).isEqualTo(120f)
    }
  }

  @Nested
  @DisplayName("addPhoto")
  inner class AddPhoto {
    @Test
    @DisplayName("should add new photo to current image")
    fun shouldAddNewPhoto() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)

      state.addPhoto(createDetectedPhoto("photo-2"))

      assertThat(state.currentPhotos.value).hasSize(2)
    }
  }

  @Nested
  @DisplayName("removePhoto")
  inner class RemovePhoto {
    @Test
    @DisplayName("should remove photo from current image")
    fun shouldRemovePhoto() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"), createDetectedPhoto("photo-2"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)

      state.removePhoto("photo-1")

      assertThat(state.currentPhotos.value).hasSize(1)
      assertThat(state.currentPhotos.value[0].id).isEqualTo("photo-2")
    }

    @Test
    @DisplayName("should clear selected photo if removed")
    fun shouldClearSelectedPhotoIfRemoved() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val photos = listOf(createDetectedPhoto("photo-1"))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, photos)
      state.selectPhoto("photo-1")

      state.removePhoto("photo-1")

      assertThat(state.selectedPhotoId.value).isNull()
    }
  }

  @Nested
  @DisplayName("selectCorner")
  inner class SelectCorner {
    @Test
    @DisplayName("should update selected corner")
    fun shouldUpdateSelectedCorner() {
      state.selectCorner(CornerType.BOTTOM_RIGHT)

      assertThat(state.selectedCorner.value).isEqualTo(CornerType.BOTTOM_RIGHT)
    }

    @Test
    @DisplayName("should allow deselection")
    fun shouldAllowDeselection() {
      state.selectCorner(CornerType.TOP_LEFT)
      state.selectCorner(null)

      assertThat(state.selectedCorner.value).isNull()
    }
  }

  @Nested
  @DisplayName("selectPhoto")
  inner class SelectPhoto {
    @Test
    @DisplayName("should update selected photo ID")
    fun shouldUpdateSelectedPhotoId() {
      state.selectPhoto("photo-123")

      assertThat(state.selectedPhotoId.value).isEqualTo("photo-123")
    }
  }

  @Nested
  @DisplayName("nextImage")
  inner class NextImage {
    @Test
    @DisplayName("should increment index and reset state")
    fun shouldIncrementIndexAndReset() {
      state.initialize(listOf(File("/test/img1.jpg"), File("/test/img2.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, listOf(createDetectedPhoto("photo-1")))
      state.selectPhoto("photo-1")

      state.nextImage()

      assertThat(state.currentIndex.value).isEqualTo(1)
      assertThat(state.selectedPhotoId.value).isNull()
    }

    @Test
    @DisplayName("should update counts when advancing")
    fun shouldUpdateCounts() {
      state.initialize(listOf(File("/test/img1.jpg"), File("/test/img2.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, listOf(createDetectedPhoto("photo-1")))

      state.nextImage()

      assertThat(state.processedCount.value).isEqualTo(1)
      assertThat(state.extractedCount.value).isEqualTo(1)
    }

    @Test
    @DisplayName("should transition to complete on last image")
    fun shouldTransitionToComplete() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, emptyList())

      state.nextImage()

      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.COMPLETE)
    }
  }

  @Nested
  @DisplayName("skipImage")
  inner class SkipImage {
    @Test
    @DisplayName("should advance without counting extracted photos")
    fun shouldAdvanceWithoutExtracted() {
      state.initialize(listOf(File("/test/img1.jpg"), File("/test/img2.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, listOf(createDetectedPhoto("photo-1")))

      state.skipImage()

      assertThat(state.processedCount.value).isEqualTo(1)
      assertThat(state.extractedCount.value).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("workflow transitions")
  inner class WorkflowTransitions {
    @Test
    @DisplayName("should transition to metadata editing")
    fun shouldTransitionToMetadataEditing() {
      state.proceedToMetadata()

      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.METADATA_EDITING)
    }

    @Test
    @DisplayName("should transition to export")
    fun shouldTransitionToExport() {
      state.proceedToExport()

      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.EXPORTING)
    }

    @Test
    @DisplayName("should auto-select first photo when entering metadata")
    fun shouldAutoSelectFirstPhoto() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, listOf(createDetectedPhoto("auto-select-me")))

      state.proceedToMetadata()

      assertThat(state.selectedPhotoId.value).isEqualTo("auto-select-me")
    }
  }

  @Nested
  @DisplayName("reset")
  inner class Reset {
    @Test
    @DisplayName("should reset all state")
    fun shouldResetAllState() {
      state.initialize(listOf(File("/test/img1.jpg")))
      val image = java.awt.image.BufferedImage(100, 100, java.awt.image.BufferedImage.TYPE_INT_RGB)
      state.setCurrentImageDetected(image, listOf(createDetectedPhoto("photo-1")))
      state.selectPhoto("photo-1")
      state.selectCorner(CornerType.TOP_RIGHT)
      state.step.value = PhotoScanState.Step.EXPORTING

      state.reset()

      assertThat(state.images.value).isEmpty()
      assertThat(state.currentIndex.value).isEqualTo(0)
      assertThat(state.selectedPhotoId.value).isNull()
      assertThat(state.selectedCorner.value).isNull()
      assertThat(state.step.value).isEqualTo(PhotoScanState.Step.DETECTING)
      assertThat(state.processedCount.value).isEqualTo(0)
      assertThat(state.extractedCount.value).isEqualTo(0)
    }
  }

  @Nested
  @DisplayName("computed properties")
  inner class ComputedProperties {
    @Test
    @DisplayName("should calculate progress percent")
    fun shouldCalculateProgressPercent() {
      state.initialize(listOf(File("/test/img1.jpg"), File("/test/img2.jpg")))
      state.currentIndex.value = 1

      assertThat(state.progressPercent).isEqualTo(0.5f)
    }

    @Test
    @DisplayName("should return zero progress for empty images")
    fun shouldReturnZeroProgressForEmpty() {
      assertThat(state.progressPercent).isEqualTo(0f)
    }

    @Test
    @DisplayName("should calculate remaining count")
    fun shouldCalculateRemainingCount() {
      state.initialize(
          listOf(File("/test/img1.jpg"), File("/test/img2.jpg"), File("/test/img3.jpg")))
      state.currentIndex.value = 0

      assertThat(state.remainingCount).isEqualTo(2)
    }
  }
}
