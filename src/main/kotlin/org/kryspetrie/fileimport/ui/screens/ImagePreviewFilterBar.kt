package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

internal fun ImagePreviewViewModel.FileFilter.labelKey(): StringKey =
    when (this) {
        ImagePreviewViewModel.FileFilter.ALL -> StringKey.IMPORT_FILTER_ALL
        ImagePreviewViewModel.FileFilter.PHOTOS -> StringKey.IMPORT_FILTER_PHOTOS
        ImagePreviewViewModel.FileFilter.VIDEOS -> StringKey.IMPORT_FILTER_VIDEOS
        ImagePreviewViewModel.FileFilter.RAW -> StringKey.IMPORT_FILTER_RAW
    }

internal fun ImagePreviewViewModel.SortMode.labelKey(): StringKey =
    when (this) {
        ImagePreviewViewModel.SortMode.NAME -> StringKey.IMPORT_SORT_NAME
        ImagePreviewViewModel.SortMode.DATE -> StringKey.IMPORT_SORT_DATE
        ImagePreviewViewModel.SortMode.SIZE -> StringKey.IMPORT_SORT_SIZE
        ImagePreviewViewModel.SortMode.TYPE -> StringKey.IMPORT_SORT_TYPE
    }

@Composable
internal fun FilterAndSortBar(viewModel: ImagePreviewViewModel) {
    val s = strings()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = {
                Text(
                    s.t(StringKey.IMPORT_SEARCH_PLACEHOLDER),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.weight(1f).height(40.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
            trailingIcon = {
                if (viewModel.searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = { viewModel.searchQuery = "" },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(Icons.Default.Clear, s.t(StringKey.ACC_CLEAR), Modifier.size(14.dp))
                    }
                }
            },
        )
        ImagePreviewViewModel.FileFilter.entries.forEach { filter ->
            FilterChip(
                selected = viewModel.filterType == filter,
                onClick = { viewModel.filterType = filter },
                label = {
                    Text(s.t(filter.labelKey()), style = MaterialTheme.typography.labelSmall)
                },
                modifier = Modifier.height(28.dp),
            )
        }
        // Sort dropdown
        var sortMenuExpanded by remember { mutableStateOf(false) }
        Box {
            FilterChip(
                selected = false,
                onClick = { sortMenuExpanded = true },
                label = {
                    Text(
                        s.t(
                            StringKey.IMPORT_SORT_LABEL,
                            "mode" to s.t(viewModel.sortMode.labelKey()),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                trailingIcon = {
                    Icon(
                        if (viewModel.sortAscending) Icons.Default.ArrowUpward
                        else Icons.Default.ArrowDownward,
                        null,
                        Modifier.size(14.dp),
                    )
                },
                modifier = Modifier.height(28.dp),
            )
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
            ) {
                ImagePreviewViewModel.SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(s.t(mode.labelKey()), style = MaterialTheme.typography.bodySmall)
                        },
                        onClick = {
                            viewModel.sortMode = mode
                            sortMenuExpanded = false
                        },
                        trailingIcon = {
                            if (viewModel.sortMode == mode)
                                Icon(
                                    if (viewModel.sortAscending) Icons.Default.ArrowUpward
                                    else Icons.Default.ArrowDownward,
                                    null,
                                    Modifier.size(14.dp),
                                )
                        },
                    )
                }
            }
        }
    }
}
