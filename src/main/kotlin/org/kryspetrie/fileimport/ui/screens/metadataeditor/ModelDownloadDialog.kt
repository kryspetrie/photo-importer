package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.ModelDownloadState
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Dialog prompting the user to download the orientation detection model.
 *
 * Shows download progress, errors, and retry options. The dialog is shown when the user tries to
 * use auto-rotate but the model is not available locally.
 *
 * @param downloadState Current download state (null = not started).
 * @param onDownload Callback when user clicks "Download".
 * @param onCancel Callback when user clicks "Cancel" or dismisses.
 * @param onRetry Callback when user clicks "Retry" after a failure.
 */
@Composable
fun ModelDownloadDialog(
    downloadState: ModelDownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val s = strings()

    AlertDialog(
        onDismissRequest = { if (downloadState !is ModelDownloadState.Downloading) onCancel() },
        title = { Text(s.t(StringKey.MODEL_DOWNLOAD_TITLE)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (downloadState) {
                    null,
                    is ModelDownloadState.Idle -> {
                        Text(
                            s.t(StringKey.MODEL_DOWNLOAD_INTRO),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            s.t(StringKey.MODEL_DOWNLOAD_OFFLINE_NOTE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ModelDownloadState.Connecting -> {
                        Text(
                            s.t(StringKey.META_MODEL_DOWNLOAD_CONNECTING),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is ModelDownloadState.Downloading -> {
                        Text(
                            s.t(StringKey.META_MODEL_DOWNLOAD_DOWNLOADING),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (downloadState.progressPercent != null) {
                            LinearProgressIndicator(
                                progress = { downloadState.progressPercent!! / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                s.t(
                                    StringKey.META_MODEL_DOWNLOAD_PROGRESS_MB,
                                    "progress" to downloadState.progressPercent.toString(),
                                    "downloaded" to
                                        "%.1f".format(bytesToMb(downloadState.bytesDownloaded)),
                                    "total" to
                                        "%.1f"
                                            .format(
                                                downloadState.totalBytes?.let { bytesToMb(it) }
                                                    ?: 0.0
                                            ),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                s.t(
                                    StringKey.META_MODEL_DOWNLOAD_PROGRESS_BYTES,
                                    "downloaded" to
                                        "%.1f".format(bytesToMb(downloadState.bytesDownloaded)),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is ModelDownloadState.Failed -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                s.t(
                                    StringKey.MODEL_DOWNLOAD_FAILED,
                                    "message" to downloadState.error,
                                ),
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (downloadState.canRetry) {
                            Text(
                                s.t(StringKey.META_MODEL_DOWNLOAD_RETRY_HINT),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is ModelDownloadState.Completed -> {
                        Text(
                            s.t(StringKey.MODEL_DOWNLOAD_COMPLETE),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ModelDownloadState.Cancelled -> {
                        Text(
                            s.t(StringKey.META_MODEL_DOWNLOAD_CANCELLED),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                null,
                is ModelDownloadState.Idle -> {
                    Button(onClick = onDownload) { Text(s.t(StringKey.META_MODEL_DOWNLOAD_BUTTON)) }
                }
                is ModelDownloadState.Connecting,
                is ModelDownloadState.Downloading -> {
                    OutlinedButton(onClick = onCancel) {
                        Text(s.t(StringKey.META_MODEL_DOWNLOAD_CANCEL_BUTTON))
                    }
                }
                is ModelDownloadState.Failed -> {
                    if (downloadState.canRetry) {
                        Button(onClick = onRetry) { Text(s.t(StringKey.ACTION_RETRY)) }
                    }
                    TextButton(onClick = onCancel) { Text(s.close) }
                }
                is ModelDownloadState.Completed -> {
                    Button(onClick = onCancel) { Text(s.ok) }
                }
                is ModelDownloadState.Cancelled -> {
                    TextButton(onClick = onCancel) { Text(s.close) }
                }
            }
        },
        dismissButton = {
            if (downloadState == null || downloadState is ModelDownloadState.Idle) {
                TextButton(onClick = onCancel) { Text(s.t(StringKey.MODEL_DOWNLOAD_CANCEL)) }
            }
        },
    )
}

private fun bytesToMb(bytes: Long): Double = bytes / (1024.0 * 1024.0)
