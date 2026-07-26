package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DuplicateGroupCard(group: DuplicateInfo, onSetPrimary: (String) -> Unit) {
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.FileCopy,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    s.t(
                        StringKey.DUP_FILES_TYPE,
                        "count" to (1 + group.duplicateImages.size).toString(),
                        "type" to group.duplicateType.name.replace("_", " "),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            val all = listOf(group.primaryImage) + group.duplicateImages
            all.forEach { image ->
                val isPrimary = image.id == group.primaryImage.id
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = 60,
                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            image.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${formatFileSize(image.fileSize)} · ${image.fileType.displayName}" +
                                (image.metadata?.resolution?.let { " · $it" }.orEmpty()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isPrimary) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(s.t(StringKey.DUP_KEEP), style = MaterialTheme.typography.labelSmall)
                            },
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            modifier = Modifier.height(24.dp),
                        )
                    } else {
                        OutlinedButton(
                            onClick = { onSetPrimary(image.id) },
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(s.t(StringKey.DUP_SET_KEEP), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
