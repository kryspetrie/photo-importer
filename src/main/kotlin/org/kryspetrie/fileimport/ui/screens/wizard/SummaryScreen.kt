package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBox
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.screens.wizard.summary.DestinationSelector
import org.kryspetrie.fileimport.ui.screens.wizard.summary.ExportBottomBar

/**
 * Summary screen showing all detected photos as a scrolling grid of image tiles. Each tile displays
 * the cropped+rotated preview with inline rotation buttons. Warp-stretch perspective correction is
 * always applied.
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

    Scaffold(
        topBar = {
            SummaryTopAppBar(
                photoCount = boundingBoxList.size(),
                exportDestination = exportDestination,
                onDestinationChange = onDestinationChange,
                onRotateAllCW = { state.rotateAllBoxesCW() },
                onRotateAllCCW = { state.rotateAllBoxesCCW() },
                onClearAll = { state.clearAllConfigurations() },
            )
        },
        content = { paddingValues ->
            PhotoGrid(
                image = image,
                perspectiveService = perspectiveService,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                onConfigChange = { boxId, config -> state.setPhotoConfiguration(boxId, config) },
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
private fun SummaryTopAppBar(
    photoCount: Int,
    exportDestination: String,
    onDestinationChange: ((String) -> Unit)?,
    onRotateAllCW: () -> Unit,
    onRotateAllCCW: () -> Unit,
    onClearAll: () -> Unit,
) {
    TopAppBar(
        title = { Text("Photo Summary — $photoCount photo(s)") },
        actions = {
            // Bulk rotation buttons in the top bar
            OutlinedButton(onClick = onRotateAllCCW, modifier = Modifier.height(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateLeft, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("All CCW", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onRotateAllCW, modifier = Modifier.height(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("All CW", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.height(32.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            DestinationSelector(
                destination = exportDestination,
                onDestinationChange = { onDestinationChange?.invoke(it) },
            )
        },
    )
}

/**
 * Scrolling grid of photo tiles. Each tile shows the perspective-corrected and rotated preview
 * image with rotation controls overlaid at the bottom.
 */
@Composable
private fun PhotoGrid(
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionService,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    onConfigChange: (String, PhotoConfiguration) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(boundingBoxList.boxes) { index, box ->
            val config = photoConfigurations[box.id] ?: PhotoConfiguration()
            val previewImage =
                remember(image, box, config) {
                    cropAndRotateBoundingBox(image, box, config, perspectiveService)
                }

            PhotoTile(
                index = index,
                box = box,
                config = config,
                previewImage = previewImage,
                onRotateCW = { onConfigChange(box.id, config.cycleRotationCW()) },
                onRotateCCW = { onConfigChange(box.id, config.cycleRotationCCW()) },
            )
        }
    }
}

/**
 * A single tile in the photo grid. Shows the cropped+rotated preview image with rotation buttons at
 * the bottom.
 */
@Composable
private fun PhotoTile(
    index: Int,
    box: BoundingBox,
    config: PhotoConfiguration,
    previewImage: BufferedImage?,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Image area
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (previewImage != null) {
                    Image(
                        bitmap = previewImage.toComposeImageBitmap(),
                        contentDescription = "Photo ${index + 1} preview",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text(
                        "Could not render preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Bottom bar with rotation buttons and info
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: rotate CCW
                    IconButton(onClick = onRotateCCW, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateLeft,
                            "Rotate counter-clockwise",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Center: photo label + rotation state
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Photo ${index + 1}", style = MaterialTheme.typography.labelSmall)
                        if (config.rotationDegrees != 0) {
                            Text(
                                "${config.rotationDegrees}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Right: rotate CW
                    IconButton(onClick = onRotateCW, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            "Rotate clockwise",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Crops the bounding box region from the source image, applies perspective correction
 * (warp-stretch, always on), then rotates according to the [PhotoConfiguration].
 */
private fun cropAndRotateBoundingBox(
    image: BufferedImage,
    box: BoundingBox,
    config: PhotoConfiguration,
    perspectiveService: PerspectiveCorrectionService,
): BufferedImage? {
    return try {
        // Always apply perspective/warp-stretch correction
        val detectedPhoto = boxToDetectedPhoto(box)
        val corrected = perspectiveService.correctPerspective(image, detectedPhoto)

        // Apply rotation if configured
        if (config.rotationDegrees != 0) {
            rotateBufferedImage(corrected, rotationFromDegrees(config.rotationDegrees))
        } else {
            corrected
        }
    } catch (_: Exception) {
        null
    }
}

/** Rotates a [BufferedImage] by the given [RotationAngle]. */
private fun rotateBufferedImage(image: BufferedImage, rotation: RotationAngle): BufferedImage {
    if (rotation == RotationAngle.NONE) return image

    val newWidth: Int
    val newHeight: Int

    when (rotation) {
        RotationAngle.CW_90,
        RotationAngle.CCW_90 -> {
            newWidth = image.height
            newHeight = image.width
        }
        else -> {
            newWidth = image.width
            newHeight = image.height
        }
    }

    val rotated =
        BufferedImage(
            newWidth.coerceAtLeast(1),
            newHeight.coerceAtLeast(1),
            BufferedImage.TYPE_INT_RGB,
        )

    val graphics = rotated.createGraphics()
    graphics.background = java.awt.Color.BLACK

    when (rotation) {
        RotationAngle.CW_90 -> {
            graphics.translate(newWidth, 0)
            graphics.rotate(Math.PI / 2)
        }
        RotationAngle.CCW_90 -> {
            graphics.translate(0, newHeight)
            graphics.rotate(-Math.PI / 2)
        }
        RotationAngle.CW_180 -> {
            graphics.translate(newWidth / 2.0, newHeight / 2.0)
            graphics.rotate(Math.PI)
            graphics.translate(-image.width / 2.0, -image.height / 2.0)
        }
        RotationAngle.NONE -> {
            // No rotation
        }
    }

    graphics.drawImage(image, 0, 0, null)
    graphics.dispose()

    return rotated
}

/** Converts degrees (0, 90, 180, 270) to RotationAngle. */
private fun rotationFromDegrees(degrees: Int): RotationAngle {
    return when (degrees) {
        90 -> RotationAngle.CW_90
        180 -> RotationAngle.CW_180
        270 -> RotationAngle.CCW_90
        -90 -> RotationAngle.CCW_90
        else -> RotationAngle.NONE
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
