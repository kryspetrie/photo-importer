package org.kryspetrie.fileimport.domain.model

import org.apache.commons.imaging.common.RationalNumber

/**
 * Pure value-transformation functions for resolving EXIF metadata values.
 *
 * These functions perform input→output transformations with no I/O or side effects. They belong in
 * the domain layer because they encode business rules about how configuration values map to
 * EXIF-formatted data.
 */
object ExifValueResolver {

    /**
     * Resolves the effective keywords value from configuration.
     *
     * Since [PhotoScanConfiguration.keywords] is always present (non-null), this simply returns it
     * if non-blank, otherwise null.
     *
     * @return The resolved keyword string, or null if no keywords are set.
     */
    fun resolveKeywords(config: PhotoScanConfiguration): String? {
        return if (config.keywords.isNotBlank()) {
            config.keywords
        } else {
            null
        }
    }

    /**
     * Resolves the DateTimeOriginal string from configuration fields.
     *
     * Uses [PhotoScanConfiguration.originalDate] if non-blank, otherwise derives from
     * [PhotoScanConfiguration.year] only.
     *
     * Format returned: "YYYY:MM:DD HH:MM:SS" (EXIF format, colons in date part).
     *
     * @return EXIF-formatted date string, or null if no date override
     */
    fun resolveDateOriginal(config: PhotoScanConfiguration): String? {
        // originalDate takes priority (full date string)
        if (config.originalDate.isNotBlank()) {
            return formatDateToExif(config.originalDate)
        }
        // Year-only override
        if (config.year.isNotBlank()) {
            return "${config.year}:01:01 00:00:00"
        }
        return null
    }

    /**
     * Converts a date string to EXIF format.
     *
     * Accepts "YYYY-MM-DD HH:MM:SS" or "YYYY-MM-DD" and converts to "YYYY:MM:DD HH:MM:SS".
     */
    fun formatDateToExif(dateStr: String): String {
        // Normalize: replace dashes in the date portion with colons
        val parts = dateStr.trim().split(" ", limit = 2)
        val datePart = parts[0].replace("-", ":")
        val timePart = parts.getOrElse(1) { "00:00:00" }
        return "$datePart $timePart"
    }

    /** Parses a focal length string (e.g. "50mm", "50", "24mm") to a floating-point value in mm. */
    fun parseFocalLength(value: String): Double? {
        return value.trim().removeSuffix("mm").removeSuffix("MM").trim().toDoubleOrNull()
    }

    /** Parses an aperture string (e.g. "f/2.8", "2.8", "F2.8") to a floating-point f-number. */
    fun parseAperture(value: String): Double? {
        val cleaned =
            value.trim().removePrefix("f/").removePrefix("F/").removePrefix("f").removePrefix("F")
        return cleaned.toDoubleOrNull()
    }

    /**
     * Parses a shutter speed string to a RationalNumber for EXIF ExposureTime.
     *
     * Accepts formats: "1/125" (fraction), "0.008" (decimal seconds), "125" (1/N).
     */
    fun parseShutterSpeed(value: String): RationalNumber? {
        val trimmed = value.trim()
        // Fraction format: "1/125"
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 2) {
                val num = parts[0].toIntOrNull() ?: return null
                val den = parts[1].toIntOrNull() ?: return null
                if (den != 0) return RationalNumber(num, den)
            }
        }
        // Decimal format: "0.008"
        val decimal = trimmed.toDoubleOrNull()
        if (decimal != null && decimal > 0) {
            return RationalNumber.valueOf(decimal)
        }
        // Integer "1/N" format: "125" means 1/125
        val intVal = trimmed.toIntOrNull()
        if (intVal != null && intVal > 0) {
            return RationalNumber(1, intVal)
        }
        return null
    }

    /**
     * Converts a decimal degree value to GPS rationals (degrees, minutes, seconds). EXIF GPS
     * latitude/longitude fields require 3 RationalNumber values: degrees, minutes, seconds.
     *
     * @param decimalDegrees Absolute value of latitude or longitude in decimal degrees
     * @return Array of 3 RationalNumbers representing [degrees, minutes, seconds]
     */
    fun decimalToGpsRationals(decimalDegrees: Double): Array<RationalNumber> {
        val degrees = decimalDegrees.toInt()
        val minutesDecimal = (decimalDegrees - degrees) * 60.0
        val minutes = minutesDecimal.toInt()
        val seconds = (kotlin.math.round((minutesDecimal - minutes) * 60.0 * 10000.0)).toInt()
        return arrayOf(
            RationalNumber(degrees, 1),
            RationalNumber(minutes, 1),
            RationalNumber(seconds, 10000),
        )
    }

    /**
     * Applies a tri-state field resolution strategy for EXIF metadata overrides.
     * - [OverrideState.NULL_OUT]: Remove the field ([onNullOut])
     * - [OverrideState.OVERRIDE]: Set the field to [value] ([onOverride]), if non-blank
     * - [OverrideState.KEEP_SOURCE] or null: Legacy behavior — apply [legacyAction] if
     *   [legacyPredicate]
     *
     * @param overrideState The tri-state override for this field (null = backward compat)
     * @param value The current field value (used for OVERRIDE validation)
     * @param onNullOut Action to remove the field from EXIF
     * @param onOverride Action to set the field value in EXIF (receives the string value)
     * @param legacyPredicate Whether legacy behavior should apply
     * @param legacyAction Action to take under legacy behavior
     */
    fun applyTriStateField(
        overrideState: OverrideState?,
        value: String,
        onNullOut: () -> Unit,
        onOverride: (String) -> Unit,
        legacyPredicate: Boolean,
        legacyAction: () -> Unit,
    ) {
        when (overrideState) {
            OverrideState.NULL_OUT -> onNullOut()
            OverrideState.OVERRIDE -> {
                if (value.isNotBlank()) onOverride(value)
            }
            OverrideState.KEEP_SOURCE,
            null -> {
                if (legacyPredicate) legacyAction()
            }
        }
    }
}
