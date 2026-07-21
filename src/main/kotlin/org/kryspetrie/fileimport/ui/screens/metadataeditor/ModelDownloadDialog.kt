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
import org.kryspetrie.fileimport.domain.port.ModelDownloadState

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
    AlertDialog(
        onDismissRequest = { if (downloadState !is ModelDownloadState.Downloading) onCancel() },
        title = { Text("Download Orientation Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (downloadState) {
                    null,
                    is ModelDownloadState.Idle -> {
                        Text(
                            "The auto-rotate feature requires an orientation detection model " +
                                "(~330 MB). This model will be downloaded from the internet " +
                                "and stored locally.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Once downloaded, auto-rotate will work offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is ModelDownloadState.Connecting -> {
                        Text(
                            "Connecting to download server...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is ModelDownloadState.Downloading -> {
                        Text(
                            "Downloading orientation model...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (downloadState.progressPercent != null) {
                            LinearProgressIndicator(
                                progress = { downloadState.progressPercent!! / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "%.1f%% (%.1f / %.1f MB)"
                                    .format(
                                        downloadState.progressPercent,
                                        bytesToMb(downloadState.bytesDownloaded),
                                        downloadState.totalBytes?.let { bytesToMb(it) } ?: 0.0,
                                    ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "%.1f MB downloaded"
                                    .format(bytesToMb(downloadState.bytesDownloaded)),
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
                                "Download failed: ${downloadState.error}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (downloadState.canRetry) {
                            Text(
                                "Click Retry to try again.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    is ModelDownloadState.Completed -> {
                        Text(
                            "Model downloaded successfully! Auto-rotate is now available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is ModelDownloadState.Cancelled -> {
                        Text(
                            "Download cancelled. Auto-rotate will not be available until the " +
                                "model is downloaded.",
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
                    Button(onClick = onDownload) { Text("Download") }
                }
                is ModelDownloadState.Connecting,
                is ModelDownloadState.Downloading -> {
                    OutlinedButton(onClick = onCancel) { Text("Cancel Download") }
                }
                is ModelDownloadState.Failed -> {
                    if (downloadState.canRetry) {
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                    TextButton(onClick = onCancel) { Text("Close") }
                }
                is ModelDownloadState.Completed -> {
                    Button(onClick = onCancel) { Text("OK") }
                }
                is ModelDownloadState.Cancelled -> {
                    TextButton(onClick = onCancel) { Text("Close") }
                }
            }
        },
        dismissButton = {
            if (downloadState == null || downloadState is ModelDownloadState.Idle) {
                TextButton(onClick = onCancel) { Text("Later") }
            }
        },
    )
}

private fun bytesToMb(bytes: Long): Double = bytes / (1024.0 * 1024.0)
