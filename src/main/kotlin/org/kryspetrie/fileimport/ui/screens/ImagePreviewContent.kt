package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun ImageContentArea(
    filteredAndSorted: List<ImageFile>,
    viewModel: ImagePreviewViewModel,
    density: Density,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filteredAndSorted.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxWidth())
    } else {
        Row(modifier = modifier) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (viewModel.viewMode) {
                    ImagePreviewViewModel.ViewMode.LIST ->
                        ImageListView(
                            filteredAndSorted,
                            onToggle = onToggleSelection,
                            onPreview = { viewModel.previewImage = it },
                        )
                    ImagePreviewViewModel.ViewMode.GRID ->
                        ImageGridView(
                            filteredAndSorted,
                            onToggle = onToggleSelection,
                            onPreview = { viewModel.previewImage = it },
                        )
                }
            }

            viewModel.previewImage?.let { img ->
                // Drag handle
                val dragInteraction = remember { MutableInteractionSource() }
                val isDragHovered by dragInteraction.collectIsHoveredAsState()
                Box(
                    modifier =
                        Modifier.width(8.dp)
                            .fillMaxHeight()
                            .hoverable(dragInteraction)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state =
                                    rememberDraggableState { deltaPx ->
                                        val deltaDp = with(density) { deltaPx.toDp().value }
                                        viewModel.paneWidthDp =
                                            (viewModel.paneWidthDp - deltaDp).coerceIn(
                                                ImagePreviewViewModel.PANE_MIN_DP,
                                                ImagePreviewViewModel.PANE_MAX_DP,
                                            )
                                    },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.width(2.dp)
                                .fillMaxHeight(0.3f)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isDragHovered)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                    )
                }

                PreviewSidePane(
                    image = img,
                    modifier = Modifier.width(viewModel.paneWidthDp.dp).fillMaxHeight(),
                    onClose = { viewModel.previewImage = null },
                    onFullScreen = { viewModel.fullScreenImage = img },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Empty state
// ---------------------------------------------------------------------------

@Composable
internal fun EmptyState(modifier: Modifier = Modifier) {
    val s = strings()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Image,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                s.t(StringKey.ERROR_NO_FILES_FOUND),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
