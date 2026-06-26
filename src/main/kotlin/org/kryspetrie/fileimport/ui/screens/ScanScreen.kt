package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.kryspetrie.fileimport.application.ScanService
import org.kryspetrie.fileimport.domain.model.DetectedPhoto
import org.kryspetrie.fileimport.infrastructure.adapter.toProcessedImage
import org.kryspetrie.fileimport.domain.model.PhotoCorner
import org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.NamingPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.screens.scan.EditPhotoDialog
import org.kryspetrie.fileimport.ui.screens.scan.ScanActionBar
import org.kryspetrie.fileimport.ui.screens.scan.ScanImagePreview
import org.kryspetrie.fileimport.ui.screens.scan.ScanPhotoList

/** Screen for photo scan preview and editing. */
@Composable
fun ScanScreen(
    filepaths: List<String>,
    destinationPath: String,
    onFinished: () -> Unit,
    scanService: ScanService,
    namingPort: NamingPort,
    imageRepository: ImageRepositoryPort,
    settingsPort: SettingsPort? = null,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsPort?.observeSettings()?.collectAsState() ?: remember { mutableStateOf(null) }
    var currentFileIndex by remember { mutableStateOf(0) }
    var currentDetectedPhotos by remember { mutableStateOf<List<DetectedPhoto>>(emptyList()) }
    var exportProgress by remember { mutableStateOf(0) }
    var exportTotal by remember { mutableStateOf(0) }
    var imagePreviewBounds by remember { mutableStateOf(Rect.Zero) }
    var draggedCornerPhotoIndex by remember { mutableStateOf<Int?>(null) }
    var draggedCornerName by remember { mutableStateOf<String?>(null) }
    var editingPhotoIndex by remember { mutableStateOf<Int?>(null) }

    // Load detected photos when file changes
    LaunchedEffect(currentFileIndex) {
        if (currentFileIndex < filepaths.size) {
            currentDetectedPhotos = scanService.detectPhotos(filepaths[currentFileIndex])
        }
    }

    val currentFilePath = if (currentFileIndex < filepaths.size) filepaths[currentFileIndex] else ""
    val currentFile = if (currentFilePath.isNotEmpty()) File(currentFilePath) else null
    val currentImage: BufferedImage? =
        if (currentFile != null && currentFile.exists()) {
            try {
                ImageIO.read(currentFile)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    // --- Event handlers ---

    val updatePhotoCorner = { index: Int, corner: String, x: Float, y: Float ->
        val photo = currentDetectedPhotos[index]
        val updatedPhoto =
            when (corner) {
                "topLeft" -> photo.copy(topLeft = PhotoCorner.create(x.toInt(), y.toInt()))
                "topRight" -> photo.copy(topRight = PhotoCorner.create(x.toInt(), y.toInt()))
                "bottomLeft" -> photo.copy(bottomLeft = PhotoCorner.create(x.toInt(), y.toInt()))
                "bottomRight" -> photo.copy(bottomRight = PhotoCorner.create(x.toInt(), y.toInt()))
                else -> photo
            }
        currentDetectedPhotos =
            currentDetectedPhotos.mapIndexed { i, p -> if (i == index) updatedPhoto else p }
    }

    val updatePhotoMetadata = { index: Int, config: PhotoScanConfiguration ->
        val photo = currentDetectedPhotos[index]
        currentDetectedPhotos =
            currentDetectedPhotos.mapIndexed { i, p ->
                if (i == index) photo.copy(configuration = config) else p
            }
    }

    // --- Main UI ---

    if (currentImage == null) {
        if (currentFileIndex >= filepaths.size) {
            Text("All scans processed!")
            Button(onClick = onFinished) { Text("Finish") }
        } else {
            Text("Could not load image: $currentFilePath")
        }
        return
    }

    Column {
        ScanImagePreview(
            currentImage = currentImage,
            currentFile = currentFile,
            detectedPhotos = currentDetectedPhotos,
            imagePreviewBounds = imagePreviewBounds,
            onBoundsChanged = { imagePreviewBounds = it },
            draggedCornerPhotoIndex = draggedCornerPhotoIndex,
            draggedCornerName = draggedCornerName,
            onCornerDrag = { index, corner, x, y -> updatePhotoCorner(index, corner, x, y) },
            onCornerClick = { index, corner ->
                draggedCornerPhotoIndex = index
                draggedCornerName = corner
            },
            modifier = Modifier.padding(8.dp),
        )

        ScanPhotoList(
            detectedPhotos = currentDetectedPhotos,
            onRemovePhoto = { index ->
                currentDetectedPhotos = currentDetectedPhotos.filterIndexed { i, _ -> i != index }
            },
            onEditPhoto = { index -> editingPhotoIndex = index },
            modifier = Modifier.padding(8.dp),
        )

        // Export progress
        if (exportTotal > 0) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Export Progress: $exportProgress / $exportTotal",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = {
                            if (exportTotal > 0) exportProgress.toFloat() / exportTotal else 0f
                        }
                    )
                }
            }
        }

        ScanActionBar(
            canNavigateNext = currentFileIndex < filepaths.size - 1,
            onRedetect = { currentDetectedPhotos = emptyList() },
            onAddPhoto = {
                val bounds = currentImage.let { img -> img.width to img.height }
                val newPhoto =
                    DetectedPhoto(
                        topLeft = PhotoCorner.create(100, 100),
                        topRight = PhotoCorner.create(bounds.first - 100, 100),
                        bottomLeft = PhotoCorner.create(100, bounds.second - 100),
                        bottomRight = PhotoCorner.create(bounds.first - 100, bounds.second - 100),
                    )
                currentDetectedPhotos = currentDetectedPhotos + newPhoto
            },
            onSkip = { currentFileIndex++ },
            onExportAll = {
                if (currentFile != null && currentFileIndex < filepaths.size) {
                    exportTotal = currentDetectedPhotos.size
                    exportProgress = 0
                    currentDetectedPhotos.forEachIndexed { index, photo ->
                        scanService.exportPhoto(
                            BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB).toProcessedImage(),
                            destinationPath,
                            currentFile,
                            index + 1,
                            photo.configuration,
                        )
                        exportProgress = index + 1
                    }
                }
            },
            onNext = {
                if (currentFileIndex < filepaths.size - 1) {
                    currentFileIndex++
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.padding(8.dp),
        )
    }

    // Photo editing dialog
    editingPhotoIndex?.let { index ->
        val photo = currentDetectedPhotos.getOrNull(index) ?: return@let
        EditPhotoDialog(
            photo = photo,
            metadataHistory = settings?.metadataHistory,
            onRecordMetadataSet = settingsPort?.let { port ->
                { set ->
                    scope.launch {
                        val current = port.observeSettings().first()
                        port.saveSettings(current.addMetadataSet(set))
                    }
                }
            },
            onClose = { editingPhotoIndex = null },
            onConfigChange = { config -> updatePhotoMetadata(index, config) },
        )
    }
}
