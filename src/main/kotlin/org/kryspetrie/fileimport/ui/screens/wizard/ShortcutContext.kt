package org.kryspetrie.fileimport.ui.screens.wizard

enum class ShortcutContext {
    /** Import screen — limited shortcuts */
    IMPORT,
    /** Overview/Refinement screen — full shortcuts */
    CANVAS,
    /** Summary screen — navigation shortcuts only */
    SUMMARY,
    /** Bulk metadata editor */
    METADATA_EDITOR,
    /** App-wide shortcuts (menu Help dialog) */
    APP,
}
