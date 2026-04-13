package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.ui.components.formatFileSize

@Composable
fun ImportProgressInline(progress: ImportProgress, onCancel: () -> Unit) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text("Importing...", style = MaterialTheme.typography.titleSmall)
              }
          if (progress.totalFiles > 0) {
            val percent = (progress.progressPercent * 100).toInt()
            Text(
                "${progress.currentIndex} of ${progress.totalFiles} ($percent%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                progress.currentFile,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { progress.progressPercent },
                Modifier.fillMaxWidth().height(4.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "${formatFileSize(progress.copiedBytes)} / ${formatFileSize(progress.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
  }
}
