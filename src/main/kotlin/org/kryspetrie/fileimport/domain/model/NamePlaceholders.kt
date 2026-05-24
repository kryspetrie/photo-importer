package org.kryspetrie.fileimport.domain.model

/**
 * Available placeholders for folder and filename patterns.
 *
 * Placeholders are replaced with actual values during import. Different placeholders are available
 * for folder patterns vs. filename patterns.
 *
 * @see FolderPresets Examples using folder placeholders
 * @see FilenamePresets Examples using filename placeholders
 */
object NamePlaceholders {
    /** Placeholders available in folder patterns. */
    val folderPlaceholders =
        mapOf(
            "{yyyy-MM-dd}" to "Year-Month-Day",
            "{yyyy-MM}" to "Year-Month",
            "{yyyy}" to "Year (4 digits)",
            "{yy}" to "Year (2 digits)",
            "{MM}" to "Month (01-12)",
            "{dd}" to "Day (01-31)",
            "{HH}" to "Hour (00-23)",
            "{mm}" to "Minute (00-59)",
            "{camera}" to "Camera model",
            "{make}" to "Camera make",
            "{lens}" to "Lens model",
            "{type}" to "Media type (Photos / Videos)",
        )

    /** Placeholders available in filename patterns (includes EXIF data). */
    val filePlaceholders =
        mapOf(
            "{original}" to "Original filename",
            "{ext}" to "File extension",
            "{yyyy}" to "Year",
            "{MM}" to "Month",
            "{dd}" to "Day",
            "{HH}" to "Hour",
            "{mm}" to "Minute",
            "{ss}" to "Second",
            "{camera}" to "Camera model",
            "{make}" to "Camera make",
            "{lens}" to "Lens model",
            "{iso}" to "ISO value",
            "{aperture}" to "Aperture (f/x)",
            "{shutter}" to "Shutter speed",
            "{focal}" to "Focal length (mm)",
            "{focal35}" to "35mm equiv. focal length",
            "{width}" to "Image/video width (px)",
            "{height}" to "Image/video height (px)",
            "{counter}" to "Sequential counter",
            "{type}" to "Media type (photo / video)",
            "{duration}" to "Video duration (e.g. 1m30s)",
            "{fps}" to "Video frame rate",
            "{codec}" to "Video codec",
            "{date}" to "Date taken (YYYYMMDD)",
            "{time}" to "Time taken (HHMMSS)",
            "{datetime}" to "Date & time (YYYYMMDD_HHMMSS)",
            "{date_orig}" to "Date original (YYYYMMDD)",
            "{make_raw}" to "Camera make (raw tag)",
            "{model_raw}" to "Camera model (raw tag)",
            "{lens_model}" to "Lens model (raw tag)",
            "{iso_speed}" to "ISO speed (raw tag)",
            "{f_number}" to "F-number (aperture raw)",
            "{exposure}" to "Exposure time (raw)",
            "{focal_length}" to "Focal length (raw mm)",
            "{gps_lat}" to "GPS latitude",
            "{gps_lon}" to "GPS longitude",
            "{gps_alt}" to "GPS altitude",
            "{orientation}" to "Orientation (1-8)",
            "{flash}" to "Flash (fired/not fired)",
            "{white_balance}" to "White balance (AWB/custom)",
            "{exposure_program}" to "Exposure program",
            "{metering_mode}" to "Metering mode",
            "{color_space}" to "Color space (sRGB/Adobe)",
            "{software}" to "Software",
            "{artist}" to "Artist/author",
            "{copyright}" to "Copyright",
            "{rating}" to "Rating (1-5 stars)",
            "{label}" to "Label/tag",
            "{comment}" to "Comment/description",
        )

    /** Standard EXIF metadata field names. */
    object ExifFields {
        const val DATE_ORIGINAL = "DateTimeOriginal"
        const val DATE_DIGITIZED = "DateTimeDigitized"
        const val DATE_MODIFIED = "DateTime"
        const val MAKE = "Make"
        const val MODEL = "Model"
        const val LENS_MODEL = "LensModel"
        const val LENS_MAKE = "LensMake"
        const val SOFTWARE = "Software"
        const val ARTIST = "Artist"
        const val COPYRIGHT = "Copyright"
        const val IMAGE_DESCRIPTION = "ImageDescription"
        const val USER_COMMENT = "UserComment"
        const val ORIENTATION = "Orientation"
        const val X_RESOLUTION = "XResolution"
        const val Y_RESOLUTION = "YResolution"
        const val RESOLUTION_UNIT = "ResolutionUnit"
        const val EXPOSURE_TIME = "ExposureTime"
        const val F_NUMBER = "FNumber"
        const val ISO_SPEED_RATINGS = "ISOSpeedRatings"
        const val FOCAL_LENGTH = "FocalLength"
        const val FOCAL_LENGTH_35MM = "FocalLengthIn35mmFilm"
        const val EXPOSURE_PROGRAM = "ExposureProgram"
        const val METING_MODE = "MeteringMode"
        const val FLASH = "Flash"
        const val WHITE_BALANCE = "WhiteBalance"
        const val COLOR_SPACE = "ColorSpace"
        const val GPS_LAT_REF = "GPSLatitudeRef"
        const val GPS_LATITUDE = "GPSLatitude"
        const val GPS_LON_REF = "GPSLongitudeRef"
        const val GPS_LONGITUDE = "GPSLongitude"
        const val GPS_ALT_REF = "GPSAltitudeRef"
        const val GPS_ALTITUDE = "GPSAltitude"
        const val RATING = "Rating"
        const val LABEL = "XPKeywords"
        const val IMAGE_WIDTH = "PixelXDimension"
        const val IMAGE_HEIGHT = "PixelYDimension"
        const val FLASHPIX_VERSION = "FlashpixVersion"
        const val EXIF_VERSION = "ExifVersion"
    }
}
