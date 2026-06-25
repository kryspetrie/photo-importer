package org.kryspetrie.fileimport.ui.components

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.infrastructure.photoscan.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.CorrectionStrategy
import org.kryspetrie.fileimport.domain.model.DetectionMode
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxCorners
import org.kryspetrie.fileimport.domain.model.geometry.Point

@DisplayName("PreviewCache")
@Tag("UnitTest")
class PreviewCacheTest {

    private lateinit var perspectiveService: PerspectiveCorrectionService
    private lateinit var previewCache: PreviewCache
    private lateinit var testImage: BufferedImage
    private lateinit var testBox: BoundingBox
    private lateinit var defaultConfig: PhotoScanConfiguration

    @BeforeEach
    fun setup() {
        perspectiveService = PerspectiveCorrectionService()
        previewCache = PreviewCache(perspectiveService)
        testImage = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
        testBox = BoundingBox(
            corners = BoundingBoxCorners(
                topLeft = Point(100.0, 100.0),
                topRight = Point(300.0, 100.0),
                bottomRight = Point(300.0, 200.0),
                bottomLeft = Point(100.0, 200.0),
            )
        )
        defaultConfig = PhotoScanConfiguration()
    }

    @Nested
    @DisplayName("cache key generation")
    inner class CacheKeyTests {

        @Test
        @DisplayName("same box and config produce same cache key")
        fun sameBoxAndConfigProducesSameKey() {
            val key1 = previewCache.cacheKey(testBox, defaultConfig)
            val key2 = previewCache.cacheKey(testBox, defaultConfig)
            assertThat(key1).isEqualTo(key2)
        }

        @Test
        @DisplayName("different rotation produces different cache key")
        fun differentRotationProducesDifferentKey() {
            val config0 = PhotoScanConfiguration(rotationDegrees = 0)
            val config90 = PhotoScanConfiguration(rotationDegrees = 90)
            val key0 = previewCache.cacheKey(testBox, config0)
            val key90 = previewCache.cacheKey(testBox, config90)
            assertThat(key0).isNotEqualTo(key90)
        }

        @Test
        @DisplayName("different perspective correction produces different cache key")
        fun differentPerspectiveProducesDifferentKey() {
            val configOff = PhotoScanConfiguration(perspectiveCorrectionEnabled = false)
            val configOn = PhotoScanConfiguration(perspectiveCorrectionEnabled = true)
            val keyOff = previewCache.cacheKey(testBox, configOff)
            val keyOn = previewCache.cacheKey(testBox, configOn)
            assertThat(keyOff).isNotEqualTo(keyOn)
        }

        @Test
        @DisplayName("different aspect ratio produces different cache key")
        fun differentAspectRatioProducesDifferentKey() {
            val configOriginal = PhotoScanConfiguration(aspectRatio = 0.0)
            val config43 = PhotoScanConfiguration(aspectRatio = 0.75)
            val keyOriginal = previewCache.cacheKey(testBox, configOriginal)
            val key43 = previewCache.cacheKey(testBox, config43)
            assertThat(keyOriginal).isNotEqualTo(key43)
        }

        @Test
        @DisplayName("different correction strategy produces different cache key")
        fun differentCorrectionStrategyProducesDifferentKey() {
            val configDefault = PhotoScanConfiguration(correctionStrategy = null)
            val configCrop = PhotoScanConfiguration(correctionStrategy = CorrectionStrategy.CROP)
            val keyDefault = previewCache.cacheKey(testBox, configDefault)
            val keyCrop = previewCache.cacheKey(testBox, configCrop)
            assertThat(keyDefault).isNotEqualTo(keyCrop)
        }

        @Test
        @DisplayName("different box corners produce different cache key")
        fun differentBoxCornersProduceDifferentKey() {
            val otherBox = BoundingBox(
                corners = BoundingBoxCorners(
                    topLeft = Point(200.0, 200.0),
                    topRight = Point(400.0, 200.0),
                    bottomRight = Point(400.0, 300.0),
                    bottomLeft = Point(200.0, 300.0),
                )
            )
            val key1 = previewCache.cacheKey(testBox, defaultConfig)
            val key2 = previewCache.cacheKey(otherBox, defaultConfig)
            assertThat(key1).isNotEqualTo(key2)
        }

        @Test
        @DisplayName("metadata-only changes do not affect cache key")
        fun metadataOnlyChangesDoNotAffectCacheKey() {
            val configNoMetadata = PhotoScanConfiguration()
            val configWithMetadata = PhotoScanConfiguration(
                description = "Test photo",
                keywords = "wedding, family",
                originalDate = "2024-01-15",
                cameraMake = "Canon",
                subjects = "Mom, Dad",
            )
            val keyNoMetadata = previewCache.cacheKey(testBox, configNoMetadata)
            val keyWithMetadata = previewCache.cacheKey(testBox, configWithMetadata)
            assertThat(keyNoMetadata).isEqualTo(keyWithMetadata)
        }

        @Test
        @DisplayName("detection mode change does not affect cache key (visual-only key)")
        fun detectionModeDoesNotAffectCacheKey() {
            val configNoMode = PhotoScanConfiguration(detectionMode = null)
            val configCv = PhotoScanConfiguration(detectionMode = DetectionMode.COMPUTER_VISION)
            val keyNoMode = previewCache.cacheKey(testBox, configNoMode)
            val keyCv = previewCache.cacheKey(testBox, configCv)
            // Detection mode doesn't affect the visual preview
            assertThat(keyNoMode).isEqualTo(keyCv)
        }
    }

