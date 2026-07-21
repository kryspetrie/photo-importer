package org.kryspetrie.fileimport.infrastructure.photoscan

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.infrastructure.adapter.AwtProcessedImage
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory

/**
 * Integration tests for orientation detection using the real ONNX model.
 *
 * Uses the existing test images (`faces-01.jpg`, `faces-02.jpg`) rotated at known angles (0°, 90°,
 * 180°, 270°) to verify that [OrientationDetectionService] correctly identifies the orientation and
 * computes the appropriate correction rotation.
 *
 * **Angle semantics:**
 * - The model outputs the **correction angle** (how much to rotate CW to make upright)
 * - `orientationDegrees` = how much the image is rotated CW from upright = (360 - correction) % 360
 * - `nearestRotation` = discrete [RotationAngle] mapped from `correctionDegrees`
 *
 * The correction mapping works as follows:
 * - Upright (0°) → correction ≈ 0° → NONE (no correction needed)
 * - 90° CW rotated → correction ≈ 270° → CCW_90 (rotate 270° CW = 90° CCW)
 * - 180° rotated → correction ≈ 180° → CW_180 (rotate 180°)
 * - 270° CW (= 90° CCW) → correction ≈ 90° → CW_90 (rotate 90° CW)
 *
 * The test is gated behind [orientationModelAvailable] — it only runs when the ONNX model is
 * present.
 */
@DisplayName("Orientation Detection Integration")
@EnabledIf("orientationModelAvailable")
class OrientationDetectionIntegrationTest {

    companion object {
        private val modelResourcePort = ClasspathModelResourceAdapter()
        private val available = modelResourcePort.isOrientationDetectionModelAvailable()

        private val service: OrientationDetectionService? by lazy {
            if (available) OrientationDetectionService(modelResourcePort, OrtSessionFactory())
            else null
        }

        @JvmStatic
        @BeforeAll
        fun setUp() {
            service?.preload()
        }

        @JvmStatic fun orientationModelAvailable(): Boolean = available

        private fun rotateImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
            val newWidth: Int
            val newHeight: Int
            when (rotation) {
                RotationAngle.CW_90,
                RotationAngle.CCW_90 -> {
                    newWidth = image.height
                    newHeight = image.width
                }
                else -> {
                    newWidth = image.width
                    newHeight = image.height
                }
            }
            val rotated =
                BufferedImage(
                    newWidth.coerceAtLeast(1),
                    newHeight.coerceAtLeast(1),
                    BufferedImage.TYPE_INT_RGB,
                )
            val graphics = rotated.createGraphics() as Graphics2D
            graphics.background = java.awt.Color.BLACK
            when (rotation) {
                RotationAngle.CW_90 -> {
                    graphics.translate(newWidth, 0)
                    graphics.rotate(Math.PI / 2)
                }
                RotationAngle.CCW_90 -> {
                    graphics.translate(0, newHeight)
                    graphics.rotate(-Math.PI / 2)
                }
                RotationAngle.CW_180 -> {
                    graphics.translate(newWidth / 2.0, newHeight / 2.0)
                    graphics.rotate(Math.PI)
                    graphics.translate(-image.width / 2.0, -image.height / 2.0)
                }
                RotationAngle.NONE -> {}
            }
            graphics.drawImage(image, 0, 0, null)
            graphics.dispose()
            return rotated
        }

