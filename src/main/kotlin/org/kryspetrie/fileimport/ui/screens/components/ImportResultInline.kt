package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.io.File
import org.kryspetrie.fileimport.domain.model.ImportResult

@Composable
fun ImportResultInline(result: ImportResult, destinationPath: String, onReset: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                Modifier.size(36.dp),
                tint =
                    if (result.errorCount == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
            )
            Text(
                if (result.errorCount == 0) "Import Complete!" else "Completed with Errors",
                style = MaterialTheme.typography.titleSmall,
            )
            if (destinationPath.isNotBlank()) {
                Text(
                    destinationPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCol("${result.successCount}", "Copied")
                StatCol("${result.skippedCount}", "Skipped")
                StatCol("${result.errorCount}", "Errors")
                StatCol("${result.duplicateCount}", "Duplicates")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (destinationPath.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            try {
                                Desktop.getDesktop().open(File(destinationPath))
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Destination")
                    }
                }
                OutlinedButton(onClick = onReset) { Text("New Import") }
            }
        }
    }
}

@Composable
fun StatCol(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
