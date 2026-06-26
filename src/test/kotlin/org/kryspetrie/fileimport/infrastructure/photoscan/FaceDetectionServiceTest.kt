package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.image.BufferedImage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.domain.port.DetectedFace
import org.kryspetrie.fileimport.domain.port.ModelResourcePort
import org.kryspetrie.fileimport.infrastructure.adapter.AwtProcessedImage
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.photoscan.yolo.YoloFaceDetectionService
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [FaceDetectionService].
 *
 * Uses Mockito to stub [ModelResourcePort] and [YoloFaceDetectionService].
 * Reflection injects a mock [YoloFaceDetectionService] into the lazy `faceService` delegate
 * to avoid needing a real ONNX Runtime session, while still exercising the actual
 * [FaceDetectionService.detectFaces] mapping logic.
 */
class FaceDetectionServiceTest {

    private lateinit var modelResourcePort: ModelResourcePort

    @BeforeEach
    fun setUp() {
        modelResourcePort = mock()
    }

    @Nested
    @DisplayName("isFaceDetectionAvailable()")
    inner class IsFaceDetectionAvailableTests {

        @Test
        @DisplayName("returns true when model is available")
        fun returnsTrueWhenModelAvailable() {
            whenever(modelResourcePort.isFaceDetectionModelAvailable()).thenReturn(true)
            val service = FaceDetectionService(modelResourcePort, OrtSessionFactory())

            val result = service.isFaceDetectionAvailable()

            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("returns false when model is NOT available")
        fun returnsFalseWhenModelNotAvailable() {
            whenever(modelResourcePort.isFaceDetectionModelAvailable()).thenReturn(false)
            val service = FaceDetectionService(modelResourcePort, OrtSessionFactory())

            val result = service.isFaceDetectionAvailable()

            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("detectFaces()")
    inner class DetectFacesTests {

        @Test
        @DisplayName("throws IllegalStateException when model is NOT available")
        fun throwsWhenModelNotAvailable() {
            whenever(modelResourcePort.isFaceDetectionModelAvailable()).thenReturn(false)
            val service = FaceDetectionService(modelResourcePort, OrtSessionFactory())
            val image = AwtProcessedImage(
                BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
            )

            assertThatThrownBy { service.detectFaces(image) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Face detection model is not available")
        }

        @Test
        @DisplayName("converts ProcessedImage to BufferedImage and delegates to YoloFaceDetectionService")
        fun convertsAndDelegatesToYoloService() {
            whenever(modelResourcePort.isFaceDetectionModelAvailable()).thenReturn(true)

            val bufferedImage = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
            val image = AwtProcessedImage(bufferedImage)

            val yoloService = mock<YoloFaceDetectionService>()
            whenever(yoloService.detectFaces(bufferedImage, 0.5f, 0.45f))
                .thenReturn(emptyList())

            val service = FaceDetectionService(modelResourcePort, OrtSessionFactory())
            service.injectYoloService(yoloService)

            service.detectFaces(image, 0.5f, 0.45f)

            verify(yoloService).detectFaces(bufferedImage, 0.5f, 0.45f)
        }

        @Test
        @DisplayName("maps FaceDetection to DetectedFace correctly")
        fun mapsDetectedFaceCorrectly() {
            whenever(modelResourcePort.isFaceDetectionModelAvailable()).thenReturn(true)

            val bufferedImage = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
            val image = AwtProcessedImage(bufferedImage)

            val face1 = YoloFaceDetectionService.FaceDetection(
                x1 = 10.5f,
                y1 = 20.5f,
                x2 = 110.5f,
                y2 = 120.5f,
                confidence = 0.95f,
            )

            val face2 = YoloFaceDetectionService.FaceDetection(
                x1 = 200f,
                y1 = 300f,
                x2 = 400f,
                y2 = 500f,
                confidence = 0.8f,
            )

            val yoloService = mock<YoloFaceDetectionService>()
            whenever(yoloService.detectFaces(bufferedImage, 0.5f, 0.45f))
                .thenReturn(listOf(face1, face2))

            val service = FaceDetectionService(modelResourcePort, OrtSessionFactory())
            service.injectYoloService(yoloService)

            val results = service.detectFaces(image, 0.5f, 0.45f)

            assertThat(results).hasSize(2)

            assertThat(results[0]).isEqualTo(
                DetectedFace(
                    x1 = 10.5f,
                    y1 = 20.5f,
                    x2 = 110.5f,
                    y2 = 120.5f,
                    confidence = 0.95f,
                )
            )

            assertThat(results[1]).isEqualTo(
                DetectedFace(
                    x1 = 200f,
                    y1 = 300f,
                    x2 = 400f,
                    y2 = 500f,
                    confidence = 0.8f,
                )
            )

            verify(yoloService).detectFaces(bufferedImage, 0.5f, 0.45f)
        }
    }

    /**
     * Injects a mock [YoloFaceDetectionService] into the `faceService` lazy delegate
     * of [FaceDetectionService] via reflection.
     *
     * This avoids needing a real ONNX Runtime session while testing the actual
     * [FaceDetectionService.detectFaces] method — including the ProcessedImage-to-BufferedImage
     * conversion and the YOLO-to-domain result mapping.
     */
    private fun FaceDetectionService.injectYoloService(mock: YoloFaceDetectionService) {
        val delegateField = FaceDetectionService::class.java
            .getDeclaredField("faceService\$delegate")
        delegateField.isAccessible = true
        delegateField.set(this, lazyOf(mock))
    }
}