package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter
import org.kryspetrie.fileimport.infrastructure.adapter.OrtSessionFactory
import org.kryspetrie.fileimport.infrastructure.photoscan.PhotoScanDetectorService
import org.kryspetrie.fileimport.infrastructure.photoscan.RectangleDetector

/**
 * Visual comparison test: runs Kotlin pipeline and produces annotated image comparable to Python's
 * --debug output for side-by-side comparison.
 */
class PipelineVisualComparisonTest {

    companion object {
        private const val MODELS_DIR = "src/main/resources/models"
        private const val TEST_IMAGE =
            "/Users/krys.petrie/dev/photo-pose-detector/real_world_examples/real_world_example_01.jpg"
        private const val OUTPUT_PATH = "/tmp/kotlin_pipeline_output.jpg"
        private var modelsAvailable = false

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val detModel = File("$MODELS_DIR/detection_model.onnx")
            val poseModel = File("$MODELS_DIR/pose_model.onnx")
            val cornerModel = File("$MODELS_DIR/corner_regression_model.onnx")
            modelsAvailable = detModel.exists() && poseModel.exists() && cornerModel.exists()
            if (!modelsAvailable) {
                println("SKIP: ONNX models not found in $MODELS_DIR")
            }
        }

        @JvmStatic fun modelsAvailable(): Boolean = modelsAvailable
    }

    @Test
    @EnabledIf("modelsAvailable")
    fun `generate annotated comparison image`() {
        val imageFile = File(TEST_IMAGE)
        if (!imageFile.exists()) {
            println("SKIP: Test image not found at $TEST_IMAGE")
            return
        }

        val image = ImageIO.read(imageFile)
        println("Loaded image: ${image.width}x${image.height}")

        val service =
            PhotoScanDetectorService(
                rectangleDetector = RectangleDetector(),
                maxPhotos = 4,
                modelResourcePort = ClasspathModelResourceAdapter(),
                ortSessionFactory = OrtSessionFactory(),
            )

        assert(service.isYoloAvailable()) { "YOLO models should be available" }

        val results = service.detectPhotos(image)
        println("Pipeline detected ${results.size} photos")

        // Annotate image with results
        val annotated = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val g2d = annotated.createGraphics()
        g2d.drawImage(image, 0, 0, null)

        // Colors matching Python's photocrop.py
        val boxColor = Color(255, 0, 255) // #FF00FF magenta
        val edgeColor = Color(0, 255, 255) // #00FFFF cyan
        val keypointColors =
            mapOf(
                "LL" to Color(255, 68, 68), // #FF4444 red
                "UL" to Color(68, 221, 68), // #44DD44 green
                "UR" to Color(68, 136, 255), // #4488FF blue
                "LR" to Color(255, 204, 0), // #FFCC00 yellow
            )

        val font = Font("Helvetica", Font.PLAIN, 14)
        g2d.font = font
        g2d.stroke = BasicStroke(2f)

        for ((i, photo) in results.withIndex()) {
            val corners =
                listOf(
                    "LL" to photo.bottomLeft,
                    "UL" to photo.topLeft,
                    "UR" to photo.topRight,
                    "LR" to photo.bottomRight,
                )

            // Draw bounding box
            val minX = corners.minOf { it.second.x }
            val minY = corners.minOf { it.second.y }
            val maxX = corners.maxOf { it.second.x }
            val maxY = corners.maxOf { it.second.y }

            g2d.color = boxColor
            g2d.drawRect(minX.toInt(), minY.toInt(), (maxX - minX).toInt(), (maxY - minY).toInt())
            g2d.drawString("photo #${i + 1}", minX.toInt() + 4, minY.toInt() + 16)

            // Draw corner circles and labels
            val points = mutableListOf<Pair<Float, Float>>()
            for ((name, corner) in corners) {
                val color = keypointColors[name] ?: Color.WHITE
                g2d.color = color
                val r = 6
                g2d.fillOval(corner.x.toInt() - r, corner.y.toInt() - r, r * 2, r * 2)

                g2d.color = Color.BLACK
                g2d.drawOval(corner.x.toInt() - r, corner.y.toInt() - r, r * 2, r * 2)

                g2d.color = color
                g2d.drawString(
                    "$name (${corner.x.toInt()}, ${corner.y.toInt()})",
                    corner.x.toInt() + 10,
                    corner.y.toInt() - 8,
                )
                points.add(Pair(corner.x, corner.y))
            }

            // Draw quadrilateral edges: LL->UL->UR->LR->LL
            g2d.color = edgeColor
            g2d.stroke = BasicStroke(2f)
            for (j in 0 until points.size) {
                val p1 = points[j]
                val p2 = points[(j + 1) % points.size]
                g2d.drawLine(
                    p1.first.toInt(),
                    p1.second.toInt(),
                    p2.first.toInt(),
                    p2.second.toInt(),
                )
            }
        }

        g2d.dispose()

        // Save annotated image
        val outputFile = File(OUTPUT_PATH)
        ImageIO.write(annotated, "jpg", outputFile)
        println("Saved annotated image to: $OUTPUT_PATH")

        // Also print corner coordinates for comparison
        println("\nKotlin pipeline corner coordinates:")
        for ((i, photo) in results.withIndex()) {
            println("  Photo ${i + 1}:")
            println("    UL (${photo.topLeft.x}, ${photo.topLeft.y})")
            println("    UR (${photo.topRight.x}, ${photo.topRight.y})")
            println("    LR (${photo.bottomRight.x}, ${photo.bottomRight.y})")
            println("    LL (${photo.bottomLeft.x}, ${photo.bottomLeft.y})")
        }

        // Print Python reference for comparison
        println("\nPython pipeline corner coordinates (reference):")
        println("  Photo 1:")
        println("    UL (110.8, 1080.4)")
        println("    UR (749.7, 1078.2)")
        println("    LR (751.5, 1973.6)")
        println("    LL (124.7, 1962.3)")
        println("  Photo 2:")
        println("    UL (792.7, 1063.3)")
        println("    UR (1437.6, 1072.7)")
        println("    LR (1401.1, 1942.9)")
        println("    LL (782.2, 1954.2)")
        println("  Photo 3:")
        println("    UL (94.8, 49.4)")
        println("    UR (761.2, 50.2)")
        println("    LR (752.7, 996.7)")
        println("    LL (111.4, 990.6)")
        println("  Photo 4:")
        println("    UL (782.0, 98.6)")
        println("    UR (1437.6, 100.4)")
        println("    LR (1439.7, 1025.1)")
        println("    LL (794.0, 1036.9)")
    }
}
