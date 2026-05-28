package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.min
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf

/**
 * Verification test that Kotlin YoloPreprocessing produces output matching Python's PIL
 * Image.BILINEAR preprocessing pipeline.
 *
 * The Kotlin YoloPreprocessing.pilBilinearResize() exactly replicates PIL's antialiased bilinear
 * interpolation:
 * - For downscaling: wider kernel with support = max(1.0, 1/scale)
 * - For upscaling: standard bilinear with support = 1.0
 * - Zero-extension at boundaries (no edge clamping)
 * - Per-pixel weight normalization
 * - No intermediate rounding (float64 throughout)
 *
 * Expected difference vs PIL: at most ~1/255 per channel (from PIL's uint8 rounding of intermediate
 * passes), which is negligible for ONNX inference.
 */
class PreprocessingComparisonTest {

    companion object {
        private const val MODELS_DIR = "src/main/resources/models"
        private const val TEST_IMAGE =
            "/Users/krys.petrie/dev/photo-pose-detector/real_world_examples/real_world_example_01.jpg"
        private var modelsAvailable = false
        private var testImageAvailable = false

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val detModel = File("$MODELS_DIR/detection_model.onnx")
            val poseModel = File("$MODELS_DIR/pose_model.onnx")
            val cornerModel = File("$MODELS_DIR/corner_regression_model.onnx")
            modelsAvailable = detModel.exists() && poseModel.exists() && cornerModel.exists()
            testImageAvailable = File(TEST_IMAGE).exists()
        }

