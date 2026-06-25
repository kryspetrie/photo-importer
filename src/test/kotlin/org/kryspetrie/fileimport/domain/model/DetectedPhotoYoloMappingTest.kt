package org.kryspetrie.fileimport.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DetectedPhotoYoloMappingTest {

    @Test
    fun `fromYoloKeypoints maps LL UL UR LR to bottomLeft topLeft topRight bottomRight`() {
        val kp0 = PhotoCorner(x = 100f, y = 400f) // LL = bottom-left
        val kp1 = PhotoCorner(x = 100f, y = 100f) // UL = top-left
        val kp2 = PhotoCorner(x = 400f, y = 100f) // UR = top-right
        val kp3 = PhotoCorner(x = 400f, y = 400f) // LR = bottom-right

        val photo =
            DetectedPhoto.fromYoloKeypoints(
                kp0 = kp0,
                kp1 = kp1,
                kp2 = kp2,
                kp3 = kp3,
                confidence = 0.92f,
                detectionMode = DetectionMode.PERSPECTIVE_CORRECTION,
            )

        // Verify YOLO → petrie mapping
        assertEquals(kp1, photo.topLeft, "kp1 (UL) should map to topLeft")
        assertEquals(kp2, photo.topRight, "kp2 (UR) should map to topRight")
        assertEquals(kp0, photo.bottomLeft, "kp0 (LL) should map to bottomLeft")
        assertEquals(kp3, photo.bottomRight, "kp3 (LR) should map to bottomRight")
        assertEquals(0.92f, photo.confidence, "confidence should be preserved")
        assertEquals(DetectionMode.PERSPECTIVE_CORRECTION, photo.detectionMode)
    }

    @Test
    fun `toYoloKeypoints returns LL UL UR LR order`() {
        val photo =
            DetectedPhoto(
                topLeft = PhotoCorner(x = 100f, y = 100f),
                topRight = PhotoCorner(x = 400f, y = 100f),
                bottomLeft = PhotoCorner(x = 100f, y = 400f),
                bottomRight = PhotoCorner(x = 400f, y = 400f),
            )

        val keypoints = photo.toYoloKeypoints()

        assertEquals(4, keypoints.size)
        assertEquals(photo.bottomLeft, keypoints[0], "kp0 should be bottomLeft (LL)")
        assertEquals(photo.topLeft, keypoints[1], "kp1 should be topLeft (UL)")
        assertEquals(photo.topRight, keypoints[2], "kp2 should be topRight (UR)")
        assertEquals(photo.bottomRight, keypoints[3], "kp3 should be bottomRight (LR)")
    }

    @Test
    fun `round-trip fromYoloKeypoints then toYoloKeypoints returns original corners`() {
        val kp0 = PhotoCorner(x = 50f, y = 300f)
        val kp1 = PhotoCorner(x = 50f, y = 50f)
        val kp2 = PhotoCorner(x = 350f, y = 50f)
        val kp3 = PhotoCorner(x = 350f, y = 300f)

        val photo = DetectedPhoto.fromYoloKeypoints(kp0, kp1, kp2, kp3)
        val result = photo.toYoloKeypoints()

        assertEquals(kp0, result[0], "Round-trip kp0 (LL)")
        assertEquals(kp1, result[1], "Round-trip kp1 (UL)")
        assertEquals(kp2, result[2], "Round-trip kp2 (UR)")
        assertEquals(kp3, result[3], "Round-trip kp3 (LR)")
    }

    @Test
    fun `round-trip toYoloKeypoints then fromYoloKeypoints returns original photo corners`() {
        val original =
            DetectedPhoto(
                topLeft = PhotoCorner(x = 10f, y = 10f),
                topRight = PhotoCorner(x = 200f, y = 10f),
                bottomLeft = PhotoCorner(x = 10f, y = 300f),
                bottomRight = PhotoCorner(x = 200f, y = 300f),
            )

        val keypoints = original.toYoloKeypoints()
        val restored =
            DetectedPhoto.fromYoloKeypoints(
                kp0 = keypoints[0],
                kp1 = keypoints[1],
                kp2 = keypoints[2],
                kp3 = keypoints[3],
            )

        assertEquals(original.topLeft, restored.topLeft)
        assertEquals(original.topRight, restored.topRight)
        assertEquals(original.bottomLeft, restored.bottomLeft)
        assertEquals(original.bottomRight, restored.bottomRight)
    }

    @Test
    fun `fromYoloKeypoints default values`() {
        val photo =
            DetectedPhoto.fromYoloKeypoints(
                kp0 = PhotoCorner(0f, 0f),
                kp1 = PhotoCorner(0f, 0f),
                kp2 = PhotoCorner(0f, 0f),
                kp3 = PhotoCorner(0f, 0f),
            )

        assertEquals(1.0f, photo.confidence, "Default confidence should be 1.0")
        assertEquals(
            DetectionMode.PERSPECTIVE_CORRECTION,
            photo.detectionMode,
            "Default detectionMode should be PERSPECTIVE_CORRECTION",
        )
        assertEquals(
            true,
            photo.applyPerspectiveCorrection,
            "Default applyPerspectiveCorrection should be true",
        )
        assertEquals(RotationAngle.NONE, photo.rotation, "Default rotation should be NONE")
    }

    @Test
    fun `DetectionMode enums have correct properties`() {
        assertEquals(false, DetectionMode.COMPUTER_VISION.usesYolo)
        assertEquals(true, DetectionMode.COMPUTER_VISION.providesCorners)

        assertEquals(true, DetectionMode.BOUNDING_BOX.usesYolo)
        assertEquals(false, DetectionMode.BOUNDING_BOX.providesCorners)

        assertEquals(true, DetectionMode.PERSPECTIVE_CORRECTION.usesYolo)
        assertEquals(true, DetectionMode.PERSPECTIVE_CORRECTION.providesCorners)

        // HYBRID mode removed — PERSPECTIVE_CORRECTION is the recommended YOLO mode
    }

    @Test
    fun `DetectedPhoto defaults have COMPUTER_VISION mode and 1f confidence`() {
        val photo = DetectedPhoto()
        assertEquals(1.0f, photo.confidence)
        assertEquals(DetectionMode.COMPUTER_VISION, photo.detectionMode)
    }
}
