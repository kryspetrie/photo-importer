package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun RefinementTopBar(
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onShowHelp: () -> Unit = {},
    refocus: () -> Unit = {},
) {
    val s = strings()
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.t(StringKey.WIZARD_REFINE_BOX), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
        }
    }
}