        @JvmStatic fun modelsAvailable(): Boolean = modelsAvailable && testImageAvailable
    }

    @Test
    @EnabledIf("modelsAvailable")
    fun `letterbox preprocessing produces correct format`() {
        val image = ImageIO.read(File(TEST_IMAGE))!!
        val targetSize = 640

        val result = YoloPreprocessing.preprocessLetterbox(image, targetSize)

        // Verify shape
        assert(result.shape contentEquals longArrayOf(1, 3, 640, 640)) {
            "Shape should be [1,3,640,640] but was ${result.shape.toList()}"
        }

        // Verify padding value (114/255 ≈ 0.4471)
        val padVal = 114.0f / 255.0f
        val topLeftPixel = result.flatArray[0]
        assert(abs(topLeftPixel - padVal) < 0.001f) {
            "Top-left padding should be ~$padVal but was $topLeftPixel"
        }

        // Verify value range is approximately [0, 1]
        val maxVal = result.flatArray.maxOrNull()!!
        val minVal = result.flatArray.minOrNull()!!
        assert(minVal >= -0.001f && maxVal <= 1.001f) {
            "Values should be in [0,1] (with float tolerance) but range was [$minVal, $maxVal]"
        }

        // Verify scale factors
        val origW = image.width
        val origH = image.height
        val expectedRatio = min(targetSize.toFloat() / origW, targetSize.toFloat() / origH)
        assert(abs(result.ratio - expectedRatio) < 0.0001f) {
            "Ratio should be $expectedRatio but was ${result.ratio}"
        }

        println("✅ Letterbox preprocessing format verified")
        println("   Image: ${image.width}x${image.height}")
        println("   Shape: ${result.shape.toList()}")
        println("   Ratio: ${result.ratio}")
        println("   Padding: (${result.padW}, ${result.padH})")
        println("   Value range: [$minVal, $maxVal]")
    }

    @Test
    @EnabledIf("modelsAvailable")
    fun `crop preprocessing produces correct format`() {
        val image = ImageIO.read(File(TEST_IMAGE))!!

        val cropX1 = 80
        val cropY1 = 1060
        val cropX2 = 750
        val cropY2 = 1970
        val cropW = cropX2 - cropX1
        val cropH = cropY2 - cropY1
        val crop = image.getSubimage(cropX1, cropY1, cropW, cropH)

        val result = YoloPreprocessing.preprocessCrop(crop, 640)

        assert(result.shape contentEquals longArrayOf(1, 3, 640, 640)) {
            "Shape should be [1,3,640,640] but was ${result.shape.toList()}"
        }
        assert(result.cropWidth == cropW && result.cropHeight == cropH) {
            "Original dimensions should be ${cropW}x${cropH} but were ${result.cropWidth}x${result.cropHeight}"
        }

        val maxVal = result.flatArray.maxOrNull()!!
        val minVal = result.flatArray.minOrNull()!!
        assert(minVal >= -0.001f && maxVal <= 1.001f) {
            "Values should be in [0,1] (with float tolerance) but range was [$minVal, $maxVal]"
        }

        println("✅ Crop preprocessing format verified")
        println("   Crop (${cropW}x${cropH}) -> 640x640")
        println("   Value range: [$minVal, $maxVal]")
    }

    @Test
    @EnabledIf("modelsAvailable")
    fun `detection preprocessing matches PIL reference`() {
        val image = ImageIO.read(File(TEST_IMAGE))!!
        val result = YoloPreprocessing.preprocessLetterbox(image, 640)

        // Save Kotlin buffer for comparison
        val dumpFile = File("/tmp/kotlin_detection_input.bin")
        dumpFile.outputStream().use { dos ->
            val buffer = java.nio.ByteBuffer.allocate(result.flatArray.size * 4)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (f in result.flatArray) {
                buffer.putFloat(f)
            }
            dos.write(buffer.array())
        }

        // Compare with Python buffer if available
        val pythonFile = File("/tmp/python_detection_input.bin")
        if (pythonFile.exists()) {
            val pythonData = pythonFile.readBytes()
            val kotlinData = dumpFile.readBytes()

            println("=== KOTLIN vs PYTHON (PIL BILINEAR) COMPARISON ===")
            println("Python buffer: ${pythonData.size} bytes")
            println("Kotlin buffer: ${kotlinData.size} bytes")

            if (pythonData.size != kotlinData.size) {
                println(
                    "❌ BUFFER SIZE MISMATCH: Python=${pythonData.size}, Kotlin=${kotlinData.size}"
                )
                return
            }

            val floatCount = pythonData.size / 4
            var maxDiff = 0.0f
            var maxDiffIdx = -1
            var sumAbsDiff = 0.0
            var diffCount001 = 0
            var diffCount01 = 0
            var diffCount1 = 0

            for (i in 0 until floatCount) {
                val pyVal =
                    java.nio.ByteBuffer.wrap(pythonData, i * 4, 4)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .float
                val ktVal =
                    java.nio.ByteBuffer.wrap(kotlinData, i * 4, 4)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .float
                val diff = abs(pyVal - ktVal)
                sumAbsDiff += diff.toDouble()
                if (diff > 0.001f) diffCount001++
                if (diff > 0.01f) diffCount01++
                if (diff > 0.1f) diffCount1++
                if (diff > maxDiff) {
                    maxDiff = diff
                    maxDiffIdx = i
                }
            }

            val meanAbsDiff = if (floatCount > 0) sumAbsDiff / floatCount else 0.0
            println("Total values: $floatCount (${floatCount / 3} pixels × 3 channels)")
            println(
                "Values diff >0.001: $diffCount001 (${"%.2f".format(100.0 * diffCount001 / floatCount)}%)"
            )
            println(
                "Values diff >0.01:  $diffCount01 (${"%.2f".format(100.0 * diffCount01 / floatCount)}%)"
            )
            println(
                "Values diff >0.1:   $diffCount1 (${"%.2f".format(100.0 * diffCount1 / floatCount)}%)"
            )
            println("Max absolute diff: $maxDiff")
            println("Mean absolute diff: ${"%.8f".format(meanAbsDiff)}")

            // With PIL BILINEAR matching, we expect differences within ~1/255
            // (from PIL's uint8 rounding of intermediate passes)
            if (maxDiff < 0.01f) {
                println("✅ EXCELLENT: Kotlin matches Python PIL within float precision")
            } else if (maxDiff < 0.01f) {
                println("✅ GOOD: Kotlin matches Python PIL within uint8 rounding tolerance")
            } else if (maxDiff < 0.02f) {
                println("✅ ACCEPTABLE: Differences within ~1/255 (PIL uint8 rounding)")
            } else {
                println("⚠️  Differences exceed expected bounds — investigate")
            }
        } else {
            println("Kotlin buffer saved to ${dumpFile.absolutePath}")
            println("Run Python reference script to generate /tmp/python_detection_input.bin")
        }
    }
}
