package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Corner coordinates for a detected photo.
 *
 * Represents a single corner point in the image coordinate system.
 *
 * @property x X coordinate (horizontal position from left)
 * @property y Y coordinate (vertical position from top)
 */
@Serializable
data class PhotoCorner(
    /** X coordinate (horizontal position from left) */
    val x: Float = 0f,

    /** Y coordinate (vertical position from top) */
    val y: Float = 0f,
) {
    companion object {
        fun create(x: Int, y: Int): PhotoCorner = PhotoCorner(x = x.toFloat(), y = y.toFloat())
    }
}

/**
 * Rotation angle for photo export.
 *
 * Applied after perspective correction to rotate the final image.
 */
enum class RotationAngle(val degrees: Int, val radians: Double) {
    NONE(0, 0.0),
    CW_90(90, Math.PI / 2),
    CW_180(180, Math.PI),
    CCW_90(-90, -Math.PI / 2);

    /** Returns the next clockwise rotation. */
    fun rotateCW(): RotationAngle =
        when (this) {
            NONE -> CW_90
            CW_90 -> CW_180
            CW_180 -> CCW_90
            CCW_90 -> NONE
        }

    /** Returns the next counter-clockwise rotation. */
    fun rotateCCW(): RotationAngle =
        when (this) {
            NONE -> CCW_90
            CCW_90 -> CW_180
            CW_180 -> CW_90
            CW_90 -> NONE
        }
}

/**
 * Detected photo within a scanned image.
 *
 * Represents one photograph detected in a scan, with its bounding box and associated metadata
 * configuration.
 *
 * @property id Unique identifier for this detected photo
 * @property topLeft Coordinates of the top-left corner of the photo bounding box
 * @property topRight Coordinates of the top-right corner of the photo bounding box
 * @property bottomLeft Coordinates of the bottom-left corner of the photo bounding box
 * @property bottomRight Coordinates of the bottom-right corner of the photo bounding box
 * @property configuration Metadata configuration for this photo
 * @property applyPerspectiveCorrection Whether to apply perspective correction (true) or just crop
 *   (false)
 * @property rotation Rotation angle for the output image
 */
@Serializable
data class DetectedPhoto(
    /** Unique identifier for this detected photo */
    val id: String = DomainDefaults.generateId(),

    /** Top-left corner of the bounding box (x, y) */
    val topLeft: PhotoCorner = PhotoCorner(),

    /** Top-right corner of the bounding box (x, y) */
    val topRight: PhotoCorner = PhotoCorner(),

    /** Bottom-left corner of the bounding box (x, y) */
    val bottomLeft: PhotoCorner = PhotoCorner(),

    /** Bottom-right corner of the bounding box (x, y) */
    val bottomRight: PhotoCorner = PhotoCorner(),

    /** Metadata configuration for this photo */
    val configuration: PhotoScanConfiguration = PhotoScanConfiguration(),

    /**
     * Whether to apply perspective correction (true) or just crop with axis-aligned rectangle
     * (false)
     */
    val applyPerspectiveCorrection: Boolean = true,

    /** Rotation angle for the output image */
    val rotation: RotationAngle = RotationAngle.NONE,
) {
    /** Get the width of the detected photo in pixels. */
    fun getWidth(): Int {
        return kotlin.math.abs(topRight.x.toInt() - topLeft.x.toInt())
    }

    /** Get the height of the detected photo in pixels. */
    fun getHeight(): Int {
        return kotlin.math.abs(bottomLeft.y.toInt() - topLeft.y.toInt())
    }

    /** Get the bounding rectangle of the detected photo. */
    fun getBounds(): PhotoBounds {
        val xCoords =
            listOf(
                topLeft.x.toInt(),
                topRight.x.toInt(),
                bottomLeft.x.toInt(),
                bottomRight.x.toInt(),
            )
        val yCoords =
            listOf(
                topLeft.y.toInt(),
                topRight.y.toInt(),
                bottomLeft.y.toInt(),
                bottomRight.y.toInt(),
            )

        return PhotoBounds(
            minX = xCoords.minOrNull() ?: 0,
            maxX = xCoords.maxOrNull() ?: 0,
            minY = yCoords.minOrNull() ?: 0,
            maxY = yCoords.maxOrNull() ?: 0,
        )
    }

    /** Returns a new DetectedPhoto with perspective correction toggle. */
    fun withPerspectiveCorrection(enabled: Boolean): DetectedPhoto =
        copy(applyPerspectiveCorrection = enabled)

    /** Returns a new DetectedPhoto rotated clockwise. */
    fun rotateCW(): DetectedPhoto = copy(rotation = rotation.rotateCW())

    /** Returns a new DetectedPhoto rotated counter-clockwise. */
    fun rotateCCW(): DetectedPhoto = copy(rotation = rotation.rotateCCW())

    /** Returns a new DetectedPhoto with specific rotation. */
    fun withRotation(angle: RotationAngle): DetectedPhoto = copy(rotation = angle)
}

/** Bounding rectangle for a detected photo. */
@Serializable
data class PhotoBounds(
    /** Minimum X coordinate */
    val minX: Int,

    /** Maximum X coordinate */
    val maxX: Int,

    /** Minimum Y coordinate */
    val minY: Int,

    /** Maximum Y coordinate */
    val maxY: Int,
) {
    /** Get the width */
    fun getWidth(): Int = maxX - minX

    /** Get the height */
    fun getHeight(): Int = maxY - minY
}
