package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.DefaultColors
import org.kryspetrie.fileimport.ui.theme.DefaultSpacing

@Composable
fun DuplicateGroupCard(group: DuplicateInfo, onSetPrimary: (String) -> Unit) {
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(DefaultSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(DefaultSpacing.sm + DefaultSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DefaultSpacing.md),
            ) {
                Icon(
                    Icons.Default.FileCopy,
                    s.t(StringKey.DUP_TITLE),
                    Modifier.size(DefaultSpacing.iconMedium),
                    tint = DefaultColors.error,
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = DefaultSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DefaultSpacing.md),
                ) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = 60,
                        modifier =
                            Modifier.size(DefaultSpacing.buttonHeightTall)
                                .clip(MaterialTheme.shapes.small),
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
                            color = DefaultColors.textSecondary,
                        )
                    }
                    if (isPrimary) {
                        Surface(
                            shape = RoundedCornerShape(DefaultSpacing.cornerLarge),
                            color = DefaultColors.primaryContainer,
                            modifier = Modifier.height(DefaultSpacing.iconButtonSmall),
                        ) {
                            Text(
                                s.t(StringKey.DUP_KEEP),
                                style = MaterialTheme.typography.labelSmall,
                                color = DefaultColors.onPrimaryContainer,
                                modifier =
                                    Modifier.padding(
                                        horizontal = DefaultSpacing.md,
                                        vertical = DefaultSpacing.sm,
                                    ),
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSetPrimary(image.id) },
                            modifier = Modifier.height(DefaultSpacing.iconButtonSmall),
                            contentPadding =
                                PaddingValues(
                                    horizontal = DefaultSpacing.md,
                                    vertical = DefaultSpacing.none,
                                ),
                        ) {
                            Text(
                                s.t(StringKey.DUP_SET_KEEP),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
