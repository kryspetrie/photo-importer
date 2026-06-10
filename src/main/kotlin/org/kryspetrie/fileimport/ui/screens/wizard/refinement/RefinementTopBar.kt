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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefinementTopBar(
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowHelp: () -> Unit = {},
    refocus: () -> Unit = {},
) {
    TopAppBar(
        title = { Text("Refine Bounding Box") },
        actions = {
            IconButton(
                onClick = {
                    onUndo()
                    refocus()
                }
            ) {
                Icon(Icons.Default.Undo, "Undo")
            }
            IconButton(
                onClick = {
                    onRedo()
                    refocus()
                }
            ) {
                Icon(Icons.Default.Redo, "Redo")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onShowHelp) { Icon(Icons.Default.Info, "Keyboard shortcuts") }
        },
    )
}
