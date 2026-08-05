package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun ImageListView(
    images: List<ImageFile>,
    onToggle: (String) -> Unit,
    onPreview: (ImageFile) -> Unit,
) {
    val s = strings()

    Column {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(80.dp))
            Text(
                s.t(StringKey.IMPORT_SORT_NAME),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(3f),
            )
            Text(
                s.t(StringKey.IMPORT_TYPE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                s.t(StringKey.IMPORT_SIZE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                s.t(StringKey.IMPORT_DATE),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1.5f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn {
            items(images, key = { it.id }) { image ->
                ImageListRow(
                    image = image,
                    onToggle = { onToggle(image.id) },
                    onPreview = { onPreview(image) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
internal fun ImageListRow(image: ImageFile, onToggle: () -> Unit, onPreview: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onPreview)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = image.isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        ThumbnailImage(
            file = image.file,
            maxPx = IMAGE_PREVIEW_THUMB_PX,
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
            durationText = image.metadata?.durationFormatted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            image.fileName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f),
        )
        Text(
            image.fileType.displayName,
            style = MaterialTheme.typography.bodySmall,
            color =
                when {
                    image.fileType.isVideo -> MaterialTheme.colorScheme.secondary
                    image.fileType.isRaw -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        Text(
            formatFileSize(image.fileSize),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            image.dateTakenFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
    }
}
