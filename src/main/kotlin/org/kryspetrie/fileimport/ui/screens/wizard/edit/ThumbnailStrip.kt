package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.ui.wizard.state.BoundingBoxList
import org.kryspetrie.fileimport.ui.wizard.state.PhotoConfiguration
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.ui.components.PreviewCache

/** Horizontal scrollable thumbnail strip for photo selection. */
@Composable
internal fun ThumbnailStrip(
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    onSelect: (Int) -> Unit,
    onDeselectAll: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(boundingBoxList.boxes) { index, box ->
            val config = photoConfigurations[box.id] ?: PhotoConfiguration()
            val visualConfig = PhotoConfiguration(rotationDegrees = config.rotationDegrees)
            val thumbnail = previewCache.getThumbnail(image, box, visualConfig)
            val isSelected = index in selectedIndices
            Card(
                modifier = Modifier.width(100.dp).height(80.dp).clickable { onSelect(index) },
                shape = RoundedCornerShape(6.dp),
                border =
                    BorderStroke(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                    ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = "Photo ${index + 1}",
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    if (isSelected) {
                        if (isMultiEditMode) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = { onSelect(index) },
                                modifier = Modifier.align(Alignment.TopStart).size(18.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                            )
                        }
                    }
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                    )
                    if (config.hasMetadata()) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}
