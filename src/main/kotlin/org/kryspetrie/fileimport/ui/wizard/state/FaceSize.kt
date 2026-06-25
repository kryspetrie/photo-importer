package org.kryspetrie.fileimport.ui.wizard.state

/**
 * Preset sizes for face regions. The [radius] value represents the circle radius as a fraction of
 * image height (0.0-1.0). We store w=h=radius*2 in MWG-RS format (the bounding box of the circle),
 * but display as a circle.
 */
enum class FaceSize(val displayName: String, val radius: Double) {
    SMALL("S", 0.04),
    MEDIUM("M", 0.07),
    LARGE("L", 0.12);

    /** The MWG-RS w/h values for this circle size (diameter of the circle). */
    val diameter: Double
        get() = radius * 2.0

    companion object {
        val DEFAULT = MEDIUM
    }
}
