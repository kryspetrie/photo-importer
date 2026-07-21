package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar

/**
 * Sidebar thumbnail strip for the metadata editor.
 *
 * Shows a scrollable list of image thumbnails with selection state and modification indicators.
 * Supports both single-select (click to select) and multi-select (click to toggle, checkbox
 * overlay) modes. Uses [ChunkyScrollbar] for visible scroll feedback when content overflows.
 *
 * @param state The bulk edit state.
 * @param thumbnailCache Cache of pre-scaled thumbnail images.
 * @param isMultiEditMode Whether multi-edit mode is active.
 * @param selectedIndices Set of currently selected indices (in multi-edit mode).
 * @param onSelect Callback when a thumbnail is clicked.
 * @param onToggleMultiEdit Callback when the Multi/Done button is clicked.
 * @param onDeselectAll Callback to deselect all items.
 * @param onOpenFolder Callback to open a new folder.
 * @param modifier Modifier for the sidebar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorSidebar(
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelect: (Int) -> Unit,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = modifier.width(120.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenFolder, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.FolderOpen, "Open folder", modifier = Modifier.size(18.dp))
                }
                if (state.fileCount > 1) {
                    if (isMultiEditMode) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${selectedIndices.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(
                                onClick = {
                                    onDeselectAll()
                                    onToggleMultiEdit()
                                },
                                modifier = Modifier.height(20.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                Text("Done", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onToggleMultiEdit,
                            modifier = Modifier.height(20.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Text("Multi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Scrollable thumbnail list with visible scrollbar
            ChunkyScrollbar(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.files.forEachIndexed { index, file ->
                        val isSelected =
                            if (isMultiEditMode) index in selectedIndices
                            else index == state.selectedIndex
                        val entry = state.fileConfigs[file.absolutePath]
                        val isModified = entry?.isModified == true

                        Card(
                            modifier =
                                Modifier.width(100.dp).height(80.dp).clickable { onSelect(index) },
                            shape = RoundedCornerShape(6.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        when {
                                            isModified && isSelected ->
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isModified ->
                                                MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val thumb = thumbnailCache[file.absolutePath]
                                if (thumb != null) {
                                    val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = file.name,
                                        modifier = Modifier.fillMaxSize().padding(2.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        "Loading",
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected && isMultiEditMode) {
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = { onSelect(index) },
                                        modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                                    )
                                }
                                // Modified indicator dot
                                if (isModified) {
                                    Surface(
                                        modifier =
                                            Modifier.align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(8.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(4.dp),
                                    ) {}
                                }
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                                )
                                if (entry?.config?.hasMetadata() == true) {
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
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
