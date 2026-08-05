package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun MetadataEditorListView(
    currentFolder: MetadataFolderNode,
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    focusedFolderPath: String?,
    onSelectIndex: (Int) -> Unit,
    onEnterFolderPath: (String) -> Unit,
) {
    val navItems = remember(currentFolder) { metadataBrowserNavItems(currentFolder) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(
            count = navItems.size,
            key = { index ->
                when (val item = navItems[index]) {
                    is MetadataBrowserNavItem.Folder -> "folder:${item.path}"
                    is MetadataBrowserNavItem.File -> "file:${item.index}"
                }
            },
        ) { index ->
            when (val item = navItems[index]) {
                is MetadataBrowserNavItem.Folder ->
                    MetadataEditorFolderRow(
                        name = item.node.name,
                        isFocused = focusedFolderPath == item.path,
                        onEnterFolder = { onEnterFolderPath(item.path) },
                    )
                is MetadataBrowserNavItem.File -> {
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

@Composable
internal fun MetadataEditorFolderRow(
    name: String,
    onEnterFolder: () -> Unit,
    isFocused: Boolean = false,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val s = strings()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    if (isFocused) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.surface
                )
                .pointerInput(onEnterFolder) {
                    detectTapGestures(onDoubleTap = { onEnterFolder() })
                }
                .padding(start = indent)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight,
            s.t(StringKey.ACC_ENTER_FOLDER),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
internal fun MetadataEditorFileRow(
    file: File,
    index: Int,
    isSelected: Boolean,
    isModified: Boolean,
    isMultiEditMode: Boolean,
    onSelectIndex: (Int) -> Unit,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val s = strings()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onSelectIndex(index) }
                .padding(start = indent)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiEditMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectIndex(index) },
                modifier = Modifier.size(20.dp),
            )
        }
        leadingIcon?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
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
