package org.kryspetrie.fileimport.ui.screens.wizard

import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.infrastructure.adapter.correctPerspective
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration

/**
 * Crops and rotates a bounding box from an image using perspective correction and rotation.
 *
 * @param image The source image to crop from.
 * @param box The bounding box defining the crop region.
 * @param config The photo configuration specifying rotation and correction settings.
 * @param perspectiveService The perspective correction service.
 * @return The cropped and rotated image, or null if correction/rotation fails.
 */
fun cropAndRotateBoundingBox(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoScanConfiguration,
    perspectiveService: PerspectiveCorrectionPort,
): BufferedImage? {
    return try {
        val detectedPhoto = boxToDetectedPhoto(box)
        val corrected = perspectiveService.correctPerspective(image, detectedPhoto)
        if (config.rotationDegrees != 0) {
            rotateBufferedImage(corrected, rotationFromDegrees(config.rotationDegrees))
        } else {
            corrected
        }
    } catch (_: Exception) {
        null
    }
}

/** Rotates a [BufferedImage] by the given [RotationAngle]. */
fun rotateBufferedImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
    if (rotation == RotationAngle.NONE) return image
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
    val graphics = rotated.createGraphics()
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
        RotationAngle.NONE -> {
            // No rotation
        }
    }
    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()
    return rotated
}

/** Converts degrees (0, 90, 180, 270) to [RotationAngle]. */
fun rotationFromDegrees(degrees: Int): RotationAngle =
    when (degrees) {
        90 -> RotationAngle.CW_90
        180 -> RotationAngle.CW_180
        270 -> RotationAngle.CCW_90
        -90 -> RotationAngle.CCW_90
        else -> RotationAngle.NONE
    }

/** Converts a [BoundingBox] to a [DetectedPhoto] for perspective correction. */
fun boxToDetectedPhoto(
    box: BoundingBox,
    applyPerspectiveCorrection: Boolean = true,
    rotationDegrees: Int = 0,
): DetectedPhoto =
    DetectedPhoto(
        topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
        topRight = PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
        bottomLeft =
            PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
        bottomRight =
            PhotoCorner(box.corners.bottomRight.x.toFloat(), box.corners.bottomRight.y.toFloat()),
        applyPerspectiveCorrection = applyPerspectiveCorrection,
        rotation = rotationFromDegrees(rotationDegrees),
    )
