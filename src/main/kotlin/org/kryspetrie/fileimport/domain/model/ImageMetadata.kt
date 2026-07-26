package org.kryspetrie.fileimport.domain.model

import java.time.LocalDateTime

data class ImageMetadata(
    // Common metadata (photos + video)
    val dateTimeOriginal: LocalDateTime? = null,
    val dateTimeDigitized: LocalDateTime? = null,
    val dateTimeModified: LocalDateTime? = null,
    val make: String? = null,
    val model: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val software: String? = null,
    val copyright: String? = null,
    val artist: String? = null,
    val description: String? = null,

    // Photo-specific
    val lensModel: String? = null,
    val focalLength: Float? = null,
    val focalLength35mm: Int? = null,
    val aperture: Float? = null,
    val shutterSpeed: String? = null,
    val iso: Int? = null,
    val exposureProgram: String? = null,
    val meteringMode: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val exposureCompensation: Float? = null,
    val orientation: Int? = null,
    val colorSpace: String? = null,

    // IPTC / location fields (read from source file for display)
    val keywords: List<String>? = null,
    val subLocation: String? = null,
    val city: String? = null,
    val provinceState: String? = null,
    val countryName: String? = null,

    // Video-specific
    val durationSeconds: Double? = null,
    val frameRate: Double? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val bitrate: Long? = null,
    val rotation: Int? = null,
) {
    val cameraModel: String
        get() = listOfNotNull(make, model).joinToString(" ")

    val hasGpsData: Boolean
        get() = latitude != null && longitude != null

    val lensInfo: String
        get() = lensModel ?: "Unknown"

    val cameraMake: String
        get() = make ?: "Unknown"

    val resolution: String?
        get() =
            if (imageWidth != null && imageHeight != null) "${imageWidth}\u00D7${imageHeight}"
            else null

    val durationFormatted: String?
        get() {
            val secs = durationSeconds ?: return null
            val h = (secs / 3600).toInt()
            val m = ((secs % 3600) / 60).toInt()
            val s = (secs % 60).toInt()
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
}
