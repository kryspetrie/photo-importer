package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun SelectionStatusBar(
    selectedCount: Int,
    totalImages: Int,
    filteredCount: Int,
    totalSelectedSize: Long,
) {
    val s = strings()
    val filteredSuffix =
        if (filteredCount != totalImages) {
            " " + s.t(StringKey.IMPORT_FILES_SHOWING, "count" to filteredCount.toString())
        } else {
            ""
        }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                s.t(
                    StringKey.IMPORT_FILES_SELECTED,
                    "selected" to selectedCount.toString(),
                    "total" to totalImages.toString(),
                    "filtered" to filteredSuffix,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatFileSize(totalSelectedSize),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
