package org.kryspetrie.fileimport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Restores [stored] when it changes, then persists [current] when it diverges from [stored].
 *
 * Skips persistence until the first restore completes so default VM state cannot overwrite saved
 * preferences on initial composition.
 */
@Composable
fun <T> SessionPreferencesEffect(
    stored: T,
    current: T,
    onRestore: (T) -> Unit,
    onPersist: (T) -> Unit,
) where T : Any {
    var persistEnabled by remember { mutableStateOf(false) }
    var restoreGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(stored) {
        onRestore(stored)
        persistEnabled = true
        restoreGeneration++
    }

    LaunchedEffect(current, stored, persistEnabled, restoreGeneration) {
        if (shouldPersistSessionPreferences(persistEnabled, current, stored)) {
            onPersist(current)
        }
    }
}

/** Guards persistence until restore completes and [current] diverges from [stored]. */
internal fun shouldPersistSessionPreferences(
    persistEnabled: Boolean,
    current: Any?,
    stored: Any?,
): Boolean = persistEnabled && current != stored
