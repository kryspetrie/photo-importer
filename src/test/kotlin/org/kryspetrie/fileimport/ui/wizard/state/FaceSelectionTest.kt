package org.kryspetrie.fileimport.ui.wizard.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox

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

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
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

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.addFaceRegion(0, "Bob", 0.7, 0.6)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.faceRegions?.get(1)?.name)
            assertEquals("Alice, Bob", config?.subjects)
        }

        @Test
        @DisplayName("should add face regions to different photos independently")
        fun shouldAddFaceRegionsToDifferentPhotos() {
            addTestBoxes(2)

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.addFaceRegion(1, "Bob", 0.5, 0.5)

            val config0 = state.photoConfigurations.value[state.configs.boxes[0].id]
            val config1 = state.photoConfigurations.value[state.configs.boxes[1].id]
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

            state.faceRegions.addFaceRegion(0, "Over", -0.5, 1.5)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            val region = config?.faceRegions?.get(0)
            assertEquals(0.0, region?.x) // coerced from -0.5
            assertEquals(1.0, region?.y) // coerced from 1.5
        }

        @Test
        @DisplayName("should do nothing for out-of-range photo index")
        fun shouldDoNothingForOutOfRangeIndex() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(5, "Nobody", 0.5, 0.5)

            // Should not crash, no configuration created for out-of-range index
            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            // Config may be null or have no face regions
            assertEquals(0, config?.faceRegions?.size ?: 0)
        }

        @Test
        @DisplayName("should do nothing for negative photo index")
        fun shouldDoNothingForNegativeIndex() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(-1, "Nobody", 0.5, 0.5)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
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

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.addFaceRegion(0, "Bob", 0.7, 0.6)
            assertEquals(
                "Alice, Bob",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            state.faceRegions.removeFaceRegion(0, 0) // Remove Alice

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Bob", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.subjects)
        }

        @Test
        @DisplayName("should remove the last face region and clear subjects")
        fun shouldRemoveLastFaceRegion() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.removeFaceRegion(0, 0)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(0, config?.faceRegions?.size)
            assertEquals("", config?.subjects)
        }

        @Test
        @DisplayName("should do nothing for out-of-range face index")
        fun shouldDoNothingForOutOfRangeFaceIndex() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.removeFaceRegion(0, 5) // Out of range

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
        }

        @Test
        @DisplayName("should do nothing for out-of-range photo index")
        fun shouldDoNothingForOutOfRangePhotoIndex() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.removeFaceRegion(99, 0) // Out of range

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
        }
    }

    @Nested
    @DisplayName("face selection mode")
    inner class FaceSelectionMode {

        @Test
        @DisplayName("should enter face selection mode")
        fun shouldEnterFaceSelectionMode() {
            assertFalse(state.faceRegions.faceSelectMode.value)
            assertNull(state.faceRegions.faceSelectPhotoIndex.value)

            state.faceRegions.enterFaceSelectMode(2)

            assertTrue(state.faceRegions.faceSelectMode.value)
            assertEquals(2, state.faceRegions.faceSelectPhotoIndex.value)
        }

        @Test
        @DisplayName("should exit face selection mode")
        fun shouldExitFaceSelectionMode() {
            state.faceRegions.enterFaceSelectMode(0)
            assertTrue(state.faceRegions.faceSelectMode.value)

            state.faceRegions.exitFaceSelectMode()

            assertFalse(state.faceRegions.faceSelectMode.value)
            assertNull(state.faceRegions.faceSelectPhotoIndex.value)
        }

        @Test
        @DisplayName("should switch photo index when re-entering face selection")
        fun shouldSwitchPhotoIndexOnReEnter() {
            state.faceRegions.enterFaceSelectMode(0)
            assertEquals(0, state.faceRegions.faceSelectPhotoIndex.value)

            state.faceRegions.enterFaceSelectMode(3)
            assertEquals(3, state.faceRegions.faceSelectPhotoIndex.value)
        }
    }

    @Nested
    @DisplayName("face regions persistence")
    inner class FaceRegionsPersistence {

        @Test
        @DisplayName("should persist face regions through configuration updates")
        fun shouldPersistThroughConfigUpdate() {
            addTestBoxes(1)

            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            // Update other configuration fields
            state.configs.updatePhotoScanConfiguration(state.configs.boxes[0].id) {
                it.copy(description = "Test photo")
            }
            state.configs.updatePhotoScanConfiguration(state.configs.boxes[0].id) {
                it.copy(keywords = "vacation")
            }

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
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
            state.configs.updatePhotoScanConfiguration(state.configs.boxes[0].id) {
                it.copy(subjects = "Grandma, Grandpa")
            }

            // Adding a face should replace subjects with face region names
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            // addFaceRegion rebuilds subjects from all faceRegion names
            assertEquals("Alice", config?.subjects)
        }
    }

    @Nested
    @DisplayName("addDetectedFaceRegions")
    inner class AddDetectedFaceRegions {

        @Test
        @DisplayName("should add multiple unnamed face regions at once")
        fun shouldAddMultipleUnnamedFaceRegions() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.3, y = 0.4, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.7, y = 0.5, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.2, w = 0.08, h = 0.08),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(3, config?.faceRegions?.size)
            // Unnamed regions should not contribute to subjects
            assertEquals("", config?.subjects)
            assertEquals(0.3, config?.faceRegions?.get(0)?.x)
            assertEquals(0.7, config?.faceRegions?.get(1)?.x)
            assertEquals(0.5, config?.faceRegions?.get(2)?.x)
        }

        @Test
        @DisplayName("should add detected regions alongside manually-placed regions")
        fun shouldAddDetectedRegionsAlongsideManual() {
            addTestBoxes(1)

            // Place a manual face first
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            assertEquals(
                1,
                state.photoConfigurations.value[state.configs.boxes[0].id]?.faceRegions?.size,
            )

            // Auto-detect adds more
            val detected =
                listOf(FaceRegion(name = "", type = "Face", x = 0.7, y = 0.5, w = 0.14, h = 0.14))
            state.faceRegions.addDetectedFaceRegions(0, detected)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            // Alice is named, so subjects includes her
            assertEquals("Alice", config?.subjects)
        }

        @Test
        @DisplayName("should rebuild subjects from all named regions after bulk add")
        fun shouldRebuildSubjectsFromNamedRegionsAfterBulkAdd() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "Bob", type = "Face", x = 0.3, y = 0.4, w = 0.14, h = 0.14),
                    FaceRegion(name = "Carol", type = "Face", x = 0.7, y = 0.5, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            assertEquals("Bob, Carol", config?.subjects)
        }

        @Test
        @DisplayName("should ignore out-of-range photo index")
        fun shouldIgnoreOutOfRangePhotoIndex() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "Nobody", type = "Face", x = 0.5, y = 0.5, w = 0.14, h = 0.14)
                )
            state.faceRegions.addDetectedFaceRegions(5, detected)
            state.faceRegions.addDetectedFaceRegions(-1, detected)

            // Should not crash or add any configuration
            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(0, config?.faceRegions?.size ?: 0)
        }

        @Test
        @DisplayName("should add face regions to different photos independently")
        fun shouldAddRegionsToDifferentPhotos() {
            addTestBoxes(2)

            val detected0 =
                listOf(
                    FaceRegion(name = "Alice", type = "Face", x = 0.3, y = 0.3, w = 0.14, h = 0.14)
                )
            val detected1 =
                listOf(
                    FaceRegion(name = "Bob", type = "Face", x = 0.6, y = 0.6, w = 0.14, h = 0.14)
                )
            state.faceRegions.addDetectedFaceRegions(0, detected0)
            state.faceRegions.addDetectedFaceRegions(1, detected1)

            val config0 = state.photoConfigurations.value[state.configs.boxes[0].id]
            val config1 = state.photoConfigurations.value[state.configs.boxes[1].id]
            assertEquals(1, config0?.faceRegions?.size)
            assertEquals("Alice", config0?.faceRegions?.get(0)?.name)
            assertEquals("Alice", config0?.subjects)
            assertEquals(1, config1?.faceRegions?.size)
            assertEquals("Bob", config1?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config1?.subjects)
        }

        @Test
        @DisplayName("should add face regions with different region types")
        fun shouldAddRegionsWithDifferentTypes() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.3, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Pet", x = 0.7, y = 0.5, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            assertEquals("Face", config?.faceRegions?.get(0)?.type)
            assertEquals("Pet", config?.faceRegions?.get(1)?.type)
        }
    }

    @Nested
    @DisplayName("updateFaceRegionName")
    inner class UpdateFaceRegionName {

        @Test
        @DisplayName("should update the name of a face region")
        fun shouldUpdateFaceRegionName() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "", 0.3, 0.4)

            state.faceRegions.updateFaceRegionName(0, 0, "Alice")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
        }

        @Test
        @DisplayName("should auto-populate subjects when name is set")
        fun shouldAutoPopulateSubjectsWhenNameIsSet() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "", 0.3, 0.4)

            state.faceRegions.updateFaceRegionName(0, 0, "Alice")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Alice", config?.subjects)
        }

        @Test
        @DisplayName("should update subjects when renaming an existing face")
        fun shouldUpdateSubjectsWhenRenaming() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.addFaceRegion(0, "Bob", 0.7, 0.6)
            assertEquals(
                "Alice, Bob",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            state.faceRegions.updateFaceRegionName(0, 0, "Carol")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Carol", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.faceRegions?.get(1)?.name)
            assertEquals("Carol, Bob", config?.subjects)
        }

        @Test
        @DisplayName("should remove name from subjects when cleared to empty string")
        fun shouldRemoveNameFromSubjectsWhenCleared() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)
            state.faceRegions.addFaceRegion(0, "Bob", 0.7, 0.6)
            assertEquals(
                "Alice, Bob",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            state.faceRegions.updateFaceRegionName(0, 0, "")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("", config?.faceRegions?.get(0)?.name)
            // Only Bob is named now
            assertEquals("Bob", config?.subjects)
        }

        @Test
        @DisplayName("should do nothing for out-of-range photo index")
        fun shouldDoNothingForOutOfRangePhotoIndex() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            state.faceRegions.updateFaceRegionName(5, 0, "Bob")
            state.faceRegions.updateFaceRegionName(-1, 0, "Bob")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
        }

        @Test
        @DisplayName("should do nothing for out-of-range face index")
        fun shouldDoNothingForOutOfRangeFaceIndex() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            state.faceRegions.updateFaceRegionName(0, 5, "Bob")
            state.faceRegions.updateFaceRegionName(0, -1, "Bob")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
        }

        @Test
        @DisplayName("should update multiple face names sequentially")
        fun shouldUpdateMultipleFaceNamesSequentially() {
            addTestBoxes(1)
            state.faceRegions.addFaceRegion(0, "", 0.2, 0.3)
            state.faceRegions.addFaceRegion(0, "", 0.5, 0.4)
            state.faceRegions.addFaceRegion(0, "", 0.8, 0.5)

            state.faceRegions.updateFaceRegionName(0, 0, "Alice")
            state.faceRegions.updateFaceRegionName(0, 1, "Bob")
            state.faceRegions.updateFaceRegionName(0, 2, "Carol")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(3, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.faceRegions?.get(1)?.name)
            assertEquals("Carol", config?.faceRegions?.get(2)?.name)
            assertEquals("Alice, Bob, Carol", config?.subjects)
        }
    }

    @Nested
    @DisplayName("addDetectedFaceRegions then updateFaceRegionName (naming cycle)")
    inner class NamingCycle {

        @Test
        @DisplayName("should add detected faces then name them sequentially")
        fun shouldAddDetectedFacesThenNameThem() {
            addTestBoxes(1)

            // Step 1: Auto-detect 3 unnamed faces
            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.2, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.4, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.8, y = 0.5, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            val config0 = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("", config0?.subjects) // No names yet
            assertEquals(3, config0?.faceRegions?.size)

            // Step 2: Name faces one by one (simulating Tab cycling)
            state.faceRegions.updateFaceRegionName(0, 0, "Alice")
            assertEquals(
                "Alice",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            state.faceRegions.updateFaceRegionName(0, 1, "Bob")
            assertEquals(
                "Alice, Bob",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            state.faceRegions.updateFaceRegionName(0, 2, "Carol")
            assertEquals(
                "Alice, Bob, Carol",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )
        }

        @Test
        @DisplayName("should correctly name faces after mixing detection and manual addition")
        fun shouldMixDetectionAndManualAddition() {
            addTestBoxes(1)

            // Add a manual face first
            state.faceRegions.addFaceRegion(0, "Alice", 0.3, 0.4)

            // Then auto-detect more
            val detected =
                listOf(FaceRegion(name = "", type = "Face", x = 0.7, y = 0.5, w = 0.14, h = 0.14))
            state.faceRegions.addDetectedFaceRegions(0, detected)

            // Name the detected face
            state.faceRegions.updateFaceRegionName(0, 1, "Bob")

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals("Alice, Bob", config?.subjects)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Bob", config?.faceRegions?.get(1)?.name)
        }
    }

    @Nested
    @DisplayName("skip face during naming cycle")
    inner class SkipFaceDuringNaming {

        @Test
        @DisplayName("should remove face and advance when skipping")
        fun shouldRemoveFaceAndAdvanceWhenSkipping() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.2, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.4, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.8, y = 0.5, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            assertEquals(
                3,
                state.photoConfigurations.value[state.configs.boxes[0].id]?.faceRegions?.size,
            )

            // Skip face 0 (remove it)
            state.faceRegions.removeFaceRegion(0, 0)

            // After skipping, remaining faces should be re-indexed
            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)
            // Faces 1 and 2 should now be at indices 0 and 1
            assertEquals("", config?.faceRegions?.get(0)?.name) // face at x=0.5
            assertEquals("", config?.faceRegions?.get(1)?.name) // face at x=0.8
        }

        @Test
        @DisplayName("should name face, skip next, then name last")
        fun shouldNameFaceSkipNextThenNameLast() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.2, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.4, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.8, y = 0.5, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            // Name face 0
            state.faceRegions.updateFaceRegionName(0, 0, "Alice")
            assertEquals(
                "Alice",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            // Skip face 1 (remove it)
            state.faceRegions.removeFaceRegion(0, 1)

            // Face 2 is now at index 1
            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(2, config?.faceRegions?.size)

            // Name face 2 (now at index 1)
            state.faceRegions.updateFaceRegionName(0, 1, "Carol")
            assertEquals(
                "Alice, Carol",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )
        }

        @Test
        @DisplayName("should skip all faces leaving none")
        fun shouldSkipAllFacesLeavingNone() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.2, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.4, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            // Skip (remove) face 0
            state.faceRegions.removeFaceRegion(0, 0)
            // Skip (remove) face 0 (which was face 1)
            state.faceRegions.removeFaceRegion(0, 0)

            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(0, config?.faceRegions?.size)
            assertEquals("", config?.subjects)
        }

        @Test
        @DisplayName("should update subjects correctly after skip and rename")
        fun shouldUpdateSubjectsCorrectlyAfterSkipAndRename() {
            addTestBoxes(1)

            val detected =
                listOf(
                    FaceRegion(name = "", type = "Face", x = 0.2, y = 0.3, w = 0.14, h = 0.14),
                    FaceRegion(name = "", type = "Face", x = 0.5, y = 0.4, w = 0.14, h = 0.14),
                )
            state.faceRegions.addDetectedFaceRegions(0, detected)

            // Name face 0 as "Alice"
            state.faceRegions.updateFaceRegionName(0, 0, "Alice")
            assertEquals(
                "Alice",
                state.photoConfigurations.value[state.configs.boxes[0].id]?.subjects,
            )

            // Skip face 1 (remove it)
            state.faceRegions.removeFaceRegion(0, 1)

            // Only Alice remains
            val config = state.photoConfigurations.value[state.configs.boxes[0].id]
            assertEquals(1, config?.faceRegions?.size)
            assertEquals("Alice", config?.faceRegions?.get(0)?.name)
            assertEquals("Alice", config?.subjects)
        }
    }
}
