package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp

@Composable
internal fun MetadataEditorHierarchyView(
    node: MetadataFolderNode,
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
) {
    val defaultExpanded = remember(node) { defaultExpandedHierarchyPaths(node) }
    var expandedPaths by remember(node) { mutableStateOf(defaultExpanded) }
    val listItems = remember(node, expandedPaths) { buildHierarchyListItems(node, expandedPaths) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(
            count = listItems.size,
            key = { index ->
                when (val item = listItems[index]) {
                    is MetadataHierarchyItem.FolderRow -> "hf:${item.path}"
                    is MetadataHierarchyItem.FileRow -> "file:${item.index}"
                }
            },
        ) { index ->
            when (val item = listItems[index]) {
                is MetadataHierarchyItem.FolderRow -> {
                    val indent = (item.depth * 16).dp
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    expandedPaths =
                                        if (item.expanded) expandedPaths - item.path
                                        else expandedPaths + item.path
                                }
                                .padding(start = indent, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier =
                                Modifier.size(16.dp).graphicsLayer {
                                    rotationZ = if (item.expanded) 90f else 0f
                                },
                        )
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                        )
                        Text(item.node.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
                is MetadataHierarchyItem.FileRow -> {
                    val file = state.files.getOrNull(item.index) ?: return@items
                    MetadataEditorFileRow(
                        file = file,
                        index = item.index,
                        isSelected =
                            if (isMultiEditMode) item.index in selectedIndices
                            else item.index == state.selectedIndex,
                        isModified = state.fileConfigs[file.absolutePath]?.isModified == true,
                        isMultiEditMode = isMultiEditMode,
                        onSelectIndex = onSelectIndex,
                        indent = (item.depth * 16).dp,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}
