package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CompactCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(vertical = 2.dp)) {
        Checkbox(checked, onCheckedChange, Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
      }
}

@Composable
fun ProgressCard(title: String, current: Int, total: Int, currentFile: String) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(
        Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text(title, style = MaterialTheme.typography.titleSmall)
              }
          if (total > 0) {
            LinearProgressIndicator(
                progress = { current.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$current / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (currentFile.isNotBlank()) {
            Text(
                currentFile,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
          }
        }
  }
}
