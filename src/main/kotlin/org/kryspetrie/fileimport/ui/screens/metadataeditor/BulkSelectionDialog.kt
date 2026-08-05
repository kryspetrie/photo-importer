package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.awt.image.BufferedImage
import java.io.File
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkSelectionDialog(
    state: BulkEditState,
    thumbnailCache: java.util.concurrent.ConcurrentHashMap<String, BufferedImage>,
    thumbnailCacheRevision: Int,
    onEnsureThumbnail: suspend (File) -> Unit,
    selectedIndices: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = strings()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            s.t(StringKey.META_SELECT_PHOTOS),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            s.t(
                                StringKey.META_SELECTED_OF_TOTAL,
                                "selected" to selectedIndices.size.toString(),
                                "total" to state.fileCount.toString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSelectAll, modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.SelectAll, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                s.t(StringKey.ACTION_ALL),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        OutlinedButton(onClick = onSelectNone, modifier = Modifier.height(32.dp)) {
                            Icon(Icons.Default.Deselect, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                s.t(StringKey.ACTION_NONE),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, s.close) }
                    }
                }
                HorizontalDivider()

                // Grid of thumbnails
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    gridItemsIndexed(state.files) { index, file ->
                        val isSelected = index in selectedIndices
                        Card(
                            modifier =
                                Modifier.height(100.dp).clickable { onToggleSelection(index) },
                            shape = RoundedCornerShape(6.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                ),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LaunchedEffect(file.absolutePath, thumbnailCacheRevision) {
                                    onEnsureThumbnail(file)
                                }
                                val thumb = thumbnailCache[file.absolutePath]
                                if (thumb != null) {
                                    val bitmap = remember(thumb) { thumb.toComposeImageBitmap() }
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = file.name,
                                        modifier = Modifier.fillMaxSize().padding(2.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Image,
                                        s.t(StringKey.ACC_LOADING),
                                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSelection(index) },
                                    modifier = Modifier.align(Alignment.TopStart).size(20.dp),
                                )
                                Text(
                                    file.name,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Footer with confirm
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(s.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, enabled = selectedIndices.isNotEmpty()) {
                        Text(
                            if (selectedIndices.size == 1) s.t(StringKey.META_EDIT_ONE_PHOTO)
                            else
                                s.t(
                                    StringKey.META_EDIT_N_PHOTOS,
                                    "count" to selectedIndices.size.toString(),
                                )
                        )
                    }
                }
            }
        }
    }
}
