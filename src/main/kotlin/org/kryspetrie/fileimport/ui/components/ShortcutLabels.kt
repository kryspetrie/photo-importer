package org.kryspetrie.fileimport.ui.components

import org.kryspetrie.fileimport.infrastructure.adapter.Platform

/** Platform-aware modifier label for keyboard shortcut help text. */
object ShortcutLabels {
    val modifier: String
        get() = if (Platform.isMac) "⌘" else "Ctrl"

    fun chord(key: String): String = "$modifier+$key"

    fun chord(first: String, second: String): String = "$modifier+$first / $modifier+$second"
}
