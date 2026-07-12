package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Tests for FaceRegion data class and PhotoScanConfiguration face region behavior. */
@DisplayName("PhotoScanConfiguration Face Regions")
class PhotoScanConfigurationFaceRegionExtTest {

    @Nested
    @DisplayName("FaceRegion")
    inner class FaceRegionTests {

        @Test
        @DisplayName("FaceRegion should have correct defaults")
        fun shouldHaveCorrectDefaults() {
            val region = FaceRegion()
            assertEquals("", region.name)
            assertEquals("Face", region.type)
            assertEquals(0.0, region.x)
            assertEquals(0.0, region.y)
            assertEquals(0.0, region.w)
            assertEquals(0.0, region.h)
        }

        @Test
        @DisplayName("FaceRegion should accept custom values")
        fun shouldAcceptCustomValues() {
            val region =
                FaceRegion(name = "Alice", type = "Pet", x = 0.3, y = 0.4, w = 0.15, h = 0.20)
            assertEquals("Alice", region.name)
            assertEquals("Pet", region.type)
            assertEquals(0.3, region.x)
            assertEquals(0.4, region.y)
            assertEquals(0.15, region.w)
            assertEquals(0.20, region.h)
        }

        @Test
        @DisplayName("FaceRegion should support copy with modification")
        fun shouldSupportCopyWithModification() {
            val region = FaceRegion(name = "Alice", x = 0.3, y = 0.4, w = 0.15, h = 0.20)
            val renamed = region.copy(name = "Bob")
            assertEquals("Bob", renamed.name)
            assertEquals(0.3, renamed.x) // preserved
        }
    }

