package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.WatchFolderStatus
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.formatRelativeTime
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun WatchFolderStatusCard(
    watchStatus: WatchFolderStatus,
    onStopWatching: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (watchStatus.lastError != null && !watchStatus.isWatching) {
                        Icons.Default.Error
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (watchStatus.lastError != null && !watchStatus.isWatching) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        s.t(StringKey.WATCH_FILES_DETECTED, "path" to watchStatus.watchPath),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append("${watchStatus.filesDetected} file(s) detected")
                            if (watchStatus.autoImportsPending > 0) {
                                append(
                                    " · ${watchStatus.autoImportsPending} ${s.t(StringKey.WATCH_PENDING)}"
                                )
                            }
                            if (watchStatus.importCount > 0) {
                                append(
                                    " · ${watchStatus.importCount} ${s.t(StringKey.WATCH_IMPORTED)}"
                                )
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (watchStatus.lastImportTime > 0) {
                        Text(
                            s.t(
                                StringKey.WATCH_LAST_IMPORT,
                                "time" to s.formatRelativeTime(watchStatus.lastImportTime),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onStopWatching,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        s.t(StringKey.IMPORT_STOP_BUTTON),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            watchStatus.lastError?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
