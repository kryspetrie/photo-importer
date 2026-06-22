package org.kryspetrie.fileimport.infrastructure.photoscan.yolo

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.kryspetrie.fileimport.infrastructure.adapter.ClasspathModelResourceAdapter

/**
 * Runs face detection on test images and prints the bounding boxes.
 * This is a "gold standard" extraction test — run manually to capture reference coordinates
 * for [FaceDetectionIntegrationTest].
 *
 * Remove `@Disabled` temporarily to regenerate reference coordinates after model changes.
 */
@Disabled("Gold standard extraction — run manually to capture reference coordinates")
class FaceDetectionGoldTest {

    companion object {
        private var service: YoloFaceDetectionService? = null
        private var available = false

        @JvmStatic
        @BeforeAll
        fun setUp() {
            val adapter = ClasspathModelResourceAdapter()
            available = adapter.isFaceDetectionModelAvailable()
            if (available) {
                val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
                val opts = ai.onnxruntime.OrtSession.SessionOptions()
                val session = env.createSession(adapter.loadFaceDetectionModel(), opts)
                service = YoloFaceDetectionService(env, session)
            }
        }

        @JvmStatic
        fun sessionAvailable(): Boolean = available
    }

    @Test
    @EnabledIf("sessionAvailable")
    fun `print face detections for faces-01`() {
        val image = loadTestImage("faces-01") ?: return
        val results = service!!.detectFaces(image)
        println("=== faces-01.jpg (${image.width}x${image.height}) ===")
        println("Faces detected: ${results.size}")
        results.forEachIndexed { i, det ->
            println("  Face $i: x1=${det.x1}, y1=${det.y1}, x2=${det.x2}, y2=${det.y2}, conf=${det.confidence}")
        }
    }

    @Test
    @EnabledIf("sessionAvailable")
    fun `print face detections for faces-02`() {
        val image = loadTestImage("faces-02") ?: return
        val results = service!!.detectFaces(image)
        println("=== faces-02.jpg (${image.width}x${image.height}) ===")
        println("Faces detected: ${results.size}")
        results.forEachIndexed { i, det ->
            println("  Face $i: x1=${det.x1}, y1=${det.y1}, x2=${det.x2}, y2=${det.y2}, conf=${det.confidence}")
        }
    }

    private fun loadTestImage(name: String): BufferedImage? {
        val stream = javaClass.classLoader.getResourceAsStream("org/kryspetrie/fileimport/application/$name.jpg")
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