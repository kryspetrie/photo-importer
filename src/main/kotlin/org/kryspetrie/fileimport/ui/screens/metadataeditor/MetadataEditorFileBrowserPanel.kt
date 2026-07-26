package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyHorizontalScrollbar
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.i18n.strings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize

fun MetadataEditorFileViewMode.panelWidth(): androidx.compose.ui.unit.Dp =
    when (this) {
        MetadataEditorFileViewMode.ICONS -> 120.dp
        MetadataEditorFileViewMode.LIST -> 260.dp
        MetadataEditorFileViewMode.HIERARCHY -> 300.dp
        MetadataEditorFileViewMode.COLUMN -> 440.dp
    }

/** True when the center preview should use the compact height layout. */
fun MetadataEditorFileViewMode.usesCompactPreview(): Boolean = this != MetadataEditorFileViewMode.ICONS

/**
 * Unified file browser for the metadata editor with Finder-style view modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorFileBrowserPanel(
    state: BulkEditState,
    viewMode: MetadataEditorFileViewMode,
    onViewModeChange: (MetadataEditorFileViewMode) -> Unit,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    folderPathStack: List<String>,
    focusedFolderPath: String?,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    onOpenFolder: () -> Unit,
    onNavigateUp: () -> Unit,
    onEnterFolderPath: (String) -> Unit,
    onBrowserKey: (Key) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val fileTree = remember(state.files, state.sourcePath) { buildMetadataFileTree(state.files, state.sourcePath) }
    val currentFolder = remember(fileTree, folderPathStack) { resolveMetadataBrowserFolder(fileTree, folderPathStack) }
    val supportsFolderNavigation =
        viewMode == MetadataEditorFileViewMode.LIST || viewMode == MetadataEditorFileViewMode.ICONS
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.files, state.editingActive) {
        if (state.editingActive && state.files.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        tonalElevation = 2.dp,
        modifier =
            modifier
                .width(viewMode.panelWidth())
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onBrowserKey(keyEvent.key)
                    } else {
                        false
                    }
                },
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            MetadataEditorBrowserToolbar(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                onSelectFiles = onSelectFiles,
                onSelectFolder = onSelectFolder,
                onOpenFolder = onOpenFolder,
                showOpenFolderIcon = viewMode == MetadataEditorFileViewMode.ICONS,
            )

            MetadataEditorBrowserActionsRow(
                state = state,
                isMultiEditMode = isMultiEditMode,
                selectedIndices = selectedIndices,
                onToggleMultiEdit = onToggleMultiEdit,
                onDeselectAll = onDeselectAll,
            )

            if (supportsFolderNavigation && folderPathStack.isNotEmpty()) {
                MetadataEditorFolderNavigationBar(
                    currentFolder = currentFolder,
                    sourcePath = state.sourcePath,
                    onNavigateUp = onNavigateUp,
                )
            }

            ChunkyScrollbar(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (viewMode) {
                    MetadataEditorFileViewMode.LIST ->
                        MetadataEditorListView(
                            currentFolder = currentFolder,
                            state = state,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            focusedFolderPath = focusedFolderPath,
                            onSelectIndex = onSelectIndex,
                            onEnterFolderPath = onEnterFolderPath,
                        )
                    MetadataEditorFileViewMode.HIERARCHY ->
                        MetadataEditorHierarchyView(
                            node = fileTree,
                            state = state,
                            depth = 0,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            onSelectIndex = onSelectIndex,
                        )
                    MetadataEditorFileViewMode.COLUMN ->
                        MetadataEditorColumnView(
                            fileTree = fileTree,
                            state = state,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            onSelectIndex = onSelectIndex,
                        )
                    MetadataEditorFileViewMode.ICONS ->
                        MetadataEditorIconsView(
                            currentFolder = currentFolder,
                            state = state,
                            thumbnailCache = thumbnailCache,
                            isMultiEditMode = isMultiEditMode,
                            selectedIndices = selectedIndices,
                            focusedFolderPath = focusedFolderPath,
                            onSelectIndex = onSelectIndex,
                            onEnterFolderPath = onEnterFolderPath,
                            compact = true,
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataEditorBrowserToolbar(
    viewMode: MetadataEditorFileViewMode,
    onViewModeChange: (MetadataEditorFileViewMode) -> Unit,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    showOpenFolderIcon: Boolean,
) {
    val s = strings()
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewModeButton(
                selected = viewMode == MetadataEditorFileViewMode.COLUMN,
                onClick = { onViewModeChange(MetadataEditorFileViewMode.COLUMN) },
                icon = Icons.Default.ViewColumn,
                label = s.t(StringKey.META_VIEW_COLUMN),
            )
            ViewModeButton(
                selected = viewMode == MetadataEditorFileViewMode.LIST,
                onClick = { onViewModeChange(MetadataEditorFileViewMode.LIST) },
                icon = Icons.AutoMirrored.Filled.ViewList,
                label = s.t(StringKey.META_VIEW_LIST),
            )
            ViewModeButton(
                selected = viewMode == MetadataEditorFileViewMode.HIERARCHY,
                onClick = { onViewModeChange(MetadataEditorFileViewMode.HIERARCHY) },
                icon = Icons.Default.AccountTree,
                label = s.t(StringKey.META_VIEW_HIERARCHY),
            )
            ViewModeButton(
                selected = viewMode == MetadataEditorFileViewMode.ICONS,
                onClick = { onViewModeChange(MetadataEditorFileViewMode.ICONS) },
                icon = Icons.Default.GridView,
                label = s.t(StringKey.META_VIEW_ICONS),
            )
            if (showOpenFolderIcon) {
                IconButton(onClick = onOpenFolder, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.FolderOpen, s.t(StringKey.ACC_OPEN_FOLDER), Modifier.size(18.dp))
                }
            }
        }
        if (!showOpenFolderIcon) {
            OutlinedButton(onClick = onSelectFiles, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                Icon(Icons.Default.Image, s.t(StringKey.ACC_SELECT_IMAGES), Modifier.size(16.dp))
                Text(
                    s.t(StringKey.META_SELECT_IMAGES),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            OutlinedButton(onClick = onSelectFolder, modifier = Modifier.fillMaxWidth().height(32.dp)) {
                Icon(Icons.Default.FolderOpen, s.t(StringKey.ACC_OPEN_FOLDER), Modifier.size(16.dp))
                Text(
                    s.t(StringKey.META_SELECT_FOLDER_ELLIPSIS),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    val tooltipState = rememberTooltipState()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { Text(label) },
        state = tooltipState,
    ) {
        IconButton(
            onClick = onClick,
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
        ) {
            Icon(
                icon,
                label,
                modifier = Modifier.size(18.dp),
                tint =
                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetadataEditorBrowserActionsRow(
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    val s = strings()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onToggleMultiEdit, modifier = Modifier.height(28.dp)) {
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
                else -> s.t(StringKey.META_IMAGE_COUNT_OTHER, "count" to state.fileCount.toString())
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetadataEditorFolderNavigationBar(
    currentFolder: MetadataFolderNode,
    sourcePath: String,
    onNavigateUp: () -> Unit,
) {
    val s = strings()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigateUp, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                s.t(StringKey.META_BROWSER_UP),
                modifier = Modifier.size(18.dp),
            )
        }
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            currentFolder.displayName(sourcePath),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
    }
    HorizontalDivider()
}

@Composable
private fun MetadataEditorListView(
    currentFolder: MetadataFolderNode,
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    focusedFolderPath: String?,
    onSelectIndex: (Int) -> Unit,
    onEnterFolderPath: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        currentFolder.children.forEach { child ->
            val path = child.folder?.absolutePath ?: child.name
            MetadataEditorFolderRow(
                name = child.name,
                isFocused = focusedFolderPath == path,
                onEnterFolder = { onEnterFolderPath(path) },
            )
        }
        currentFolder.fileIndices.forEach { index ->
            val file = state.files.getOrNull(index) ?: return@forEach
            MetadataEditorFileRow(
                file = file,
                index = index,
                isSelected =
                    if (isMultiEditMode) index in selectedIndices else index == state.selectedIndex,
                isModified = state.fileConfigs[file.absolutePath]?.isModified == true,
                isMultiEditMode = isMultiEditMode,
                onSelectIndex = onSelectIndex,
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                },
            )
        }
    }
}

@Composable
private fun MetadataEditorFolderRow(
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
        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ChevronRight, s.t(StringKey.ACC_ENTER_FOLDER), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MetadataEditorFileRow(
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

@Composable
private fun MetadataEditorHierarchyView(
    node: MetadataFolderNode,
    state: BulkEditState,
    depth: Int,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
) {
    var expanded by remember(node.folder?.absolutePath ?: node.name) { mutableStateOf(depth < 2) }
    val indent = (depth * 16).dp

    Column(modifier = Modifier.fillMaxWidth()) {
        if (node.folder != null && (node.children.isNotEmpty() || node.fileIndices.isNotEmpty())) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(start = indent, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier =
                        Modifier.size(16.dp).graphicsLayer {
                            rotationZ = if (expanded) 90f else 0f
                        },
                )
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                Text(node.name, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (expanded || node.folder == null) {
            node.fileIndices.forEach { index ->
                val file = state.files.getOrNull(index) ?: return@forEach
                MetadataEditorFileRow(
                    file = file,
                    index = index,
                    isSelected =
                        if (isMultiEditMode) index in selectedIndices else index == state.selectedIndex,
                    isModified = state.fileConfigs[file.absolutePath]?.isModified == true,
                    isMultiEditMode = isMultiEditMode,
                    onSelectIndex = onSelectIndex,
                    indent = indent + if (node.folder != null) 16.dp else 0.dp,
                    leadingIcon = {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                    },
                )
            }
            node.children.forEach { child ->
                MetadataEditorHierarchyView(
                    node = child,
                    state = state,
                    depth = depth + 1,
                    isMultiEditMode = isMultiEditMode,
                    selectedIndices = selectedIndices,
                    onSelectIndex = onSelectIndex,
                )
            }
        }
    }
}

@Composable
private fun MetadataEditorColumnView(
    fileTree: MetadataFolderNode,
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
) {
    var columnNodes by remember(fileTree, state.sourcePath) { mutableStateOf(listOf(fileTree)) }
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            ) {
                node.children.forEach { child ->
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { columnNodes = columnNodes.take(columnIndex + 1) + child }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            child.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
                node.fileIndices.forEach { index ->
                    val file = state.files.getOrNull(index) ?: return@forEach
                    val selected =
                        if (isMultiEditMode) index in selectedIndices else index == state.selectedIndex
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable { onSelectIndex(index) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
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

@Composable
private fun MetadataEditorIconsView(
    currentFolder: MetadataFolderNode,
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    focusedFolderPath: String?,
    onSelectIndex: (Int) -> Unit,
    onEnterFolderPath: (String) -> Unit,
    compact: Boolean,
) {
    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            currentFolder.children.forEach { child ->
                val path = child.folder?.absolutePath ?: child.name
                MetadataEditorFolderIconCard(
                    name = child.name,
                    isFocused = focusedFolderPath == path,
                    onEnterFolder = { onEnterFolderPath(path) },
                    cardWidth = 100.dp,
                    cardHeight = 56.dp,
                )
            }
            currentFolder.fileIndices.forEach { index ->
                val file = state.files.getOrNull(index) ?: return@forEach
                MetadataEditorThumbnailCard(
                    file = file,
                    index = index,
                    state = state,
                    thumbnailCache = thumbnailCache,
                    isMultiEditMode = isMultiEditMode,
                    selectedIndices = selectedIndices,
                    onSelectIndex = onSelectIndex,
                    cardWidth = 100.dp,
                    cardHeight = 80.dp,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(currentFolder.children.size) { childIndex ->
                val child = currentFolder.children[childIndex]
                val path = child.folder?.absolutePath ?: child.name
                MetadataEditorFolderIconCard(
                    name = child.name,
                    isFocused = focusedFolderPath == path,
                    onEnterFolder = { onEnterFolderPath(path) },
                    cardWidth = 96.dp,
                    cardHeight = 72.dp,
                )
            }
            items(currentFolder.fileIndices.size) { fileIndex ->
                val index = currentFolder.fileIndices[fileIndex]
                val file = state.files.getOrNull(index) ?: return@items
                MetadataEditorThumbnailCard(
                    file = file,
                    index = index,
                    state = state,
                    thumbnailCache = thumbnailCache,
                    isMultiEditMode = isMultiEditMode,
                    selectedIndices = selectedIndices,
                    onSelectIndex = onSelectIndex,
                    cardWidth = 96.dp,
                    cardHeight = 80.dp,
                )
            }
        }
    }
}

@Composable
private fun MetadataEditorFolderIconCard(
    name: String,
    onEnterFolder: () -> Unit,
    isFocused: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
) {
    val s = strings()
    Card(
        modifier =
            Modifier.width(cardWidth)
                .height(cardHeight)
                .pointerInput(onEnterFolder) {
                    detectTapGestures(onDoubleTap = { onEnterFolder() })
                },
        shape = RoundedCornerShape(6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Folder,
                s.t(StringKey.ACC_ENTER_FOLDER),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MetadataEditorThumbnailCard(
    file: File,
    index: Int,
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onSelectIndex: (Int) -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
) {
    val s = strings()
    val isSelected =
        if (isMultiEditMode) index in selectedIndices else index == state.selectedIndex
    val entry = state.fileConfigs[file.absolutePath]
    val isModified = entry?.isModified == true

    Card(
        modifier = Modifier.width(cardWidth).height(cardHeight).clickable { onSelectIndex(index) },
        shape = RoundedCornerShape(6.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isModified && isSelected -> MaterialTheme.colorScheme.tertiaryContainer
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isModified -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumb = thumbnailCache[file.absolutePath]
            val thumbRotation = entry?.config?.rotationDegrees?.toFloat() ?: 0f
            if (thumb != null) {
                val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                Image(
                    bitmap = bitmap,
                    contentDescription = file.name,
                    modifier =
                        Modifier.fillMaxSize().padding(2.dp).let { mod ->
                            if (thumbRotation != 0f) mod.graphicsLayer { rotationZ = thumbRotation } else mod
                        },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    s.t(StringKey.ACC_LOADING),
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected && isMultiEditMode) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { onSelectIndex(index) },
                    modifier = Modifier.align(Alignment.TopStart).size(16.dp),
                )
            }
            val rotationDeg = entry?.config?.rotationDegrees ?: 0
            if (rotationDeg != 0) {
                RotationBadge(
                    rotationDegrees = rotationDeg,
                    isAutoDetected = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                )
            }
        }
    }
}
