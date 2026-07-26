package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefinementTopBar(
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowHelp: () -> Unit = {},
    refocus: () -> Unit = {},
) {
    val s = strings()
    TopAppBar(
        title = { Text(s.t(StringKey.WIZARD_REFINE_BOX)) },
        actions = {
            IconButton(
                onClick = {
                    onUndo()
                    refocus()
                }
            ) {
                Icon(Icons.Default.Undo, s.t(StringKey.META_UNDO))
            }
            IconButton(
                onClick = {
                    onRedo()
                    refocus()
                }
            ) {
                Icon(Icons.Default.Redo, s.t(StringKey.META_REDO))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, s.delete, tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onShowHelp) {
                Icon(Icons.Default.Info, s.t(StringKey.WIZARD_KEYBOARD_SHORTCUTS))
            }
        },
    )
}
