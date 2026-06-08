package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * A face/subject region within a photo, in normalized (0.0-1.0) coordinates.
 *
 * Represents a rectangular or circular region identifying a person, pet, or object in a photo.
 * Coordinates are fractions of the image dimensions (0.0-1.0), not pixels.
 *
 * MWG-RS region types: "Face", "Pet", "Body", "Object".
 *
 * @property name Person/subject name for this face region
 * @property type Region type: "Face", "Pet", "Body", or "Object" (MWG-RS types)
 * @property x Center X as fraction of image width (0.0-1.0)
 * @property y Center Y as fraction of image height (0.0-1.0)
 * @property w Width as fraction of image width (0.0-1.0) — for circular faces, equals diameter
 * @property h Height as fraction of image height (0.0-1.0) — for circular faces, equals diameter
 */
@Serializable
data class FaceRegion(
    val name: String = "",
    val type: String = "Face",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
)

/**
 * MWG-RS region types. Used to categorize what a region represents. See:
 * https://web.archive.org/web/20180921201257/http://www.metadataworkinggroup.org/pdf/mwg_guidance.pdf
 */
enum class RegionType(val displayName: String, val mwgRsValue: String) {
    FACE("Face", "Face"),
    PET("Pet", "Pet"),
    BODY("Body", "Body"),
    OBJECT("Object", "Object");

    companion object {
        /** Parses a region type string, defaulting to FACE for unknown values. */
        fun fromMwgRs(value: String): RegionType =
            entries.find { it.mwgRsValue.equals(value, ignoreCase = true) } ?: FACE
    }
}
