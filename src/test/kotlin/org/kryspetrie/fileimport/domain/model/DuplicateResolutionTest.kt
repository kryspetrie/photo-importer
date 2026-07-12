package org.kryspetrie.fileimport.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("DuplicateResolution")
class DuplicateResolutionTest {

    private val candidates =
        listOf(
            ResolvableDuplicate(
                id = "raw-large",
                pixelCount = 24_000_000,
                isRawFormat = true,
                lastModifiedEpochMillis = 1000,
                fileSize = 50_000_000,
            ),
            ResolvableDuplicate(
                id = "jpeg-small",
                pixelCount = 6_000_000,
                isRawFormat = false,
                lastModifiedEpochMillis = 2000,
                fileSize = 5_000_000,
            ),
            ResolvableDuplicate(
                id = "jpeg-new",
                pixelCount = 12_000_000,
                isRawFormat = false,
                lastModifiedEpochMillis = 3000,
                fileSize = 10_000_000,
            ),
            ResolvableDuplicate(
                id = "jpeg-old",
                pixelCount = 12_000_000,
                isRawFormat = false,
                lastModifiedEpochMillis = 500,
                fileSize = 8_000_000,
            ),
        )

    @Nested
    @DisplayName("KEEP_HIGHEST_RES")
    inner class KeepHighestRes {

        @Test
        fun `selects file with highest pixel count`() {
            val result = pickKeeper(candidates, DuplicateAction.KEEP_HIGHEST_RES)
            assertEquals("raw-large", result)
        }

        @Test
        fun `returns first when pixel counts are equal`() {
            val tied =
                listOf(
                    ResolvableDuplicate(
                        id = "a",
                        pixelCount = 1000,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 0,
                        fileSize = 0,
                    ),
                    ResolvableDuplicate(
                        id = "b",
                        pixelCount = 1000,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 0,
                        fileSize = 0,
                    ),
                )
            val result = pickKeeper(tied, DuplicateAction.KEEP_HIGHEST_RES)
            assertEquals("a", result)
        }
    }

    @Nested
    @DisplayName("KEEP_RAW_OVER_JPEG")
    inner class KeepRawOverJpeg {

        @Test
        fun `prefers RAW format over JPEG`() {
            val result = pickKeeper(candidates, DuplicateAction.KEEP_RAW_OVER_JPEG)
            assertEquals("raw-large", result)
        }

        @Test
        fun `returns first JPEG when no RAW format exists`() {
            val jpegsOnly =
                listOf(
                    ResolvableDuplicate(
                        id = "jpeg-1",
                        pixelCount = 1000,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 0,
                        fileSize = 0,
                    ),
                    ResolvableDuplicate(
                        id = "jpeg-2",
                        pixelCount = 2000,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 0,
                        fileSize = 0,
                    ),
                )
            val result = pickKeeper(jpegsOnly, DuplicateAction.KEEP_RAW_OVER_JPEG)
            assertEquals("jpeg-1", result)
        }
    }

    @Nested
    @DisplayName("KEEP_NEWEST")
    inner class KeepNewest {

        @Test
        fun `selects file with most recent modification time`() {
            val result = pickKeeper(candidates, DuplicateAction.KEEP_NEWEST)
            assertEquals("jpeg-new", result)
        }
    }

    @Nested
    @DisplayName("KEEP_OLDEST")
    inner class KeepOldest {

        @Test
        fun `selects file with earliest modification time`() {
            val result = pickKeeper(candidates, DuplicateAction.KEEP_OLDEST)
            assertEquals("jpeg-old", result)
        }
    }

    @Nested
    @DisplayName("KEEP_LARGEST")
    inner class KeepLargest {

        @Test
        fun `selects file with largest file size`() {
            val result = pickKeeper(candidates, DuplicateAction.KEEP_LARGEST)
            assertEquals("raw-large", result)
        }
    }

    @Nested
    @DisplayName("edge cases")
    inner class EdgeCases {

        @Test
        fun `single candidate returns itself regardless of action`() {
            val single =
                listOf(
                    ResolvableDuplicate(
                        id = "only",
                        pixelCount = 1000,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 0,
                        fileSize = 0,
                    )
                )
            DuplicateAction.entries.forEach { action ->
                assertEquals("only", pickKeeper(single, action))
            }
        }

        @Test
        fun `all actions work with two candidates`() {
            val pair =
                listOf(
                    ResolvableDuplicate(
                        id = "first",
                        pixelCount = 100,
                        isRawFormat = false,
                        lastModifiedEpochMillis = 100,
                        fileSize = 50,
                    ),
                    ResolvableDuplicate(
                        id = "second",
                        pixelCount = 200,
                        isRawFormat = true,
                        lastModifiedEpochMillis = 200,
                        fileSize = 100,
                    ),
                )
            assertEquals("second", pickKeeper(pair, DuplicateAction.KEEP_HIGHEST_RES))
            assertEquals("second", pickKeeper(pair, DuplicateAction.KEEP_RAW_OVER_JPEG))
            assertEquals("second", pickKeeper(pair, DuplicateAction.KEEP_NEWEST))
            assertEquals("first", pickKeeper(pair, DuplicateAction.KEEP_OLDEST))
            assertEquals("second", pickKeeper(pair, DuplicateAction.KEEP_LARGEST))
        }
    }
}
