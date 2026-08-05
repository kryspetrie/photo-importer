package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

@Composable
internal fun EditCenterPreview(
    state: PhotoScanWizardState,
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndices: Set<Int>,
    isMultiEditMode: Boolean,
    onFullscreenPreview: (Int) -> Unit,
    onSelectFaces: (Int) -> Unit,
    onPickBackImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Column(modifier = modifier.fillMaxHeight()) {
        if (selectedIndices.size == 1 && !isMultiEditMode) {
            val selectedIndex = selectedIndices.first()
            val box = boundingBoxList.boxes[selectedIndex]
            val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
            val visualConfig = PhotoScanConfiguration(rotationDegrees = config.rotationDegrees)
            val previewImage = previewCache.getFullPreview(image, box, visualConfig)
            val previewBitmap = remember(previewImage) { previewImage?.toComposeImageBitmap() }
            Box(
                modifier =
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        onFullscreenPreview(selectedIndex)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription =
                            s.t(
                                StringKey.ACC_PREVIEW_IMAGE,
                                "index" to (selectedIndex + 1).toString(),
                            ),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    if (config.hasBackImage()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    s.t(StringKey.META_BACK_IMAGE_ASSIGNED),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (config.backImageMode == "combine")
                                        s.t(StringKey.META_BACK_COMBINED)
                                    else s.t(StringKey.META_BACK_APPENDED),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                OutlinedButton(
                                    onClick = onPickBackImage,
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        s.t(StringKey.META_CHANGE),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        state.configs.updatePhotoScanConfiguration(box.id) {
                                            it.copy(
                                                backImageMode = null,
                                                backImageSourcePath = null,
                                                backCropNormalized = null,
                                                backCropRotation = 0,
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        s.t(StringKey.META_REMOVE),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${selectedIndices.size} photos selected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (selectedIndices.size == 1 && !isMultiEditMode) {
            val selectedIndex = selectedIndices.first()
            val box = boundingBoxList.boxes[selectedIndex]
            val config = photoConfigurations[box.id] ?: PhotoScanConfiguration()
            RotationSection(
                rotationDegrees = config.rotationDegrees,
                onRotateCW = {
                    state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCW() }
                },
                onRotateCCW = {
                    state.configs.updatePhotoScanConfiguration(box.id) { it.cycleRotationCCW() }
                },
                onRotate180 = {
                    state.configs.updatePhotoScanConfiguration(box.id) { it.rotate180() }
                },
            )
            PhotoActionsSection(
                hasBackImage = config.hasBackImage(),
                onTagPhoto = { onSelectFaces(selectedIndex) },
                onAddBack = onPickBackImage,
            )
        } else if (isMultiEditMode && selectedIndices.size > 1) {
            BatchRotationControls(
                selectedIndices = selectedIndices,
                boundingBoxList = boundingBoxList,
                state = state,
            )
        }
    }
}

/** Panel under Rotation for primary photo actions (tag people / attach back side). */
@Composable
private fun PhotoActionsSection(
    hasBackImage: Boolean,
    onTagPhoto: () -> Unit,
    onAddBack: () -> Unit,
) {
    val s = strings()
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onTagPhoto,
                modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.Sell,
                    s.t(StringKey.ACC_TAG_PEOPLE),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(s.t(StringKey.FIELD_TAG_PHOTO), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onAddBack,
                modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.Image,
                    s.t(StringKey.META_SELECT_BACK_OF_PHOTO),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (hasBackImage) s.t(StringKey.META_CHANGE)
                    else s.t(StringKey.META_ADD_BACK),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun BatchRotationControls(
    selectedIndices: Set<Int>,
    boundingBoxList: BoundingBoxList,
    state: PhotoScanWizardState,
) {
    val s = strings()
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.t(StringKey.WIZARD_ROTATE_ALL), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = {
                    selectedIndices.forEach { idx ->
                        if (idx < boundingBoxList.size()) {
                            state.configs.updatePhotoScanConfiguration(
                                boundingBoxList.boxes[idx].id
                            ) {
                                it.cycleRotationCCW()
                            }
                        }
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateLeft,
                    s.t(StringKey.ACC_ROTATE_CCW),
                    Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = {
                    selectedIndices.forEach { idx ->
                        if (idx < boundingBoxList.size()) {
                            state.configs.updatePhotoScanConfiguration(
                                boundingBoxList.boxes[idx].id
                            ) {
                                it.rotate180()
                            }
                        }
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(Icons.Default.Refresh, s.t(StringKey.FIELD_ROTATE_180), Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    selectedIndices.forEach { idx ->
                        if (idx < boundingBoxList.size()) {
                            state.configs.updatePhotoScanConfiguration(
                                boundingBoxList.boxes[idx].id
                            ) {
                                it.cycleRotationCW()
                            }
                        }
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    s.t(StringKey.ACC_ROTATE_CW),
                    Modifier.size(16.dp),
                )
            }
        }
    }
}
