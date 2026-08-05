package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun ImagePreviewHeader(
    viewModel: ImagePreviewViewModel,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val s = strings()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.t(StringKey.IMPORT_SELECT_FILES), style = MaterialTheme.typography.headlineSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { viewModel.viewMode = ImagePreviewViewModel.ViewMode.LIST },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ViewList,
                    s.t(StringKey.ACC_LIST_VIEW),
                    tint =
                        if (viewModel.viewMode == ImagePreviewViewModel.ViewMode.LIST)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { viewModel.viewMode = ImagePreviewViewModel.ViewMode.GRID },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.GridView,
                    s.t(StringKey.ACC_GRID_VIEW),
                    tint =
                        if (viewModel.viewMode == ImagePreviewViewModel.ViewMode.GRID)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSelectAll) { Text(s.t(StringKey.ACTION_SELECT_ALL)) }
            TextButton(onClick = onSelectNone) { Text(s.t(StringKey.IMPORT_SELECT_NONE)) }
        }
    }
}
