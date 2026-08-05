package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize

@Composable
internal fun ImageGridView(
    images: List<ImageFile>,
    onToggle: (String) -> Unit,
    onPreview: (ImageFile) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(images, key = { it.id }) { image ->
            ImageGridTile(
                image = image,
                onToggle = { onToggle(image.id) },
                onPreview = { onPreview(image) },
            )
        }
    }
}

@Composable
internal fun ImageGridTile(image: ImageFile, onToggle: () -> Unit, onPreview: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border =
            BorderStroke(
                1.dp,
                if (image.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .clickable(onClick = onPreview)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    ThumbnailImage(
                        file = image.file,
                        maxPx = IMAGE_PREVIEW_TILE_PX,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        durationText = image.metadata?.durationFormatted,
                    )
                }
                Box(
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Checkbox(
                        checked = image.isSelected,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    image.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${formatFileSize(image.fileSize)} \u00B7 ${image.fileType.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
