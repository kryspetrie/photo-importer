package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.edit.RotationSection
import org.kryspetrie.fileimport.ui.shared.face.FaceRegionPreviewOverlay
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

@Composable
internal fun MetadataEditorPreviewPane(
    vm: MetadataEditorViewModel,
    onPickEditorImages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val density = LocalUiDensityScale.current

    Column(modifier = modifier.fillMaxHeight()) {
        if (vm.isLoadingImage) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (
            vm.currentImage != null &&
                MetadataEditorPanelController.shouldShowPreview(
                    vm.isMultiEditMode,
                    vm.selectedIndices.size,
                )
        ) {
            val previewBitmap =
                remember(vm.currentImage) { vm.currentImage?.toComposeImageBitmap() }
            val imageWidth = vm.currentImage?.width ?: 1
            val imageHeight = vm.currentImage?.height ?: 1
            var containerWidthPx by remember { mutableIntStateOf(0) }
            var containerHeightPx by remember { mutableIntStateOf(0) }

            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged {
                            containerWidthPx = it.width
                            containerHeightPx = it.height
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = s.t(StringKey.ACC_SELECTED_IMAGE),
                        modifier =
                            Modifier.fillMaxSize().graphicsLayer {
                                rotationZ = vm.state.selectedConfig.rotationDegrees.toFloat()
                            },
                        contentScale = ContentScale.Fit,
                    )
                }
                if (!vm.showFaceTagging && vm.state.selectedConfig.faceRegions.isNotEmpty()) {
                    FaceRegionPreviewOverlay(
                        faceRegions = vm.state.selectedConfig.faceRegions,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                    )
                }
                OutlinedButton(
                    onClick = { vm.toggleFaceTagging() },
                    modifier =
                        Modifier.align(Alignment.TopEnd)
                            .padding(8.dp)
                            .height(density.controlMinHeight),
                ) {
                    Icon(
                        Icons.Default.Face,
                        s.t(StringKey.WIZARD_TAG_EDITOR),
                        Modifier.size(density.iconSize),
                    )
                }
            }
            RotationSection(
                rotationDegrees = vm.state.selectedConfig.rotationDegrees,
                onRotateCW = { vm.state.updateSelectedConfig { it.cycleRotationCW() } },
                onRotateCCW = { vm.state.updateSelectedConfig { it.cycleRotationCCW() } },
                onRotate180 = { vm.state.updateSelectedConfig { it.rotate180() } },
            )
        } else if (vm.isMultiEditMode && vm.selectedIndices.size > 1) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        s.t(StringKey.META_PHOTOS_SELECTED),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        s.t(
                            StringKey.META_PHOTOS_SELECTED,
                            "count" to vm.selectedIndices.size.toString(),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        s.t(StringKey.META_NO_IMAGE),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        s.t(StringKey.META_SELECT_AN_IMAGE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onPickEditorImages,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(s.t(StringKey.META_SELECT_IMAGES))
                    }
                }
            }
        }
    }
}
