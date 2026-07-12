package org.kryspetrie.fileimport.ui.wizard.state

/** Steps in the photo scan wizard flow. */
enum class WizardStep {
    IMPORT, // Mode selection
    OVERVIEW, // All boxes visible
    REFINEMENT, // Zoomed single box (redirects to OVERVIEW - inline refinement)
    SUMMARY, // Crop & rotate grid view
    EDIT, // Edit screen: rotation OR metadata (user chooses mode)
    PROCESSING, // Export in progress
    COMPLETE, // Done — post-export completion page
}
