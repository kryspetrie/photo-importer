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

import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditorFileViewMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.screens.wizard.edit.RotationSection
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.components.pickImageFiles
import org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.ui.i18n.strings

private const val MESSAGE_AUTO_CLEAR_MS = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataEditorScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: MetadataEditorViewModel = koinInject()
    val s = strings()
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
        pickImageFile(s.t(StringKey.META_DIALOG_SELECT_IMAGE))?.let { loadSourcePath(it) }
    }

    val onPickSourceFolder: () -> Unit = {
        pickFolder(s.t(StringKey.META_DIALOG_SELECT_FOLDER))?.let { loadSourcePath(it) }
    }

    val onPickOutputFolder: () -> Unit = {
        pickFolder(s.t(StringKey.META_DIALOG_SELECT_OUTPUT))?.let { vm.state.outputDirectory = it }
    }

    val fileViewMode = currentSettings.metadataEditorFileViewMode
    val useCompactPreview = fileViewMode.usesCompactPreview()

    val onPickEditorImages: () -> Unit = {
        pickImageFiles(
            s.t(StringKey.META_DIALOG_SELECT_IMAGES),
            s.t(StringKey.ACTION_IMAGE_FILES),
        ).let { paths ->
            vm.loadSelectedFiles(paths, coroutineScope, onSettingsChange)
        }
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

    // Rotation preview overlay (batch auto-rotation)
    if (vm.showRotationPreview) {
        RotationPreviewOverlay(
            files = vm.state.files,
            orientationResults = vm.orientationResults,
            excludedPaths = vm.rotationExcludedPaths,
            previewIndex = vm.rotationPreviewIndex,
            thumbnailCache = vm.thumbnailCache,
            currentImage = vm.rotationPreviewImage,
            onToggleExclusion = { vm.toggleRotationExclusion(it) },
            onSelectAll = { vm.selectAllForRotation() },
            onDeselectAll = { vm.deselectAllForRotation() },
            onSetPreviewIndex = { vm.updateRotationPreviewIndex(it, coroutineScope) },
            onApply = { vm.applyBatchRotationCorrection() },
            onDismiss = { vm.dismissRotationPreview() },
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
                        s.t(StringKey.META_BULK_TITLE),
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
                        Text(s.t(StringKey.META_OVERWRITE), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        RadioButton(
                            selected = vm.state.outputMode == OutputMode.SAVE_NEW,
                            onClick = { vm.state.outputMode = OutputMode.SAVE_NEW },
                            modifier = Modifier.size(24.dp),
                        )
                        Text(s.t(StringKey.META_SAVE_NEW), style = MaterialTheme.typography.labelSmall)
                    }
                    if (vm.state.outputMode == OutputMode.SAVE_NEW) {
                        FolderSelectionField(
                            value = vm.state.outputDirectory,
                            onValueChange = { vm.state.outputDirectory = it },
                            modifier = Modifier.width(220.dp).height(48.dp),
                            label = s.t(StringKey.META_OUTPUT_LABEL),
                            placeholder = s.t(StringKey.META_OUTPUT_PLACEHOLDER),
                            title = s.t(StringKey.META_DIALOG_SELECT_OUTPUT),
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
                        Text(s.t(StringKey.ACTION_PREV), style = MaterialTheme.typography.labelSmall)
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
                            if (vm.state.fileCount == 0) s.t(StringKey.META_STATUS_NO_FILES)
                            else if (vm.state.modifiedCount > 0)
                                s.t(
                                    StringKey.META_STATUS_PROGRESS_UNSAVED,
                                    "index" to (vm.state.selectedIndex + 1).toString(),
                                    "total" to vm.state.fileCount.toString(),
                                    "modified" to vm.state.modifiedCount.toString(),
                                )
                            else
                                s.t(
                                    StringKey.META_STATUS_PROGRESS,
                                    "index" to (vm.state.selectedIndex + 1).toString(),
                                    "total" to vm.state.fileCount.toString(),
                                ),
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
                                    s.t(StringKey.META_UNDO),
                                    Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(s.t(StringKey.META_UNDO), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (vm.state.canRedo) {
                            OutlinedButton(
                                onClick = { vm.redoLast(coroutineScope) },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    s.t(StringKey.META_REDO),
                                    Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(s.t(StringKey.META_REDO), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (vm.state.modifiedCount > 1) {
                            Button(
                                onClick = { vm.saveAllModified(coroutineScope) },
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Default.Save, s.t(StringKey.ACC_SAVE_ALL), Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    s.t(StringKey.META_SAVE_ALL, "count" to vm.state.modifiedCount.toString()),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Button(
                            onClick = { vm.saveCurrentFile(coroutineScope) },
                            enabled = vm.state.selectedFile != null,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Default.Save, s.t(StringKey.META_SAVE_BUTTON), Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(s.t(StringKey.META_SAVE_BUTTON), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { vm.state.nextFile() },
                            enabled = vm.state.selectedIndex < vm.state.fileCount - 1,
                            modifier = Modifier.height(32.dp),
                        ) {
                            Text(s.t(StringKey.ACTION_NEXT), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        if (!vm.state.editingActive) {
            // ── Landing page: folder selection ──
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
                        Text(s.t(StringKey.META_LOADING_FILES), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Icon(
                            Icons.Default.FolderOpen,
                            s.t(StringKey.ACC_OPEN_SOURCE),
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            s.t(StringKey.META_BULK_TITLE),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            s.t(StringKey.META_LANDING_DESCRIPTION),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.width(440.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourcePathField(
                                value = vm.state.sourcePath,
                                onValueChange = { loadSourcePath(it) },
                                onPickFile = onPickSourceFile,
                                onPickFolder = onPickSourceFolder,
                                modifier = Modifier.fillMaxWidth(),
                                label = s.t(StringKey.META_SOURCE_LABEL),
                                placeholder = s.t(StringKey.META_SOURCE_PLACEHOLDER),
                                isError = vm.state.message?.severity == MessageSeverity.ERROR,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = vm.state.includeSubfolders,
                                    onCheckedChange = { vm.state.includeSubfolders = it },
                                )
                                Text(
                                    s.t(StringKey.META_INCLUDE_SUBFOLDERS),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (vm.state.sourcePath.isNotBlank()) {
                                Button(
                                    onClick = { loadSourcePath(vm.state.sourcePath) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.FolderOpen, s.t(StringKey.META_OPEN), Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(s.t(StringKey.META_OPEN), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            OutlinedButton(
                                onClick = onPickEditorImages,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.Image, s.t(StringKey.ACC_SELECT_IMAGES), Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.t(StringKey.META_SELECT_IMAGES), style = MaterialTheme.typography.bodyMedium)
                            }
                            val recentPaths = currentSettings.metadataEditorRecentPaths
                            if (recentPaths.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.width(200.dp))
                                Text(s.t(StringKey.META_RECENT), style = MaterialTheme.typography.labelMedium)
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
            // Main editor layout: [Toolbar] + [Sidebar | Preview | Metadata Panel]
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // ── Editor toolbar ──
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.goBackToLanding() },
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, s.t(StringKey.ACTION_BACK), Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.t(StringKey.ACTION_BACK), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        vm.state.sourcePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(1f))

                    if (vm.isMultiEditMode) {
                        OutlinedButton(
                            onClick = { vm.showBulkSelectionDialog = true },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(s.t(StringKey.META_SELECT_ELLIPSIS), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (vm.state.fileCount > 0 && !vm.isDetectingOrientation) {
                        OutlinedButton(
                            onClick = { vm.startBatchOrientationDetection(coroutineScope) },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                s.t(StringKey.ACC_AUTO_ROTATE),
                                Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(s.t(StringKey.META_AUTO_ROTATE_ELLIPSIS), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (vm.isDetectingOrientation) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                s.t(
                                    StringKey.META_ANALYZING_PROGRESS,
                                    "current" to vm.orientationDetectCurrent.toString(),
                                    "total" to vm.orientationDetectTotal.toString(),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                    MetadataEditorFileBrowserPanel(
                        state = vm.state,
                        viewMode = fileViewMode,
                        onViewModeChange = { mode -> vm.setFileViewMode(mode, onSettingsChange) },
                        thumbnailCache = vm.thumbnailCache,
                        isMultiEditMode = vm.isMultiEditMode,
                        selectedIndices = vm.selectedIndices,
                        folderPathStack = vm.browserFolderPathStack,
                        focusedFolderPath = vm.browserFocusedFolderPath,
                        onSelectFiles = onPickEditorImages,
                        onSelectFolder = onPickSourceFolder,
                        onSelectIndex = { index ->
                            if (vm.isMultiEditMode) {
                                vm.toggleSelection(index)
                            } else {
                                vm.selectBrowserFile(index)
                            }
                        },
                        onToggleMultiEdit = { vm.toggleMultiEditMode() },
                        onDeselectAll = { vm.deselectAll() },
                        onOpenFolder = onPickSourceFolder,
                        onNavigateUp = { vm.navigateBrowserUp() },
                        onEnterFolderPath = { path -> vm.navigateBrowserInto(path) },
                        onBrowserKey = { key -> vm.handleBrowserKey(key, fileViewMode) },
                        modifier = Modifier.fillMaxHeight(),
                    )

                    // ═══ Center: image preview ═══
                    val previewModifier =
                        if (useCompactPreview) {
                            Modifier.weight(0.9f).fillMaxHeight()
                        } else {
                            Modifier.weight(1f).fillMaxHeight()
                        }
                    Column(modifier = previewModifier) {
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
                                    Modifier
                                        .then(
                                            if (useCompactPreview) {
                                                Modifier.height(280.dp).fillMaxWidth()
                                            } else {
                                                Modifier.weight(1f).fillMaxWidth()
                                            }
                                        )
                                        .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (previewBitmap != null) {
                                    val rotationDeg =
                                        vm.state.selectedConfig.rotationDegrees.toFloat()
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = s.t(StringKey.ACC_SELECTED_IMAGE),
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
                                                    onClick = { vm.showBackImagePicker() },
                                                    contentPadding = PaddingValues(0.dp),
                                                ) {
                                                    Text(
                                                        s.t(StringKey.META_CHANGE),
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { vm.removeBackImage() },
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
                                                s.t(StringKey.META_SELECT_BACK_OF_PHOTO),
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                s.t(StringKey.META_ADD_BACK),
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                    }
                                }
                            }

                            // Rotation controls (shared component)
                            RotationSection(
                                rotationDegrees = vm.state.selectedConfig.rotationDegrees,
                                onRotateCW = { vm.state.updateSelectedConfig { it.cycleRotationCW() } },
                                onRotateCCW = { vm.state.updateSelectedConfig { it.cycleRotationCCW() } },
                                onRotate180 = { vm.state.updateSelectedConfig { it.rotate180() } },
                            )
                    } else if (vm.isMultiEditMode && vm.selectedIndices.isNotEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    s.t(
                                        StringKey.META_PHOTOS_SELECTED,
                                        "count" to vm.selectedIndices.size.toString(),
                                    ),
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
                                        s.t(StringKey.META_NO_IMAGE),
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        s.t(StringKey.META_SELECT_AN_IMAGE),
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
                        modifier =
                            Modifier
                                .weight(if (useCompactPreview) 1.1f else 1f)
                                .fillMaxHeight(),
                    )
                }
            }
        }
    }
}
