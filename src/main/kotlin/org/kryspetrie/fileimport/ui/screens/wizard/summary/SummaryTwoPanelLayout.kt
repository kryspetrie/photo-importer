package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/** Two-panel layout: photo sidebar list on left, detail preview on right. */
@Composable
internal fun TwoPanelLayout(
    modifier: Modifier,
    image: BufferedImage,
    previewCache: PreviewCache,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoScanConfiguration>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onConfigChange: (String, PhotoScanConfiguration) -> Unit,
    onBoxDelete: (Int) -> Unit,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAllConfigurations: () -> Unit,
    state: PhotoScanWizardState,
) {
    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        PhotoSidebarList(
            image = image,
            previewCache = previewCache,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            onDelete = onBoxDelete,
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAll = onClearAllConfigurations,
            modifier = Modifier.weight(0.35f).fillMaxHeight(),
        )

        val selectedBox = boundingBoxList.boxes.getOrNull(selectedIndex)
        val selectedConfig =
            selectedBox?.let { photoConfigurations[it.id] ?: PhotoScanConfiguration() }
                ?: PhotoScanConfiguration()

        DetailPreviewPanel(
            image = image,
            previewCache = previewCache,
            box = selectedBox,
            config = selectedConfig,
            index = selectedIndex,
            totalPhotos = boundingBoxList.size(),
            onConfigChange = { config -> selectedBox?.let { onConfigChange(it.id, config) } },
            onRotateCW = {
                selectedBox?.let {
                    val current = photoConfigurations[it.id] ?: PhotoScanConfiguration()
                    onConfigChange(it.id, current.cycleRotationCW())
                }
            },
            onRotateCCW = {
                selectedBox?.let {
                    val current = photoConfigurations[it.id] ?: PhotoScanConfiguration()
                    onConfigChange(it.id, current.cycleRotationCCW())
                }
            },
            onPrev = { if (selectedIndex > 0) onSelectedIndexChange(selectedIndex - 1) },
            onNext = {
                if (selectedIndex < boundingBoxList.size() - 1)
                    onSelectedIndexChange(selectedIndex + 1)
            },
            modifier = Modifier.weight(0.65f).fillMaxHeight(),
        )
    }
}
