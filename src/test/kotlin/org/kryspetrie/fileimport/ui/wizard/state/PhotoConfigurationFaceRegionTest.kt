package org.kryspetrie.fileimport.ui.wizard.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.model.FaceRegion

/** Tests for FaceRegion data class and PhotoConfiguration face region behavior. */
@DisplayName("PhotoConfiguration Face Regions")
class PhotoConfigurationFaceRegionTest {

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
    @DisplayName("PhotoConfiguration face region behavior")
    inner class ConfigFaceRegionTests {

        @Test
        @DisplayName("default configuration has empty faceRegions")
        fun shouldHaveEmptyFaceRegions() {
            val config = PhotoConfiguration()
            assertTrue(config.faceRegions.isEmpty())
        }

        @Test
        @DisplayName("hasMetadata returns true when faceRegions is non-empty")
        fun shouldReturnTrueWhenFaceRegionsNonEmpty() {
            val config = PhotoConfiguration(faceRegions = listOf(FaceRegion(name = "Alice")))
            assertTrue(config.hasMetadata())
        }

        @Test
        @DisplayName("hasMetadata returns false when faceRegions is empty but subjects is blank")
        fun shouldReturnFalseWhenFaceRegionsEmptyAndSubjectsBlank() {
            val config = PhotoConfiguration(faceRegions = emptyList(), subjects = "")
            assertFalse(config.hasMetadata())
        }

        @Test
        @DisplayName("hasMetadata returns true when subjects is non-blank even without faceRegions")
        fun shouldReturnTrueWhenSubjectsNonBlank() {
            val config = PhotoConfiguration(subjects = "Alice, Bob")
            assertTrue(config.hasMetadata())
        }

        @Test
        @DisplayName("subjectList parses comma-separated names")
        fun shouldParseSubjectList() {
            val config = PhotoConfiguration(subjects = "Alice, Bob, Charlie")
            assertEquals(listOf("Alice", "Bob", "Charlie"), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles leading/trailing whitespace")
        fun shouldHandleWhitespaceInSubjectList() {
            val config = PhotoConfiguration(subjects = " Alice , Bob , Charlie ")
            assertEquals(listOf("Alice", "Bob", "Charlie"), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles empty string")
        fun shouldHandleEmptySubjectList() {
            val config = PhotoConfiguration(subjects = "")
            assertEquals(emptyList<String>(), config.subjectList())
        }

        @Test
        @DisplayName("subjectList handles single name")
        fun shouldHandleSingleName() {
            val config = PhotoConfiguration(subjects = "Alice")
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
            val config = PhotoConfiguration(faceRegions = regions)
            assertEquals(3, config.faceRegions.size)
            assertEquals("Alice", config.faceRegions[0].name)
            assertEquals("Bob", config.faceRegions[1].name)
            assertEquals("Charlie", config.faceRegions[2].name)
        }

        @Test
        @DisplayName("configuration preserves faceRegions through copy")
        fun shouldPreserveFaceRegionsThroughCopy() {
            val regions = listOf(FaceRegion(name = "Alice", x = 0.3, y = 0.4, w = 0.15, h = 0.20))
            val config = PhotoConfiguration(faceRegions = regions, description = "Test")
            val copied = config.copy(description = "Updated")
            assertEquals(1, copied.faceRegions.size)
            assertEquals("Alice", copied.faceRegions[0].name)
            assertEquals("Updated", copied.description)
        }

        @Test
        @DisplayName("hasMetadata returns true with both subjects and faceRegions")
        fun shouldReturnTrueWithBothSubjectsAndFaceRegions() {
            val config =
                PhotoConfiguration(
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
}
