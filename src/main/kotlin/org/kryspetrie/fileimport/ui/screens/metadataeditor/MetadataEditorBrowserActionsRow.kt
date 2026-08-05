package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun MetadataEditorBrowserActionsRow(
    state: BulkEditState,
    isMultiEditMode: Boolean,
    selectedIndices: Set<Int>,
    onToggleMultiEdit: () -> Unit,
    onDeselectAll: () -> Unit,
    diskThumbnailCacheEnabled: Boolean,
    onDiskThumbnailCacheChange: (Boolean) -> Unit,
    onClearThumbnailCache: () -> Unit,
    canClearThumbnailCache: Boolean,
) {
    val s = strings()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.clickable { onDiskThumbnailCacheChange(!diskThumbnailCacheEnabled) }
                        .padding(vertical = 2.dp),
            ) {
                Checkbox(
                    checked = diskThumbnailCacheEnabled,
                    onCheckedChange = onDiskThumbnailCacheChange,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    s.t(StringKey.META_DISK_THUMBNAILS),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            OutlinedButton(
                onClick = onClearThumbnailCache,
                enabled = canClearThumbnailCache,
                modifier = Modifier.height(28.dp),
            ) {
                Text(s.t(StringKey.META_CLEAR_THUMBS), style = MaterialTheme.typography.labelSmall)
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