        private fun loadImage(name: String): BufferedImage? {
            val stream =
                javaClass.classLoader.getResourceAsStream(
                    "org/kryspetrie/fileimport/application/$name.jpg"
                )
            if (stream != null) {
                return stream.use { ImageIO.read(it) }
            }
            val file = File("src/test/resources/org/kryspetrie/fileimport/application/$name.jpg")
            if (file.exists()) {
                return ImageIO.read(file)
            }
            println("WARN: Test image $name not found")
            return null
        }
    }

    // ─── Upright (0°) images → correction NONE ──────────────────────────────

    @Nested
    @DisplayName("Upright images (0°) → NONE")
    inner class UprightTests {

        @Test
        @DisplayName("faces-01 upright → NONE")
        fun faces01Upright() {
            val image = loadImage("faces-01") ?: return
            val result = service!!.detectOrientation(AwtProcessedImage(image)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "Upright faces-01 should need no correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.NONE)
        }

        @Test
        @DisplayName("faces-02 upright → NONE")
        fun faces02Upright() {
            val image = loadImage("faces-02") ?: return
            val result = service!!.detectOrientation(AwtProcessedImage(image)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "Upright faces-02 should need no correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.NONE)
        }
    }

    // ─── 90° CW rotated → correction CCW_90 (rotate 270° CW = 90° CCW) ───

    @Nested
    @DisplayName("90° CW rotated → correction CCW_90")
    inner class Rotated90CWTests {

        @Test
        @DisplayName("faces-01 rotated 90° CW → correction CCW_90")
        fun faces01Rotated90CW() {
            val original = loadImage("faces-01") ?: return
            val rotated = rotateImage(original, RotationAngle.CW_90)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "90° CW rotated faces-01 should need CCW_90 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CCW_90)
        }

        @Test
        @DisplayName("faces-02 rotated 90° CW → correction CCW_90")
        fun faces02Rotated90CW() {
            val original = loadImage("faces-02") ?: return
            val rotated = rotateImage(original, RotationAngle.CW_90)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "90° CW rotated faces-02 should need CCW_90 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CCW_90)
        }
    }

    // ─── 180° rotated → correction CW_180 ──────────────────────────────────

    @Nested
    @DisplayName("180° rotated → correction CW_180")
    inner class Rotated180Tests {

        @Test
        @DisplayName("faces-01 rotated 180° → correction CW_180")
        fun faces01Rotated180() {
            val original = loadImage("faces-01") ?: return
            val rotated = rotateImage(original, RotationAngle.CW_180)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "180° rotated faces-01 should need CW_180 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CW_180)
        }

        @Test
        @DisplayName("faces-02 rotated 180° → correction CW_180")
        fun faces02Rotated180() {
            val original = loadImage("faces-02") ?: return
            val rotated = rotateImage(original, RotationAngle.CW_180)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "180° rotated faces-02 should need CW_180 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CW_180)
        }
    }

    // ─── 270° CW (= 90° CCW) rotated → correction CW_90 ────────────────────

    @Nested
    @DisplayName("270° CW (= 90° CCW) rotated → correction CW_90")
    inner class Rotated270CWTests {

        @Test
        @DisplayName("faces-01 rotated 270° CW → correction CW_90")
        fun faces01Rotated270CW() {
            val original = loadImage("faces-01") ?: return
            val rotated = rotateImage(original, RotationAngle.CCW_90)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "270° CW rotated faces-01 should need CW_90 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CW_90)
        }

        @Test
        @DisplayName("faces-02 rotated 270° CW → correction CW_90")
        fun faces02Rotated270CW() {
            val original = loadImage("faces-02") ?: return
            val rotated = rotateImage(original, RotationAngle.CCW_90)
            val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: return
            assertThat(result.nearestRotation)
                .`as`(
                    "270° CW rotated faces-02 should need CW_90 correction, got orientation=${result.orientationDegrees}°, correction=${result.correctionDegrees}°"
                )
                .isEqualTo(RotationAngle.CW_90)
        }
    }

    // ─── Service availability ────────────────────────────────────────────

    @Nested
    @DisplayName("Service availability")
    inner class AvailabilityTests {

        @Test
        @DisplayName("Service reports availability when model is present")
        fun reportsAvailable() {
            assertThat(service!!.isOrientationDetectionAvailable()).isTrue
        }

        @Test
        @DisplayName("Preload succeeds when model is present")
        fun preloadSucceeds() {
            assertThat(service!!.preload()).isTrue
        }

        @Test
        @DisplayName("Detection returns non-null result for valid image")
        fun returnsNonNullResult() {
            val image = loadImage("faces-01") ?: return
            val result = service!!.detectOrientation(AwtProcessedImage(image))
            assertThat(result).`as`("Detection should return non-null for valid image").isNotNull
        }
    }

    // ─── Consistency across repeated calls ────────────────────────────────

    @Nested
    @DisplayName("Consistency")
    inner class ConsistencyTests {

        @Test
        @DisplayName("Repeated calls on same image produce same result")
        fun consistentResultsAcrossCalls() {
            val image = loadImage("faces-01") ?: return
            val result1 = service!!.detectOrientation(AwtProcessedImage(image))!!
            val result2 = service!!.detectOrientation(AwtProcessedImage(image))!!

            assertThat(result1.nearestRotation)
                .`as`("Repeated detection should give same rotation")
                .isEqualTo(result2.nearestRotation)
            assertThat(result1.orientationDegrees)
                .`as`("Repeated detection should give same angle")
                .isCloseTo(result2.orientationDegrees, within(1f))
        }
    }

    // ─── Correction angle is complementary to orientation ────────────────

    @Nested
    @DisplayName("Correction angle computation")
    inner class CorrectionAngleTests {

        @Test
        @DisplayName("Correction + orientation ≈ 360° for non-zero orientations")
        fun correctionComplementaryToOrientation() {
            val image = loadImage("faces-01") ?: return
            for (rotation in
                listOf(RotationAngle.CW_90, RotationAngle.CW_180, RotationAngle.CCW_90)) {
                val rotated = rotateImage(image, rotation)
                val result = service!!.detectOrientation(AwtProcessedImage(rotated)) ?: continue
                if (result.nearestRotation == RotationAngle.NONE) continue
                // correctionDegrees + orientationDegrees ≈ 360° (modulo floating-point)
                val sum = result.correctionDegrees + result.orientationDegrees
                val normalized = sum % 360f
                assertThat(normalized)
                    .`as`(
                        "correction(${result.correctionDegrees}°) + orientation(${result.orientationDegrees}°) ≈ 360° for rotation ${rotation.degrees}°, sum=$sum, normalized=$normalized"
                    )
                    .satisfies({ assertThat(it < 2f || it > 358f).isTrue })
            }
        }

        @Test
        @DisplayName("Upright image has near-zero correction and orientation")
        fun uprightNearZero() {
            val image = loadImage("faces-01") ?: return
            val result = service!!.detectOrientation(AwtProcessedImage(image)) ?: return
            assertThat(result.correctionDegrees)
                .`as`("Upright image correction should be near 0° or 360°")
                .satisfies({ assertThat(it < 10f || it > 350f).isTrue })
            assertThat(result.orientationDegrees)
                .`as`("Upright image orientation should be near 0° or 360°")
                .satisfies({ assertThat(it < 10f || it > 350f).isTrue })
        }
    }
}
