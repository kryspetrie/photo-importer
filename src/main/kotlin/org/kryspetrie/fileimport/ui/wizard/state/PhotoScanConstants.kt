package org.kryspetrie.fileimport.ui.wizard.state

/**
 * Constants for the Photo Scan workflow.
 *
 * Centralizes magic numbers and configuration values that were previously scattered throughout the
 * codebase.
 */
object PhotoScanConstants {

    // ==================== BOX CREATION ====================

    /**
     * Minimum box size in pixels.
     *
     * Bounding boxes smaller than this are rejected to prevent:
     * - Creating boxes that are essentially noise
     * - Exporting useless tiny images
     * - Performance issues with very small regions
     */
    const val MIN_BOX_SIZE_PIXELS = 50.0

    /**
     * Default box width as a ratio of image width.
     *
     * When creating a box by clicking (e.g., "Add Box" mode), the box will be 30% of the image
     * width by default.
     */
    const val DEFAULT_BOX_WIDTH_RATIO = 0.3

    /**
     * Default box aspect ratio (width / height).
     *
     * Default is 1.5 which corresponds to a 3:2 aspect ratio, common for photos.
     */
    const val DEFAULT_BOX_ASPECT_RATIO = 1.5

    // ==================== EXPORT ====================

    /**
     * Estimated disk space required per photo in bytes.
     *
     * This is a conservative estimate for high-resolution exports. Used for disk space validation
     * before export.
     */
    const val ESTIMATED_SPACE_PER_PHOTO_BYTES = 50L * 1024 * 1024 // 50MB

    /**
     * Minimum disk space warning threshold.
     *
     * If free space is below this percentage of required space, show a warning.
     */
    const val MIN_FREE_SPACE_WARNING_PERCENT = 1.10 // 110% (10% buffer)

    /** Default JPEG quality for exports. */
    const val DEFAULT_JPEG_QUALITY = 0.90f

    // ==================== ZOOM ====================

    /** Minimum zoom level (most zoomed out). */
    const val MIN_ZOOM_LEVEL = 0.1

    /** Maximum zoom level (most zoomed in). */
    const val MAX_ZOOM_LEVEL = 10.0

    /** Default zoom step size (multiplier). */
    const val ZOOM_STEP = 1.25

    /** Zoom step size for keyboard shortcuts. */
    const val KEYBOARD_ZOOM_STEP = 1.0

    // ==================== UI ====================

    /**
     * Corner hit radius for mouse interactions.
     *
     * How close (in screen pixels) the cursor must be to a corner to register as a hit.
     */
    const val CORNER_HIT_RADIUS_PX = 30.0

    /** Default viewport width for zoom calculations. */
    const val DEFAULT_VIEWPORT_WIDTH = 800.0

    /** Default viewport height for zoom calculations. */
    const val DEFAULT_VIEWPORT_HEIGHT = 600.0

    // ==================== KEYBOARD ====================

    /** Arrow key movement step in pixels (normal). */
    const val ARROW_KEY_STEP = 1.0

    /** Arrow key movement step in pixels (with Shift modifier). */
    const val SHIFT_ARROW_KEY_STEP = 10.0

    /** Keyboard pan step in pixels (normal). */
    const val KEYBOARD_PAN_STEP = 50.0

    /** Keyboard pan step in pixels (with Shift modifier). */
    const val SHIFT_KEYBOARD_PAN_STEP = 100.0

    /** Rotation step in degrees. */
    const val ROTATION_STEP_DEGREES = 5.0

    /** Box expansion/contraction factor. */
    const val EXPANSION_FACTOR = 1.05

    // ==================== IMAGE PROCESSING ====================

    /**
     * Maximum image dimension for performance.
     *
     * Images larger than this will be downsampled for preview display. The full resolution is still
     * used for export.
     */
    const val MAX_PREVIEW_DIMENSION = 2000

    /** Image sampling quality (ANTIALIASING vs. NONE). */
    const val USE_ANTIALIASING_FOR_SAMPLING = true

    // ==================== PERSISTENCE ====================

    /** Maximum number of recent destinations to remember. */
    const val MAX_RECENT_DESTINATIONS = 5

    /** Maximum number of undo operations to store per box. */
    const val MAX_UNDO_OPERATIONS = 50
}
