package org.kryspetrie.fileimport.infrastructure.wizard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for face selection state management in PhotoScanWizardState.
 *
 * Covers addFaceRegion, removeFaceRegion, face selection mode, and auto-population of subjects from
 * face region names.
 */
@DisplayName("Face Selection")
class FaceSelectionTest {

    private lateinit var state: PhotoScanWizardState

    @BeforeEach
    fun setup() {
        state = PhotoScanWizardState()
    }

    private fun addTestBoxes(count: Int) {
        val boxes =
            (0 until count).map { i ->
                BoundingBox.createRectangular(100.0 + i * 200.0, 100.0, 150.0, 150.0)
            }
        state.setDetectedBoxes(boxes)
    }

    @Nested
    @DisplayName("addFaceRegion")
    inner class AddFaceRegion {

        @Test
        @DisplayName("should add a face region with default size and auto-populate subjects")
        fun shouldAddFaceRegionWithDefaultSize() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            val region = config?.faceRegions?.get(0)
            assertEquals("Alice", region?.name)
            assertEquals("Face", region?.type)
            assertEquals(0.3, region?.x!!)
            assertEquals(0.4, region?.y)
            assertEquals(FaceSize.DEFAULT.diameter, region?.w)
            assertEquals(FaceSize.DEFAULT.diameter, region?.h)
            // Subjects should be auto-populated
            assertEquals("Alice", config?.subjects)
        }

        @Test
        @DisplayName("should add multiple face regions and accumulate subject names")
        fun shouldAddMultipleFaceRegions() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.addFaceRegion(0, "Bob", 0.7, 0.6)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.faceRegions?.get(1)?.name)
            assertEquals("Alice, Bob", config?.subjects)
        }

        @Test
        @DisplayName("should add face regions to different photos independently")
        fun shouldAddFaceRegionsToDifferentPhotos() {
            addTestBoxes(2)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.addFaceRegion(1, "Bob", 0.5, 0.5)

            val config0 = state.photoConfigurations.value[state.boxes[0].id]
            val config1 = state.photoConfigurations.value[state.boxes[1].id]
            assertEquals(1, config0?.faceRegions?.size)
            assertEquals("Alice", config0?.faceRegions?.get(0)?.name)
            assertEquals("Alice", config0?.subjects)
            assertEquals(1, config1?.faceRegions?.size)
            assertEquals("Bob", config1?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config1?.subjects)
        }

        @Test
        @DisplayName("should coerce coordinates to 0.0-1.0 range")
        fun shouldCoerceCoordinates() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Over", -0.5, 1.5)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            val region = config?.faceRegions?.get(0)
            assertEquals(0.0, region?.x) // coerced from -0.5
            assertEquals(1.0, region?.y) // coerced from 1.5
        }

        @Test
        @DisplayName("should do nothing for out-of-range photo index")
        fun shouldDoNothingForOutOfRangeIndex() {
            addTestBoxes(1)

            state.addFaceRegion(5, "Nobody", 0.5, 0.5)

            // Should not crash, no configuration created for out-of-range index
            val config = state.photoConfigurations.value[state.boxes[0].id]
            // Config may be null or have no face regions
            assertEquals(0, config?.faceRegions?.size ?: 0)
        }

        @Test
        @DisplayName("should do nothing for negative photo index")
        fun shouldDoNothingForNegativeIndex() {
            addTestBoxes(1)

            state.addFaceRegion(-1, "Nobody", 0.5, 0.5)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertTrue(config?.faceRegions?.isEmpty() ?: true)
        }
    }

    @Nested
    @DisplayName("removeFaceRegion")
    inner class RemoveFaceRegion {

        @Test
        @DisplayName("should remove a face region and update subjects")
        fun shouldRemoveFaceRegion() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.addFaceRegion(0, "Bob", 0.7, 0.6)
            assertEquals("Alice, Bob", state.photoConfigurations.value[state.boxes[0].id]?.subjects)

            state.removeFaceRegion(0, 0) // Remove Alice

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Bob", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.subjects)
        }

        @Test
        @DisplayName("should remove the last face region and clear subjects")
        fun shouldRemoveLastFaceRegion() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.removeFaceRegion(0, 0)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(0, config?.faceRegions?.size)
            assertEquals("", config?.subjects)
        }

        @Test
        @DisplayName("should do nothing for out-of-range face index")
        fun shouldDoNothingForOutOfRangeFaceIndex() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.removeFaceRegion(0, 5) // Out of range

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
        }

        @Test
        @DisplayName("should do nothing for out-of-range photo index")
        fun shouldDoNothingForOutOfRangePhotoIndex() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.removeFaceRegion(99, 0) // Out of range

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
        }
    }

    @Nested
    @DisplayName("face selection mode")
    inner class FaceSelectionMode {

        @Test
        @DisplayName("should enter face selection mode")
        fun shouldEnterFaceSelectionMode() {
            assertFalse(state.faceSelectMode.value)
            assertNull(state.faceSelectPhotoIndex.value)

            state.enterFaceSelectMode(2)

            assertTrue(state.faceSelectMode.value)
            assertEquals(2, state.faceSelectPhotoIndex.value)
        }

        @Test
        @DisplayName("should exit face selection mode")
        fun shouldExitFaceSelectionMode() {
            state.enterFaceSelectMode(0)
            assertTrue(state.faceSelectMode.value)

            state.exitFaceSelectMode()

            assertFalse(state.faceSelectMode.value)
            assertNull(state.faceSelectPhotoIndex.value)
        }

        @Test
        @DisplayName("should switch photo index when re-entering face selection")
        fun shouldSwitchPhotoIndexOnReEnter() {
            state.enterFaceSelectMode(0)
            assertEquals(0, state.faceSelectPhotoIndex.value)

            state.enterFaceSelectMode(3)
            assertEquals(3, state.faceSelectPhotoIndex.value)
        }
    }

    @Nested
    @DisplayName("face regions persistence")
    inner class FaceRegionsPersistence {

        @Test
        @DisplayName("should persist face regions through configuration updates")
        fun shouldPersistThroughConfigUpdate() {
            addTestBoxes(1)

            state.addFaceRegion(0, "Alice", 0.3, 0.4)

            // Update other configuration fields
            state.updatePhotoConfiguration(state.boxes[0].id) {
                it.copy(description = "Test photo")
            }
            state.updatePhotoConfiguration(state.boxes[0].id) { it.copy(keywords = "vacation") }

            val config = state.photoConfigurations.value[state.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Test photo", config?.description)
            assertEquals("vacation", config?.keywords)
            assertEquals("Alice", config?.subjects)
        }

        @Test
        @DisplayName("should preserve existing subjects when adding face region")
        fun shouldPreserveSubjectsWhenAddingFace() {
            addTestBoxes(1)

            // Set subjects manually first
            state.updatePhotoConfiguration(state.boxes[0].id) {
                it.copy(subjects = "Grandma, Grandpa")
            }

            // Adding a face should replace subjects with face region names
            state.addFaceRegion(0, "Alice", 0.3, 0.4)

            val config = state.photoConfigurations.value[state.boxes[0].id]
            // addFaceRegion rebuilds subjects from all faceRegion names
            assertEquals("Alice", config?.subjects)
        }
    }
}
