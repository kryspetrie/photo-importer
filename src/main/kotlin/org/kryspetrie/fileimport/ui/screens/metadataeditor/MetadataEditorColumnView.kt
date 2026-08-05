package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.components.ChunkyHorizontalScrollbar

@Composable
internal fun MetadataEditorColumnView(
    fileTree: MetadataFolderNode,
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
) {
    var columnNodes by
        remember(fileTree, state.sourcePath) {
            mutableStateOf(metadataEditorInitialColumnNodes(fileTree))
        }
    val horizontalScrollState = rememberScrollState()

    ChunkyHorizontalScrollbar(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        scrollState = horizontalScrollState,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(horizontalScrollState),
            horizontalArrangement = Arrangement.Start,
        ) {
            columnNodes.forEachIndexed { columnIndex, node ->
                Column(
                    modifier =
                        Modifier.width(140.dp)
                            .fillMaxHeight()
                            .padding(end = 1.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            count = node.children.size,
                            key = { childIndex -> "col$columnIndex-folder-$childIndex" },
                        ) { childIndex ->
                            val child = node.children[childIndex]
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            columnNodes =
                                                metadataEditorColumnNodesAfterFolderClick(
                                                    columnNodes,
                                                    columnIndex,
                                                    child,
                                                )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    child.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        items(
                            count = node.fileIndices.size,
                            key = { fileIndex -> "col$columnIndex-file-$fileIndex" },
                        ) { fileIndex ->
                            val index = node.fileIndices[fileIndex]
                            val file = state.files.getOrNull(index) ?: return@items
                            val selected =
                                if (isMultiEditMode) index in selectedIndices
                                else index == state.selectedIndex
                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clickable { onSelectIndex(index) }
                                        .background(
                                            if (selected)
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.5f
                                                )
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
