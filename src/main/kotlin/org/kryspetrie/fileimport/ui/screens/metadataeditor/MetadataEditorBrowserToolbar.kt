package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MetadataEditorBrowserToolbar(
    viewMode: MetadataEditorFileViewMode,
    onViewModeChange: (MetadataEditorFileViewMode) -> Unit,
    onSelectFiles: () -> Unit,
    onSelectFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    showOpenFolderIcon: Boolean,
) {
    val s = strings()
    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
                    Icon(
                        Icons.Default.FolderOpen,
                        s.t(StringKey.ACC_OPEN_FOLDER),
                        Modifier.size(18.dp),
                    )
                }
            }
        }
        if (!showOpenFolderIcon) {
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewModeButton(
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
                Modifier.size(30.dp)
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