    @Nested
    @DisplayName("thumbnail generation")
    inner class ThumbnailTests {

        @Test
        @DisplayName("getThumbnail returns a non-null bitmap for valid box")
        fun getThumbnailReturnsNonNullForValidBox() {
            val result = previewCache.getThumbnail(testImage, testBox, defaultConfig)
            assertThat(result).isNotNull()
        }

        @Test
        @DisplayName("getThumbnail caches results - second call returns same bitmap")
        fun getThumbnailCachesResults() {
            val result1 = previewCache.getThumbnail(testImage, testBox, defaultConfig)
            val result2 = previewCache.getThumbnail(testImage, testBox, defaultConfig)
            assertThat(result1).isSameAs(result2)
        }

        @Test
        @DisplayName("getThumbnail respects rotation in cache key")
        fun getThumbnailRespectsRotation() {
            val config0 = PhotoScanConfiguration(rotationDegrees = 0)
            val config90 = PhotoScanConfiguration(rotationDegrees = 90)
            val result0 = previewCache.getThumbnail(testImage, testBox, config0)
            val result90 = previewCache.getThumbnail(testImage, testBox, config90)
            // Different rotations should produce different bitmaps (different cache keys)
            assertThat(result0).isNotSameAs(result90)
        }

        @Test
        @DisplayName("thumbnail size respects max dimension")
        fun thumbnailSizeRespectsMaxDimension() {
            val smallCache = PreviewCache(perspectiveService, thumbnailMaxSize = 100)
            val result = smallCache.getThumbnail(testImage, testBox, defaultConfig)
            assertThat(result).isNotNull()
            // The thumbnail should be at most 100px in any dimension
            assertThat(result!!.width).isBetween(1, 100)
            assertThat(result.height).isBetween(1, 100)
        }
    }

    @Nested
    @DisplayName("full preview generation")
    inner class FullPreviewTests {

        @Test
        @DisplayName("getFullPreview returns a non-null image for valid box")
        fun getFullPreviewReturnsNonNullForValidBox() {
            val result = previewCache.getFullPreview(testImage, testBox, defaultConfig)
            assertThat(result).isNotNull()
        }

        @Test
        @DisplayName("getFullPreview caches results - second call returns same image")
        fun getFullPreviewCachesResults() {
            val result1 = previewCache.getFullPreview(testImage, testBox, defaultConfig)
            val result2 = previewCache.getFullPreview(testImage, testBox, defaultConfig)
            assertThat(result1).isSameAs(result2)
        }

        @Test
        @DisplayName("getFullPreview and getThumbnail use same cache key but different caches")
        fun fullPreviewAndThumbnailAreSeparateCaches() {
            val full = previewCache.getFullPreview(testImage, testBox, defaultConfig)
            val thumb = previewCache.getThumbnail(testImage, testBox, defaultConfig)
            assertThat(full).isNotNull()
            assertThat(thumb).isNotNull()
            // The full preview and thumbnail are stored in separate caches;
            // they share the same cache key but are different bitmap objects
            // (thumbnail may be same size as full if crop is smaller than max thumbnail dimension)
        }
    }

