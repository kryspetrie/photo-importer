package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import org.kryspetrie.fileimport.ui.components.CircularSpinner
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.ui.components.formatFileSize

@Composable
fun ImportProgressScreen(progress: ImportProgress, onCancel: () -> Unit) {
    val percent = (progress.progressPercent * 100).toInt()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularSpinner(size = 48.dp, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text("Importing...", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        if (progress.totalFiles > 0) {
            Text(
                "${progress.currentIndex} of ${progress.totalFiles} files ($percent%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                progress.currentFile,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatFileSize(progress.copiedBytes)} / ${formatFileSize(progress.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (progress.totalFiles > 0) progress.progressPercent else 0f },
            modifier = Modifier.fillMaxWidth(0.6f).height(4.dp),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}
