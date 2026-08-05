package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.input.key.Key

/** Tab index 0–4 for Ctrl+1…5, or null when [key] is not a tab digit. */
fun appTabIndexForCtrlDigit(key: Key): Int? =
    when (key) {
        Key.One -> 0
        Key.Two -> 1
        Key.Three -> 2
        Key.Four -> 3
        Key.Five -> 4
        else -> null
    }

/** True when F1 or Ctrl+/ (Ctrl+?) should open keyboard shortcut help. */
fun shouldOpenKeyboardShortcutHelp(isCtrlPressed: Boolean, key: Key): Boolean =
    key == Key.F1 || (isCtrlPressed && key == Key.Slash)
