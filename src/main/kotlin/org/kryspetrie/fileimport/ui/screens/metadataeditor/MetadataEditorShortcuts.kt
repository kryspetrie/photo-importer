package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed

enum class MetadataEditorShortcutAction {
    APPLY_MULTI_EDIT,
    LOCATION_PICKER,
    TOGGLE_FACE_TAGGING,
    FOCUS_KEYWORDS,
    TOGGLE_BROWSER_DRAWER,
}

fun metadataEditorShortcutActionForKey(key: Key, isMeta: Boolean): MetadataEditorShortcutAction? {
    if (!isMeta) return null
    return when (key) {
        Key.Enter -> MetadataEditorShortcutAction.APPLY_MULTI_EDIT
        Key.L -> MetadataEditorShortcutAction.LOCATION_PICKER
        Key.T -> MetadataEditorShortcutAction.TOGGLE_FACE_TAGGING
        Key.F -> MetadataEditorShortcutAction.FOCUS_KEYWORDS
        Key.B -> MetadataEditorShortcutAction.TOGGLE_BROWSER_DRAWER
        else -> null
    }
}

fun metadataEditorShortcutAction(
    keyEvent: KeyEvent,
    isMeta: Boolean,
): MetadataEditorShortcutAction? = metadataEditorShortcutActionForKey(keyEvent.key, isMeta)

fun isMetadataEditorMetaKey(keyEvent: KeyEvent): Boolean = isCtrlPressed(keyEvent)