    @Nested
    @DisplayName("PhotoScanConfiguration face region behavior")
    inner class ConfigFaceRegionTests {

        @Test
        @DisplayName("default configuration has empty faceRegions")
        fun shouldHaveEmptyFaceRegions() {
            val config = PhotoScanConfiguration()
            assertTrue(config.faceRegions.isEmpty())
        }

        @Test
        @DisplayName("hasMetadata returns true when faceRegions is non-empty")
        fun shouldReturnTrueWhenFaceRegionsNonEmpty() {
            val config = PhotoScanConfiguration(faceRegions = listOf(FaceRegion(name = "Alice")))
            assertTrue(config.hasMetadata())
        }

        @Test
        @DisplayName("hasMetadata returns false when faceRegions is empty but subjects is blank")
        fun shouldReturnFalseWhenFaceRegionsEmptyAndSubjectsBlank() {
            val config = PhotoScanConfiguration(faceRegions = emptyList(), subjects = "")
            assertFalse(config.hasMetadata())
        }

        @Test
        @DisplayName("hasMetadata returns true when subjects is non-blank even without faceRegions")
        fun shouldReturnTrueWhenSubjectsNonBlank() {
            val config = PhotoScanConfiguration(subjects = "Alice, Bob")
            assertTrue(config.hasMetadata())
        }

        @Test
        @DisplayName("subjectList parses comma-separated names")
        fun shouldParseSubjectList() {
            val config = PhotoScanConfiguration(subjects = "Alice, Bob, Charlie")
            assertEquals(listOf("Alice", "Bob", "Charlie"), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles leading/trailing whitespace")
        fun shouldHandleWhitespaceInSubjectList() {
            val config = PhotoScanConfiguration(subjects = " Alice , Bob , Charlie ")
            assertEquals(listOf("Alice", "Bob", "Charlie"), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles empty string")
        fun shouldHandleEmptySubjectList() {
            val config = PhotoScanConfiguration(subjects = "")
            assertEquals(emptyList<String>(), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles single name")
        fun shouldHandleSingleName() {
            val config = PhotoScanConfiguration(subjects = "Alice")
            assertEquals(listOf("Alice"), config.subjectList())
        }

        @Test
        @DisplayName("configuration can have multiple face regions")
        fun shouldHaveMultipleFaceRegions() {
            val regions =
                listOf(
                    FaceRegion(name = "Alice", x = 0.3, y = 0.4, w = 0.15, h = 0.20),
                    FaceRegion(name = "Bob", x = 0.7, y = 0.5, w = 0.15, h = 0.20),
                    FaceRegion(name = "Charlie", x = 0.5, y = 0.6, w = 0.15, h = 0.20),
                )
            val config = PhotoScanConfiguration(faceRegions = regions)
            assertEquals(3, config.faceRegions.size)
            assertEquals("Alice", config.faceRegions[0].name)
            assertEquals("Bob", config.faceRegions[1].name)
            assertEquals("Charlie", config.faceRegions[2].name)
        }

        @Test
        @DisplayName("configuration preserves faceRegions through copy")
        fun shouldPreserveFaceRegionsThroughCopy() {
            val regions = listOf(FaceRegion(name = "Alice", x = 0.3, y = 0.4, w = 0.15, h = 0.20))
            val config = PhotoScanConfiguration(faceRegions = regions, description = "Test")
            val copied = config.copy(description = "Updated")
            assertEquals(1, copied.faceRegions.size)
            assertEquals("Alice", copied.faceRegions[0].name)
            assertEquals("Updated", copied.description)
        }

        @Test
        @DisplayName("hasMetadata returns true with both subjects and faceRegions")
        fun shouldReturnTrueWithBothSubjectsAndFaceRegions() {
            val config =
                PhotoScanConfiguration(
                    subjects = "Alice",
                    faceRegions = listOf(FaceRegion(name = "Alice")),
                )
            assertTrue(config.hasMetadata())
        }

        @Test
        @DisplayName("face region coordinates can be at boundaries")
        fun shouldAllowBoundaryCoordinates() {
            val region = FaceRegion(name = "Edge", x = 0.0, y = 1.0, w = 1.0, h = 1.0)
            assertEquals(0.0, region.x)
            assertEquals(1.0, region.y)
            assertEquals(1.0, region.w)
            assertEquals(1.0, region.h)
        }
    }

    @Nested
    @DisplayName("FaceRegion rotation transforms")
    inner class FaceRegionRotationTests {

        @Test
        @DisplayName("rotate90CW transforms correctly")
        fun rotate90CW_transformsCorrectly() {
            val region = FaceRegion(name = "A", x = 0.2, y = 0.1, w = 0.15, h = 0.20)
            val rotated = region.rotate90CW()
            assertEquals(0.9, rotated.x, 0.001) // 1 - 0.1
            assertEquals(0.2, rotated.y, 0.001)
            assertEquals(0.20, rotated.w, 0.001) // h swapped to w
            assertEquals(0.15, rotated.h, 0.001) // w swapped to h
        }

        @Test
        @DisplayName("rotate90CCW transforms correctly")
        fun rotate90CCW_transformsCorrectly() {
            val region = FaceRegion(name = "B", x = 0.8, y = 0.1, w = 0.15, h = 0.20)
            val rotated = region.rotate90CCW()
            assertEquals(0.1, rotated.x, 0.001) // y
            assertEquals(0.2, rotated.y, 0.001) // 1 - 0.8
            assertEquals(0.20, rotated.w, 0.001) // h swapped
            assertEquals(0.15, rotated.h, 0.001) // w swapped
        }

        @Test
        @DisplayName("rotate180 mirrors both axes")
        fun rotate180_mirrorsBothAxes() {
            val region = FaceRegion(name = "C", x = 0.2, y = 0.3, w = 0.15, h = 0.20)
            val rotated = region.rotate180()
            assertEquals(0.8, rotated.x, 0.001) // 1 - 0.2
            assertEquals(0.7, rotated.y, 0.001) // 1 - 0.3
            assertEquals(0.15, rotated.w, 0.001) // w unchanged
            assertEquals(0.20, rotated.h, 0.001) // h unchanged
        }

        @Test
        @DisplayName("four CW rotations return to original position")
        fun fourCWRotationsReturnToOriginal() {
            val original = FaceRegion(name = "D", x = 0.25, y = 0.35, w = 0.10, h = 0.15)
            var current = original
            repeat(4) { current = current.rotate90CW() }
            assertEquals(original.x, current.x, 0.001)
            assertEquals(original.y, current.y, 0.001)
            assertEquals(original.w, current.w, 0.001)
            assertEquals(original.h, current.h, 0.001)
            assertEquals(original.name, current.name)
        }

        @Test
        @DisplayName("CW then CCW returns to original")
        fun cwThenCCWReturnsToOriginal() {
            val original = FaceRegion(name = "E", x = 0.3, y = 0.7, w = 0.12, h = 0.18)
            val rotated = original.rotate90CW().rotate90CCW()
            assertEquals(original.x, rotated.x, 0.001)
            assertEquals(original.y, rotated.y, 0.001)
            assertEquals(original.w, rotated.w, 0.001)
            assertEquals(original.h, rotated.h, 0.001)
        }
    }

    @Nested
    @DisplayName("PhotoScanConfiguration rotation with face regions")
    inner class ConfigRotationWithFaceRegions {

        @Test
        @DisplayName("cycleRotationCW transforms face regions")
        fun cycleRotationCWTransformsFaceRegions() {
            val config =
                PhotoScanConfiguration(
                    rotationDegrees = 0,
                    faceRegions =
                        listOf(FaceRegion(name = "A", x = 0.2, y = 0.3, w = 0.15, h = 0.20)),
                )
            val rotated = config.cycleRotationCW()
            assertEquals(90, rotated.rotationDegrees)
            assertEquals(1, rotated.faceRegions.size)
            assertEquals(0.7, rotated.faceRegions[0].x, 0.001) // 1 - 0.3
            assertEquals(0.2, rotated.faceRegions[0].y, 0.001)
        }

        @Test
        @DisplayName("cycleRotationCCW transforms face regions")
        fun cycleRotationCCWTransformsFaceRegions() {
            val config =
                PhotoScanConfiguration(
                    rotationDegrees = 0,
                    faceRegions =
                        listOf(FaceRegion(name = "A", x = 0.2, y = 0.3, w = 0.15, h = 0.20)),
                )
            val rotated = config.cycleRotationCCW()
            assertEquals(270, rotated.rotationDegrees)
            assertEquals(1, rotated.faceRegions.size)
            assertEquals(0.3, rotated.faceRegions[0].x, 0.001) // y
            assertEquals(0.8, rotated.faceRegions[0].y, 0.001) // 1 - 0.2
        }

        @Test
        @DisplayName("rotate180 transforms face regions")
        fun rotate180TransformsFaceRegions() {
            val config =
                PhotoScanConfiguration(
                    rotationDegrees = 0,
                    faceRegions =
                        listOf(FaceRegion(name = "A", x = 0.2, y = 0.3, w = 0.15, h = 0.20)),
                )
            val rotated = config.rotate180()
            assertEquals(180, rotated.rotationDegrees)
            assertEquals(0.8, rotated.faceRegions[0].x, 0.001) // 1 - 0.2
            assertEquals(0.7, rotated.faceRegions[0].y, 0.001) // 1 - 0.3
        }

        @Test
        @DisplayName("rotation with empty face regions doesn't crash")
        fun rotationWithEmptyFaceRegions() {
            val config = PhotoScanConfiguration(rotationDegrees = 0)
            val rotated = config.cycleRotationCW()
            assertEquals(90, rotated.rotationDegrees)
            assertTrue(rotated.faceRegions.isEmpty())
        }
    }
}
