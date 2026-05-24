package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportConfiguration

/**
 * Generates a one-line configuration summary string for an [ImportConfiguration].
 *
 * Used as collapsed header text in settings sections across multiple screens (MediaImport,
 * PhotoScanImport, Reorganize).
 */
fun configSummary(c: ImportConfiguration): String = buildString {
    if (c.createSubfolders) append(c.folderPattern) else append("Flat")
    append(" · ")
    if (c.preserveOriginalName) append("original names") else append(c.fileNamePattern)
    if (c.verifyAfterCopy) append(" · verify")
    if (c.deleteAfterImport) append(" · delete source")
    if (c.detectVisualDuplicates) append(" · dedup")
}

/**
 * A compact stat column showing a value above a label, used in result/summary cards.
 *
 * This replaces the duplicated `StatItem` (ReorganizeScreen) and `StatCol` (ImportResultInline).
 */
@Composable
fun StatColumn(value: String, label: String, icon: ImageVector? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
        } else {
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A compact error card showing an icon + message, used across screens for consistent error display.
 */
@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A compact info/tip card with an icon, used for helpful hints. */
@Composable
fun InfoCard(
    message: String,
    icon: ImageVector = Icons.Default.Info,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
