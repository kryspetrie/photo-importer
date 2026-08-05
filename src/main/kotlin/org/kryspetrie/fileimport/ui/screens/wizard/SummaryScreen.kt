package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.summary.ExportBottomBar
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/**
 * Summary screen with a two-panel layout: scrollable photo list on the left, large preview on the
 * right. Each list item shows a thumbnail with metadata; the right panel shows a large
 * perspective-corrected preview with rotation, aspect ratio, and correction strategy controls. Uses
 * [PreviewCache] to avoid recomputing perspective correction. Supports full-screen preview on image
 * click.
 */
@Composable
fun SummaryScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    var selectedIndex by remember { mutableStateOf(0) }

    val boxCount = boundingBoxList.size()

    Column(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.Enter &&
                        boxCount > 0
                ) {
                    onExport()
                    true
                } else false
            },
    ) {
        SummaryTopAppBar(
            photoCount = boundingBoxList.size(),
            onRotateAllCW = { state.configs.rotateAllBoxesCW() },
            onRotateAllCCW = { state.configs.rotateAllBoxesCCW() },
            onClearAll = { state.configs.clearAllConfigurations() },
        )

        SummaryScreenContent(
            modifier = Modifier.weight(1f),
            image = image,
            previewCache = previewCache,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { selectedIndex = it },
            onConfigChange = { boxId, config ->
                state.configs.setPhotoScanConfiguration(boxId, config)
            },
            onBoxDelete = { index ->
                state.boxes.removeBox(index)
                val newSize = boundingBoxList.size() - 1
                if (selectedIndex >= newSize && newSize > 0) {
                    selectedIndex = newSize - 1
                }
            },
            onRotateAllCW = { state.configs.rotateAllBoxesCW() },
            onRotateAllCCW = { state.configs.rotateAllBoxesCCW() },
            onClearAllConfigurations = { state.configs.clearAllConfigurations() },
            state = state,
        )

        ExportBottomBar(
            photoCount = boundingBoxList.size(),
            onBack = onBack,
            onExport = onExport,
        )
    }
}

/** Content area of the summary screen: either an empty-state message or the two-panel layout. */
@Composable
private fun SummaryScreenContent(
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
    if (boundingBoxList.isEmpty()) {
        EmptyPhotoState(modifier)
    } else {
        val clampedIndex = selectedIndex.coerceIn(0, boundingBoxList.size() - 1)
        TwoPanelLayout(
            modifier = modifier,
            image = image,
            previewCache = previewCache,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedIndex = clampedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            onConfigChange = onConfigChange,
            onBoxDelete = onBoxDelete,
            onRotateAllCW = onRotateAllCW,
            onRotateAllCCW = onRotateAllCCW,
            onClearAllConfigurations = onClearAllConfigurations,
            state = state,
        )
    }
}

/** Empty state shown when no photos are detected. */
@Composable
private fun EmptyPhotoState(modifier: Modifier) {
    val s = strings()
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            s.t(StringKey.SCAN_NO_PHOTOS_DETECTED),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
