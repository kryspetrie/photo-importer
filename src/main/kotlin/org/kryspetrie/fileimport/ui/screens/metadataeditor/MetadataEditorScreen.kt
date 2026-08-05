package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.MetadataEditorSessionPreferences
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.CenteredContentPane
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ConfigWorkLayout
import org.kryspetrie.fileimport.ui.components.SessionPreferencesEffect
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.WorkPanelHeading
import org.kryspetrie.fileimport.ui.components.WorkPanelSectionSpacer
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.components.pickImageFiles
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlay
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialog
import org.kryspetrie.fileimport.ui.screens.wizard.edit.FaceNameEntryPanel
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerOverlay
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

private const val MESSAGE_AUTO_CLEAR_MS = 5000L

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

    vm.currentSettings = currentSettings

    val sessionPrefs =
        MetadataEditorSessionPreferences(
            outputMode = vm.state.outputMode.name,
            outputDirectory = vm.state.outputDirectory,
            includeSubfolders = vm.state.includeSubfolders,
        )
    SessionPreferencesEffect(
        stored = currentSettings.metadataEditorSessionPreferences,
        current = sessionPrefs,
        onRestore = { prefs ->
            vm.state.includeSubfolders = prefs.includeSubfolders
            vm.state.outputMode = OutputMode.valueOf(prefs.normalizedOutputMode())
            vm.state.outputDirectory = prefs.outputDirectory
        },
        onPersist = { prefs ->
            onSettingsChange(currentSettings.withMetadataEditorSessionPreferences(prefs))
        },
    )

    LaunchedEffect(Unit) { vm.observeSettings(coroutineScope) }

    // Preload ONNX models on IO dispatcher to avoid blocking UI during first use
    LaunchedEffect(Unit) {
        withContext(coroutineScope.coroutineContext + Dispatchers.IO) {
            try {
                vm.orientationCorrection.preload()
            } catch (_: Exception) {
                // Best-effort; orientation model is optional
            }
            try {
                vm.faceDetectionPort.preload()
            } catch (_: Exception) {
                // Face model is optional
            }
        }
    }

    LaunchedEffect(vm.state.message) {
        if (vm.state.message != null) {
            delay(MESSAGE_AUTO_CLEAR_MS)
            vm.state.clearMessage()
        }
    }

    LaunchedEffect(vm.state.selectedIndex, vm.state.files, vm.isMultiEditMode, vm.selectedIndices) {
        vm.loadSelectedImage()
    }

    LaunchedEffect(vm.state.files) { vm.onFilesLoaded(coroutineScope) }

    LaunchedEffect(vm.state.selectedIndex, vm.state.selectedConfig) {
        if (!vm.isMultiEditMode) {
            vm.editState.loadFrom(vm.state.selectedConfig)
        }
    }

    LaunchedEffect(vm.isMultiEditMode) {
        if (vm.isMultiEditMode) {
            vm.editState.clear()
        }
    }

    val loadSourcePath: (String) -> Unit = { path ->
        vm.loadSourceAsync(path, coroutineScope, onSettingsChange)
    }

    /** Landing: only set the path; user confirms with Edit Metadata. */
    val setSourcePath: (String) -> Unit = { path ->
        vm.state.sourcePath = path
        vm.state.clearMessage()
    }

    val onPickSourceFile: () -> Unit = {
        pickImageFile(s.t(StringKey.META_DIALOG_SELECT_IMAGE))?.let { setSourcePath(it) }
    }

    val onPickSourceFolder: () -> Unit = {
        pickFolder(s.t(StringKey.META_DIALOG_SELECT_FOLDER))?.let { setSourcePath(it) }
    }

    /** While editing, picking a new folder/files still loads into the editor immediately. */
    val onPickSourceFolderWhileEditing: () -> Unit = {
        pickFolder(s.t(StringKey.META_DIALOG_SELECT_FOLDER))?.let { loadSourcePath(it) }
    }

    val fileViewMode = currentSettings.metadataEditorFileViewMode
    val densityScale = LocalUiDensityScale.current

    val onPickEditorImages: () -> Unit = {
        pickImageFiles(s.t(StringKey.META_DIALOG_SELECT_IMAGES), s.t(StringKey.ACTION_IMAGE_FILES))
            .let { paths -> vm.loadSelectedFiles(paths, coroutineScope, onSettingsChange) }
    }

    // ── Dialogs ──

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

    if (vm.showBulkSelectionDialog && vm.isMultiEditMode) {
        BulkSelectionDialog(
            state = vm.state,
            thumbnailCache = vm.thumbnailCache,
            thumbnailCacheRevision = vm.thumbnailCacheRevision,
            onEnsureThumbnail = { file -> vm.ensureThumbnail(file) },
            selectedIndices = vm.selectedIndices,
            onToggleSelection = { index -> vm.toggleSelection(index) },
            onSelectAll = { vm.selectAll() },
            onSelectNone = { vm.deselectAll() },
            onConfirm = { vm.dismissBulkSelectionDialog() },
            onDismiss = { vm.dismissBulkSelectionDialog() },
        )
    }

    if (vm.showRotationPreview) {
        RotationPreviewOverlay(
            files = vm.state.files,
            orientationResults = vm.orientationResults,
            excludedPaths = vm.rotationExcludedPaths,
            previewIndex = vm.rotationPreviewIndex,
            thumbnailCache = vm.thumbnailCache,
            thumbnailCacheRevision = vm.thumbnailCacheRevision,
            onEnsureThumbnail = { file -> vm.ensureThumbnail(file) },
            currentImage = vm.rotationPreviewImage,
            onToggleExclusion = { vm.toggleRotationExclusion(it) },
            onSelectAll = { vm.selectAllForRotation() },
            onDeselectAll = { vm.deselectAllForRotation() },
            onSetPreviewIndex = { vm.updateRotationPreviewIndex(it, coroutineScope) },
            onApply = { vm.applyBatchRotationCorrection() },
            onDismiss = { vm.dismissRotationPreview() },
        )
    }

    if (
        vm.showFaceTagging &&
            vm.currentImage != null &&
            vm.state.selectedIndex >= 0 &&
            MetadataEditorPanelController.shouldShowPreview(
                vm.isMultiEditMode,
                vm.selectedIndices.size,
            )
    ) {
        FaceSelectorOverlay(
            fullPreview = vm.currentImage!!,
            idx = vm.state.selectedIndex,
            photoConfig = vm.state.selectedConfig,
            faceRegionMutator = vm.faceRegionMutator,
            selectedRegionType = vm.selectedRegionType,
            selectedFaceSize = vm.selectedFaceSize,
            onRegionTypeChange = { vm.selectedRegionType = it },
            onFaceSizeChange = { vm.selectedFaceSize = it },
            onPlaceFace = { _, _ -> },
            onDismiss = { vm.dismissFaceTagging() },
            inheritedFaceRegions = emptyList(),
            onAutoDetectFaces = vm.autoDetectFacesCallback(coroutineScope),
            onNameConfirmed = { _, _ -> vm.syncEditStateFromSelectedConfig() },
        )
    }

    if (vm.showModelDownloadDialog) {
        ModelDownloadDialog(
            downloadState = vm.modelDownloadState,
            onDownload = { vm.downloadOrientationModel(coroutineScope) },
            onCancel = { vm.cancelModelDownload() },
            onRetry = { vm.downloadOrientationModel(coroutineScope) },
        )
    }

    // ── Main layout ──

    Column(
        modifier =
            modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (
                        !vm.state.editingActive &&
                            keyEvent.key == Key.Enter &&
                            vm.state.sourcePath.isNotBlank() &&
                            !vm.state.isLoading
                    ) {
                        val path = vm.state.sourcePath.trim()
                        if (path.isNotEmpty()) {
                            loadSourcePath(path)
                            return@onPreviewKeyEvent true
                        }
                    }
                    val isMeta = isMetadataEditorMetaKey(keyEvent)
                    metadataEditorShortcutAction(keyEvent, isMeta)?.let { action ->
                        return@onPreviewKeyEvent vm.handleMetadataShortcut(
                            action,
                            onSettingsChange,
                            coroutineScope,
                        )
                    }
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
    ) {
        when {
            vm.state.editingActive ->
                MetadataEditorCommandBar(
                    state = vm.state,
                    vm = vm,
                    autoOrientEnabled = currentSettings.autoOrientInMetadataEditor,
                    onBack = { vm.goBackToLanding() },
                    onPrev = { vm.state.prevFile() },
                    onNext = { vm.state.nextFile() },
                    onSave = { vm.saveCurrentFile(coroutineScope) },
                    onSaveAll = { vm.saveAllModified(coroutineScope) },
                    onUndo = { vm.undoLast(coroutineScope) },
                    onRedo = { vm.redoLast(coroutineScope) },
                    onToggleBrowserDrawer = { vm.toggleBrowserDrawer(onSettingsChange) },
                    onAutoRotate = { vm.startBatchOrientationDetection(coroutineScope) },
                    onBulkSelect = { vm.showBulkSelectionDialog = true },
                )
            else ->
                // Single header matching other pages
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    CenteredContentPane(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            s.t(StringKey.META_BULK_TITLE),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            s.t(StringKey.META_LANDING_DESCRIPTION),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }

        ChunkyScrollbar(modifier = Modifier.weight(1f)) {
            if (!vm.state.editingActive) {
                ConfigWorkLayout(
                    modifier =
                        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                    contentSpacing = densityScale.spacingMd,
                    configuration = {
                        LandingSourceColumn(
                            vm = vm,
                            setSourcePath = setSourcePath,
                            onPickSourceFile = onPickSourceFile,
                            onPickSourceFolder = onPickSourceFolder,
                            recentPaths = currentSettings.metadataEditorRecentPaths,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    work = {
                        WorkPanelHeading(s.t(StringKey.TAB_ACTIONS))
                        val canEdit = vm.state.sourcePath.isNotBlank() && !vm.state.isLoading
                        Button(
                            onClick = {
                                val path = vm.state.sourcePath.trim()
                                if (path.isNotEmpty()) loadSourcePath(path)
                            },
                            enabled = canEdit,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .height(densityScale.controlMinHeight),
                        ) {
                            Text(
                                s.t(StringKey.META_EDIT_METADATA),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (vm.state.isLoading ||
                            (vm.state.message?.severity == MessageSeverity.ERROR)
                        ) {
                            WorkPanelSectionSpacer()
                            WorkPanelHeading(s.t(StringKey.TAB_RUN_STATUS))
                        }
                        if (vm.state.isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                s.t(StringKey.META_LOADING_FILES),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        vm.state.message?.let { msg ->
                            if (msg.severity == MessageSeverity.ERROR) {
                                Text(
                                    msg.text,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                )
            } else {
                MetadataEditorEditingLayout(
                    vm = vm,
                    fileViewMode = fileViewMode,
                    currentSettings = currentSettings,
                    onSettingsChange = onSettingsChange,
                    coroutineScope = coroutineScope,
                    onPickEditorImages = onPickEditorImages,
                    onPickSourceFolder = onPickSourceFolderWhileEditing,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LandingSourceColumn(
    vm: MetadataEditorViewModel,
    setSourcePath: (String) -> Unit,
    onPickSourceFile: () -> Unit,
    onPickSourceFolder: () -> Unit,
    recentPaths: List<String>,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val densityScale = LocalUiDensityScale.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(densityScale.spacingSm),
    ) {
        SourcePathField(
            value = vm.state.sourcePath,
            onValueChange = setSourcePath,
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
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = vm.state.includeSubfolders,
                onCheckedChange = { vm.state.includeSubfolders = it },
            )
            Text(
                s.t(StringKey.META_INCLUDE_SUBFOLDERS),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (recentPaths.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(s.t(StringKey.META_RECENT), style = MaterialTheme.typography.labelMedium)
            recentPaths.forEach { path ->
                OutlinedButton(
                    onClick = { setSourcePath(path) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(path, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}