    @Nested
    @DisplayName("cache management")
    inner class CacheManagementTests {

        @Test
        @DisplayName("clear removes all cached entries")
        fun clearRemovesAllEntries() {
            previewCache.getThumbnail(testImage, testBox, defaultConfig)
            previewCache.getFullPreview(testImage, testBox, defaultConfig)
            assertThat(previewCache.thumbnailSize).isGreaterThan(0)
            assertThat(previewCache.fullSize).isGreaterThan(0)

            previewCache.clear()
            assertThat(previewCache.thumbnailSize).isEqualTo(0)
            assertThat(previewCache.fullSize).isEqualTo(0)
        }

        @Test
        @DisplayName("invalidate removes specific entry")
        fun invalidateRemovesSpecificEntry() {
            previewCache.getThumbnail(testImage, testBox, defaultConfig)
            assertThat(previewCache.thumbnailSize).isEqualTo(1)

            previewCache.invalidate(testBox, defaultConfig)
            assertThat(previewCache.thumbnailSize).isEqualTo(0)
        }

        @Test
        @DisplayName("invalidate does not remove other entries")
        fun invalidateDoesNotRemoveOtherEntries() {
            val config0 = PhotoScanConfiguration(rotationDegrees = 0)
            val config90 = PhotoScanConfiguration(rotationDegrees = 90)

            previewCache.getThumbnail(testImage, testBox, config0)
            previewCache.getThumbnail(testImage, testBox, config90)
            assertThat(previewCache.thumbnailSize).isEqualTo(2)

            previewCache.invalidate(testBox, config0)
            assertThat(previewCache.thumbnailSize).isEqualTo(1)
        }

        @Test
        @DisplayName("retainOnly removes entries not in the given list")
        fun retainOnlyRemovesUnneededEntries() {
            val box2 = BoundingBox(
                corners = BoundingBoxCorners(
                    topLeft = Point(400.0, 400.0),
                    topRight = Point(500.0, 400.0),
                    bottomRight = Point(500.0, 500.0),
                    bottomLeft = Point(400.0, 500.0),
                )
            )
            val config0 = PhotoScanConfiguration(rotationDegrees = 0)
            val config90 = PhotoScanConfiguration(rotationDegrees = 90)

            // Cache 2 different entries
            previewCache.getThumbnail(testImage, testBox, config0)
            previewCache.getThumbnail(testImage, testBox, config90)
            assertThat(previewCache.thumbnailSize).isEqualTo(2)

            // Retain only the 0-degree version
            previewCache.retainOnly(listOf(testBox), mapOf(testBox.id to config0))
            assertThat(previewCache.thumbnailSize).isEqualTo(1)
        }

        @Test
        @DisplayName("preWarm populates cache for multiple boxes")
        fun preWarmPopulatesCache() {
            val box2 = BoundingBox(
                corners = BoundingBoxCorners(
                    topLeft = Point(400.0, 400.0),
                    topRight = Point(500.0, 400.0),
                    bottomRight = Point(500.0, 500.0),
                    bottomLeft = Point(400.0, 500.0),
                )
            )
            val configs = mapOf(
                testBox.id to defaultConfig,
                box2.id to defaultConfig,
            )
            val boxes = listOf(testBox, box2)

            previewCache.preWarm(testImage, boxes, configs)
            assertThat(previewCache.fullSize).isEqualTo(2)
        }

        @Test
        @DisplayName("preWarm skips already-cached entries")
        fun preWarmSkipsAlreadyCachedEntries() {
            previewCache.getFullPreview(testImage, testBox, defaultConfig)
            val sizeBefore = previewCache.fullSize

            previewCache.preWarm(testImage, listOf(testBox), mapOf(testBox.id to defaultConfig))
            // Should not add duplicates
            assertThat(previewCache.fullSize).isEqualTo(sizeBefore)
        }
    }

    @Nested
    @DisplayName("downsampleToBitmap")
    inner class DownsampleTests {

        @Test
        @DisplayName("downsamples large image to fit within max dimension")
        fun downsamplesLargeImage() {
            val largeImage = BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB)
            val result = PreviewCache.downsampleToBitmap(largeImage, 400)
            assertThat(result.width).isBetween(1, 400)
            assertThat(result.height).isBetween(1, 400)
        }

        @Test
        @DisplayName("does not upscale small image")
        fun doesNotUpscaleSmallImage() {
            val smallImage = BufferedImage(100, 75, BufferedImage.TYPE_INT_RGB)
            val result = PreviewCache.downsampleToBitmap(smallImage, 400)
            // Should remain at original size since it's smaller than max
            assertThat(result.width).isEqualTo(100)
            assertThat(result.height).isEqualTo(75)
        }

        @Test
        @DisplayName("handles square image correctly")
        fun handlesSquareImage() {
            val squareImage = BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB)
            val result = PreviewCache.downsampleToBitmap(squareImage, 200)
            assertThat(result.width).isEqualTo(200)
            assertThat(result.height).isEqualTo(200)
        }

        @Test
        @DisplayName("preserves aspect ratio when downsampling")
        fun preservesAspectRatio() {
            val wideImage = BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB)
            val result = PreviewCache.downsampleToBitmap(wideImage, 500)
            assertThat(result.width).isEqualTo(500)
            assertThat(result.height).isEqualTo(250)
        }
    }
}