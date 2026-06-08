package org.kryspetrie.fileimport.infrastructure.wizard

/** Wizard mode for the overview/refinement screens. */
enum class WizardMode {
    /** Default mode - select, move, zoom */
    NORMAL,
    /** 4-point bounding box creation mode */
    FOUR_POINT,
    /** Click-to-add rectangular box mode */
    ADD_BOX,
    /** Zoomed refinement of a single box */
    REFINEMENT,
}
