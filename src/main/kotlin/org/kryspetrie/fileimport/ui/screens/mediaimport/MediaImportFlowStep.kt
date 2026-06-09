package org.kryspetrie.fileimport.ui.screens.mediaimport

enum class MediaImportFlowStep {
    SETUP,
    SCANNING,
    SELECTING,
    INDEXING,
    CHECKING_DUPES,
    DUPE_REVIEW,
    PREVIEW,
    IMPORTING,
    COMPLETE,
}
