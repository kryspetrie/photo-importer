package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Compact file-selection panel for the metadata editor.
 *
 * Uses native macOS-style file dialogs for loading images instead of a thumbnail strip. Shows a
 * scrollable filename list for navigation once files are loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorFilePickerPanel(
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()

    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.width(260.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onSelectFiles,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                ) {
                    Icon(Icons.Default.Image, s.t(StringKey.ACC_SELECT_IMAGES), Modifier.size(16.dp))
                    Text(
                        s.t(StringKey.META_SELECT_IMAGES),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(
                    onClick = onSelectFolder,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, s.t(StringKey.ACC_OPEN_FOLDER), Modifier.size(16.dp))
                    Text(
                        s.t(StringKey.META_SELECT_FOLDER_ELLIPSIS),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onToggleMultiEdit,
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text(
                            if (isMultiEditMode) s.t(StringKey.META_DONE) else s.t(StringKey.META_MULTI),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (isMultiEditMode && selectedIndices.isNotEmpty()) {
                        OutlinedButton(onClick = onDeselectAll, modifier = Modifier.height(28.dp)) {
                            Text(s.t(StringKey.META_CLEAR), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    when {
                        state.fileCount == 0 -> s.t(StringKey.META_NO_IMAGES_SELECTED)
                        state.fileCount == 1 -> s.t(StringKey.META_IMAGE_COUNT_ONE)
                        else ->
                            s.t(StringKey.META_IMAGE_COUNT_OTHER, "count" to state.fileCount.toString())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ChunkyScrollbar(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.files.forEachIndexed { index, file ->
                        val isSelected =
                            if (isMultiEditMode) {
                                index in selectedIndices
                            } else {
                                index == state.selectedIndex
                            }
                        val isModified = state.fileConfigs[file.absolutePath]?.isModified == true
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { onSelectIndex(index) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isMultiEditMode) {
                                Checkbox(
                                    checked = index in selectedIndices,
                                    onCheckedChange = { onSelectIndex(index) },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color =
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                if (isModified) {
                                    Text(
                                        s.t(StringKey.META_MODIFIED),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
