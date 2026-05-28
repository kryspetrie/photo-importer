package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.screens.wizard.summary.ExportBottomBar
import org.kryspetrie.fileimport.ui.screens.wizard.summary.PhotoListPanel
import org.kryspetrie.fileimport.ui.screens.wizard.summary.PhotoPreviewPanel

/**
 * Summary screen showing all detected photos with correction options. Allows per-photo and bulk
 * configuration of perspective, rotation, and aspect ratio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    exportDestination: String = System.getProperty("user.home") + "/Pictures/PhotoScan",
    onDestinationChange: ((String) -> Unit)? = null,
) {
    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    var selectedPreviewIndex by remember { mutableIntStateOf(0) }
    val currentBox =
        remember(boundingBoxList, selectedPreviewIndex) {
            if (selectedPreviewIndex >= 0 && selectedPreviewIndex < boundingBoxList.size()) {
                boundingBoxList.boxes[selectedPreviewIndex]
            } else null
        }
    val currentConfig =
        remember(photoConfigurations, currentBox) {
            currentBox?.let { photoConfigurations[it.id] } ?: PhotoConfiguration()
        }
    val previewImage =
        remember(image, currentBox, currentConfig) {
            currentBox?.let { box ->
                cropBoundingBox(image, box, currentConfig, perspectiveService)
            }
        }

    Scaffold(
        topBar = { SummaryTopAppBar(photoCount = boundingBoxList.size()) },
        content = { paddingValues ->
            SummaryContent(
                state = state,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedPreviewIndex = selectedPreviewIndex,
                onSelectedPreviewIndexChange = { selectedPreviewIndex = it },
                previewImage = previewImage,
                exportDestination = exportDestination,
                onDestinationChange = onDestinationChange,
                modifier = modifier.padding(paddingValues),
            )
        },
        bottomBar = {
            ExportBottomBar(
                photoCount = boundingBoxList.size(),
                onBack = onBack,
                onExport = onExport,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryTopAppBar(photoCount: Int) {
    TopAppBar(
        title = { Text("Photo Summary") },
        actions = {
            Text(
                "$photoCount photo(s)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        },
    )
}

@Composable
private fun SummaryContent(
    state: PhotoScanWizardState,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedPreviewIndex: Int,
    onSelectedPreviewIndexChange: (Int) -> Unit,
    previewImage: BufferedImage?,
    exportDestination: String,
    onDestinationChange: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        PhotoListPanel(
            state = state,
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedPreviewIndex = selectedPreviewIndex,
            onSelectedPreviewIndexChange = onSelectedPreviewIndexChange,
            exportDestination = exportDestination,
            onDestinationChange = onDestinationChange,
            modifier = Modifier.weight(0.55f).fillMaxHeight().padding(16.dp),
        )
        PhotoPreviewPanel(
            previewImage = previewImage,
            modifier = Modifier.weight(0.45f).fillMaxHeight().padding(16.dp),
        )
    }
}

private fun cropBoundingBox(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoConfiguration,
    perspectiveService: PerspectiveCorrectionService,
): BufferedImage? {
    return try {
        if (config.perspectiveCorrectionEnabled) {
            // Apply true perspective correction using homography
            val detectedPhoto = boxToDetectedPhoto(box)
            perspectiveService.correctPerspective(image, detectedPhoto)
        } else {
            // Simple axis-aligned crop from bounding box
            val bounds = box.corners
            val minX =
                minOf(bounds.topLeft.x, bounds.bottomLeft.x).toInt().coerceIn(0, image.width - 1)
            val minY =
                minOf(bounds.topLeft.y, bounds.topRight.y).toInt().coerceIn(0, image.height - 1)
            val maxX =
                maxOf(bounds.topRight.x, bounds.bottomRight.x).toInt().coerceIn(0, image.width)
            val maxY =
                maxOf(bounds.bottomLeft.y, bounds.bottomRight.y).toInt().coerceIn(0, image.height)
            val cropWidth = maxX - minX
            val cropHeight = maxY - minY
            if (cropWidth <= 0 || cropHeight <= 0) return null
            image.getSubimage(minX, minY, cropWidth, cropHeight)
        }
    } catch (_: Exception) {
        null
    }
}

/** Converts a [BoundingBox] to a [DetectedPhoto] for use with [PerspectiveCorrectionService]. */
private fun boxToDetectedPhoto(box: BoundingBox): DetectedPhoto {
    return DetectedPhoto(
        topLeft = PhotoCorner(box.corners.topLeft.x.toFloat(), box.corners.topLeft.y.toFloat()),
        topRight = PhotoCorner(box.corners.topRight.x.toFloat(), box.corners.topRight.y.toFloat()),
        bottomLeft =
            PhotoCorner(box.corners.bottomLeft.x.toFloat(), box.corners.bottomLeft.y.toFloat()),
        bottomRight =
            PhotoCorner(box.corners.bottomRight.x.toFloat(), box.corners.bottomRight.y.toFloat()),
    )
}
