package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/** Read-only section showing all source metadata from the original file. */
@Composable
internal fun SourceMetadataSection(sourceExif: SourceExifSummary, wideLayout: Boolean = false) {
    val s = strings()
    val lines = sourceExif.summaryLines(s)
    if (lines.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Info,
                contentDescription = s.t(StringKey.FIELD_SOURCE_METADATA),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_SOURCE_METADATA),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Surface(
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (wideLayout && lines.size > 4) {
                val midpoint = (lines.size + 1) / 2
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        lines.take(midpoint).forEach { (label, value) ->
                            SourceMetadataLine(label, value)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        lines.drop(midpoint).forEach { (label, value) ->
                            SourceMetadataLine(label, value)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(6.dp)) {
                    lines.forEach { (label, value) -> SourceMetadataLine(label, value) }
                }
            }
        }
    }
}

@Composable
private fun SourceMetadataLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
