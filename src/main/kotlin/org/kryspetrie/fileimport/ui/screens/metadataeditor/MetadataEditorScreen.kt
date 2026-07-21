package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.RotationAngle
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.RotationBadge
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay

private const val MESSAGE_AUTO_CLEAR_MS = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: MetadataEditorViewModel = koinInject()
    val coroutineScope = rememberCoroutineScope()
    val currentSettings by vm.settingsPort.observeSettings().collectAsState(initial = AppSettings())

    // Wire current settings into the VM for read access
    vm.currentSettings = currentSettings

    // Observe settings
    LaunchedEffect(Unit) { vm.observeSettings(coroutineScope) }

    // Auto-clear messages after timeout
    LaunchedEffect(vm.state.message) {
        if (vm.state.message != null) {
            delay(MESSAGE_AUTO_CLEAR_MS)
            vm.state.clearMessage()
        }
    }

    // Load image when selection changes
    LaunchedEffect(vm.state.selectedIndex, vm.state.files) { vm.loadSelectedImage() }

    // Load thumbnails for sidebar
    LaunchedEffect(vm.state.files) { vm.loadThumbnails() }

    // Sync editState from current config when selection changes (single-edit mode)
    LaunchedEffect(vm.state.selectedIndex, vm.state.selectedConfig) {
        if (!vm.isMultiEditMode) {
            vm.editState.loadFrom(vm.state.selectedConfig)
        }
    }

    // Clear editState when switching to multi-edit
    LaunchedEffect(vm.isMultiEditMode) {
        if (vm.isMultiEditMode) {
            vm.editState.clear()
        }
    }

    // ── Source loading callbacks ──

    val loadSourcePath: (String) -> Unit = { path ->
        vm.loadSourceAsync(path, coroutineScope, onSettingsChange)
    }

    val onPickSourceFile: () -> Unit = {
        pickImageFile("Select Image File")?.let { loadSourcePath(it) }
    }

    val onPickSourceFolder: () -> Unit = {
        pickFolder("Select Image Folder")?.let { loadSourcePath(it) }
    }

    val onPickOutputFolder: () -> Unit = {
        pickFolder("Select Output Folder")?.let { vm.state.outputDirectory = it }
    }

    // ── Dialogs ──

    // Face name entry popup
    if (vm.showFaceNamePopup && vm.pendingFaceCoords != null) {
        EditDialog(onDismissRequest = { vm.dismissFaceNamePopup() }) {
            FaceNameEntryPanel(
                faceNameInput = vm.faceNameInput,
                onFaceNameInputChange = { vm.faceNameInput = it },
                selectedRegionType = vm.selectedRegionType,
                selectedFaceSize = vm.selectedFaceSize,
                onConfirm = { vm.confirmFaceName() },
                onCancel = { vm.dismissFaceNamePopup() },
            )
        }
    }

    // Back image picker dialog
    if (vm.showBackImagePicker) {
        val currentImageFile = vm.state.selectedFile
        val batchFiles = vm.state.files
        val preSelectedPath = vm.getPreSelectedBackPath()

        BackImagePickerDialog(
            batchFiles = batchFiles.ifEmpty { null },
            preSelectedPath = preSelectedPath,
            onConfirm = { sourcePath, cropResult, rotation, mode ->
                vm.onBackImageSelected(sourcePath, cropResult, rotation, mode)
            },
            onDismiss = { vm.dismissBackImagePicker() },
        )
    }

    // Location picker overlay
    if (vm.showLocationPicker && vm.locationPickerTargetIndices.isNotEmpty()) {
        LocationPickerOverlay(
            locationSearchService = vm.locationSearchService,
            geocodingPort = vm.geocodingPort,
            dispatcherProvider = vm.dispatcherProvider,
            initialLat = currentSettings.lastMapLat,
            initialLon = currentSettings.lastMapLon,
            initialZoom = currentSettings.lastMapZoom,
            onLocationSelected = { result -> vm.onLocationSelected(result) },
            onDismiss = { vm.dismissLocationPicker() },
            onMapLocationChanged = { lat, lon, zoom ->
                vm.updateMapLocation(lat, lon, zoom, coroutineScope)
            },
        )
    }

    // Bulk selection dialog
    if (vm.showBulkSelectionDialog && vm.isMultiEditMode) {
        BulkSelectionDialog(
            state = vm.state,
            thumbnailCache = vm.thumbnailCache,
            selectedIndices = vm.selectedIndices,
            onToggleSelection = { index -> vm.toggleSelection(index) },
            onSelectAll = { vm.selectAll() },
            onSelectNone = { vm.deselectAll() },
            onConfirm = { vm.dismissBulkSelectionDialog() },
            onDismiss = { vm.dismissBulkSelectionDialog() },
        )
    }

    // Auto-rotation result dialog
    if (vm.showAutoRotateDialog && vm.autoRotateResult != null) {
        val result = vm.autoRotateResult!!
        val filePath = vm.state.selectedFile?.absolutePath ?: ""
        val isJpeg = OrientationCorrectionService.isJpegFile(filePath)
        val nearestCorrectionDeg = vm.nearestCorrectionDeg(result)
        val currentRotation = vm.state.selectedConfig.rotationDegrees
        val correctedRotation = (currentRotation + nearestCorrectionDeg) % 360

        AlertDialog(
            onDismissRequest = { vm.dismissAutoRotateDialog() },
            title = { Text("Auto-Rotation Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Detected orientation: ${result.orientationDegrees.toInt()}° " +
                            "(confidence: ${(result.confidence * 100).toInt()}%)"
                    )
                    Text(
                        "Correction: ${result.correctionDegrees.toInt()}° " +
                            "(${result.nearestRotation.degrees}°)"
                    )
                    if (result.nearestRotation == RotationAngle.NONE) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                "Image appears upright — no rotation needed.",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text("New rotation would be: $currentRotation° → $correctedRotation°")
                        if (isJpeg) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "⚠ JPEG rotation is lossy — re-encoding degrades image quality. " +
                                        "This only updates metadata rotation, not pixels.",
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (result.nearestRotation != RotationAngle.NONE) {
                    TextButton(onClick = { vm.applyAutoRotation() }) { Text("Apply Rotation") }
                } else {
                    TextButton(onClick = { vm.dismissAutoRotateDialog() }) { Text("OK") }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissAutoRotateDialog() }) { Text("Cancel") }
            },
        )
    }

    // ── Model download dialog ──
    if (vm.showModelDownloadDialog) {
        ModelDownloadDialog(
            downloadState = vm.modelDownloadState,
            onDownload = { vm.downloadOrientationModel(coroutineScope) },
            onCancel = { vm.cancelModelDownload() },
            onRetry = { vm.downloadOrientationModel(coroutineScope) },
        )
    }

    // ── Main layout ──

    Scaffold(
        modifier =
            modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isMeta = isCtrlPressed(keyEvent)
                    when {
                        isMeta && keyEvent.key == Key.Comma -> {
                            vm.state.prevFile()
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            vm.state.nextFile()
                            true
                        }
                        isMeta && keyEvent.key == Key.Z && !keyEvent.isShiftPressed -> {
                            vm.undoLast(coroutineScope)
                            true
                        }
                        isMeta && keyEvent.key == Key.Z && keyEvent.isShiftPressed -> {
                            vm.redoLast(coroutineScope)
                            true
                        }
                        isMeta && keyEvent.key == Key.S -> {
                            vm.saveCurrentFile(coroutineScope)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bulk Metadata Editor",
                        style =
                            MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = vm.state.outputMode == OutputMode.OVERWRITE,
                            onClick = { vm.state.outputMode = OutputMode.OVERWRITE },
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Overwrite", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        RadioButton(
                            selected = vm.state.outputMode == OutputMode.SAVE_NEW,
                            onClick = { vm.state.outputMode = OutputMode.SAVE_NEW },
                            modifier = Modifier.size(24.dp),
                        )
                        Text("Save New", style = MaterialTheme.typography.labelSmall)
                    }
                    if (vm.state.outputMode == OutputMode.SAVE_NEW) {
                        FolderSelectionField(
                            value = vm.state.outputDirectory,
                            onValueChange = { vm.state.outputDirectory = it },
                            modifier = Modifier.width(220.dp).height(48.dp),
                            label = "Output",
                            placeholder = "Output folder...",
                            title = "Select Output Folder",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { vm.state.prevFile() },
                        enabled = vm.state.selectedIndex > 0,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Previous", style = MaterialTheme.typography.labelSmall)
                    }
                    if (vm.state.message != null) {
                        Text(
                            vm.state.message!!.text,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                when (vm.state.message!!.severity) {
                                    MessageSeverity.ERROR -> MaterialTheme.colorScheme.error
                                    MessageSeverity.INFO -> MaterialTheme.colorScheme.primary
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                        )
                    } else {
                        Text(
                            if (vm.state.fileCount == 0) "No files loaded"
                            else if (vm.state.modifiedCount > 0)
                                "${vm.state.selectedIndex + 1} of ${vm.state.fileCount} · ${vm.state.modifiedCount} unsaved"
                            else "${vm.state.selectedIndex + 1} of ${vm.state.fileCount}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (vm.state.canUndo) {
                            OutlinedButton(
                                onClick = { vm.undoLast(coroutineScope) },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateLeft,
                                    "Undo",
                                    Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (vm.state.canRedo) {
                            OutlinedButton(
                                onClick = { vm.redoLast(coroutineScope) },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    "Redo",
                                    Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Redo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (vm.state.modifiedCount > 1) {
                            Button(
                                onClick = { vm.saveAllModified(coroutineScope) },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Default.Save, "Save All", Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Save All (${vm.state.modifiedCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Button(
                            onClick = { vm.saveCurrentFile(coroutineScope) },
                            enabled = vm.state.selectedFile != null,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Default.Save, "Save", Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { vm.state.nextFile() },
                            enabled = vm.state.selectedIndex < vm.state.fileCount - 1,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text("Next", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        if (vm.state.files.isEmpty()) {
            // Empty state — show folder picker
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (vm.state.isLoading) {
                        CircularProgressIndicator()
                        Text("Loading files...", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Icon(
                            Icons.Default.FolderOpen,
                            "Open source",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Select a file or folder with images to edit metadata",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.width(400.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourcePathField(
                                value = vm.state.sourcePath,
                                onValueChange = { loadSourcePath(it) },
                                onPickFile = onPickSourceFile,
                                onPickFolder = onPickSourceFolder,
                                modifier = Modifier.fillMaxWidth(),
                                label = "Source",
                                placeholder = "Select file or folder...",
                                isError = vm.state.message?.severity == MessageSeverity.ERROR,
                            )
                            val recentPaths = currentSettings.metadataEditorRecentPaths
                            if (recentPaths.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.width(200.dp))
                                Text("Recent:", style = MaterialTheme.typography.labelMedium)
                                recentPaths.forEach { path ->
                                    OutlinedButton(
                                        onClick = { loadSourcePath(path) },
                                        modifier = Modifier.fillMaxWidth(0.6f),
                                    ) {
                                        Text(
                                            path,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        vm.state.message?.let { msg ->
                            if (msg.severity == MessageSeverity.ERROR) {
                                Text(
                                    msg.text,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Main editor layout: [Source bar] + [Sidebar | Preview | Metadata Panel]
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // ── Source path bar ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourcePathField(
                        value = vm.state.sourcePath,
                        onValueChange = { loadSourcePath(it) },
                        onPickFile = onPickSourceFile,
                        onPickFolder = onPickSourceFolder,
                        modifier = Modifier.weight(1f),
                        label = "Source",
                        placeholder = "File or folder...",
                        isError = vm.state.message?.severity == MessageSeverity.ERROR,
                    )
                    if (vm.isMultiEditMode) {
                        OutlinedButton(
                            onClick = { vm.showBulkSelectionDialog = true },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Select…", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                    // ═══ Left sidebar: scrollable thumbnail strip ═══
                    MetadataEditorSidebar(
                        state = vm.state,
                        thumbnailCache = vm.thumbnailCache,
                        isMultiEditMode = vm.isMultiEditMode,
                        selectedIndices = vm.selectedIndices,
                        onSelect = { index -> vm.toggleSelection(index) },
                        onToggleMultiEdit = { vm.toggleMultiEditMode() },
                        onDeselectAll = { vm.deselectAll() },
                        onOpenFolder = { onPickSourceFolder() },
                        modifier = Modifier.fillMaxHeight(),
                    )

                    // ═══ Center: image preview ═══
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (vm.isLoadingImage) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (vm.currentImage != null && !vm.isMultiEditMode) {
                            val previewBitmap =
                                remember(vm.currentImage) {
                                    vm.currentImage?.toComposeImageBitmap()
                                }
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (previewBitmap != null) {
                                    val rotationDeg =
                                        vm.state.selectedConfig.rotationDegrees.toFloat()
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = "Selected image",
                                        modifier =
                                            Modifier.fillMaxSize().graphicsLayer {
                                                rotationZ = rotationDeg
                                            },
                                        contentScale = ContentScale.Fit,
                                    )

                                    val config = vm.state.selectedConfig

                                    // Back-of-photo controls
                                    if (config.hasBackImage()) {
                                        Surface(
                                            modifier =
                                                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Row(
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp,
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Default.Image,
                                                    "Back image assigned",
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (config.backImageMode == "combine")
                                                        "Back: Combined"
                                                    else "Back: Appended",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                OutlinedButton(
                                                    onClick = { vm.showBackImagePicker() },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text(
                                                        "Change",
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { vm.removeBackImage() },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text(
                                                        "Remove",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { vm.showBackImagePicker() },
                                            modifier =
                                                Modifier.align(Alignment.BottomEnd)
                                                    .padding(8.dp)
                                                    .height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Image,
                                                "Select back of photo",
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Add Back",
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            // Rotation controls
                            Surface(
                                tonalElevation = 1.dp,
                                shape = RoundedCornerShape(8.dp),
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "Rotate:",
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        // Auto-rotation button (only visible when auto-orient
                                        // setting is enabled)
                                        if (vm.currentSettings.autoOrientInMetadataEditor) {
                                            val isAutoAvailable =
                                                vm.orientationCorrection.isAvailable()
                                            val modelDownloaded = vm.isOrientationModelAvailable
                                            IconButton(
                                                onClick = {
                                                    if (!modelDownloaded && !isAutoAvailable) {
                                                        vm.requestModelDownload()
                                                    } else {
                                                        vm.detectOrientation(coroutineScope)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp),
                                                enabled =
                                                    (isAutoAvailable || !modelDownloaded) &&
                                                        !vm.isDetectingOrientation,
                                            ) {
                                                if (vm.isDetectingOrientation) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(14.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else if (!modelDownloaded && !isAutoAvailable) {
                                                    Icon(
                                                        Icons.Default.AutoFixHigh,
                                                        "Download orientation model",
                                                        Modifier.size(16.dp),
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Default.AutoFixHigh,
                                                        "Auto-detect rotation",
                                                        Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                vm.state.updateSelectedConfig {
                                                    it.cycleRotationCCW()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.RotateLeft,
                                                "CCW",
                                                Modifier.size(16.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                vm.state.updateSelectedConfig { it.rotate180() }
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                "180°",
                                                Modifier.size(16.dp),
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                vm.state.updateSelectedConfig {
                                                    it.cycleRotationCW()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.RotateRight,
                                                "CW",
                                                Modifier.size(16.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        RotationBadge(
                                            rotationDegrees =
                                                vm.state.selectedConfig.rotationDegrees
                                        )
                                    }
                                    if (
                                        !vm.isOrientationModelAvailable &&
                                            !vm.orientationCorrection.isAvailable() &&
                                            vm.currentSettings.autoOrientInMetadataEditor
                                    ) {
                                        TextButton(
                                            onClick = { vm.requestModelDownload() },
                                            contentPadding =
                                                PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        ) {
                                            Text(
                                                "Download orientation model to enable auto-rotate",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (vm.isMultiEditMode && vm.selectedIndices.isNotEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${vm.selectedIndices.size} photos selected",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Image,
                                        "No image",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Select an image",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // ═══ Right pane: metadata editor ═══
                    MetadataEditorPanel(
                        state = vm.state,
                        editState = vm.editState,
                        isMultiEditMode = vm.isMultiEditMode,
                        selectedIndices = vm.selectedIndices,
                        sourceExif = vm.sourceExif,
                        metadataHistory = settings.metadataHistory,
                        onSettingsChange = onSettingsChange,
                        currentSettings = currentSettings,
                        settingsPort = vm.settingsPort,
                        coroutineScope = coroutineScope,
                        dispatcherProvider = vm.dispatcherProvider,
                        onPickLocation = { indices -> vm.requestLocationPicker(indices) },
                        onApply = { vm.applyMultiEdit(onSettingsChange) },
                        onClear = { vm.clearEditFields() },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
