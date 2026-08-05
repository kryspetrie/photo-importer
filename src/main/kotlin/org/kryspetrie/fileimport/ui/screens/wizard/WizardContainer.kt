package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.ui.components.LoadingIndicator
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.WizardStep

/**
 * Main container for the Photo Import Wizard. Manages the step-by-step workflow: Import → Overview
 * → Summary → Edit → Processing → Complete
 *
 * When [AppSettings.skipCropAndRotate] is true, the Summary (Crop & Rotate) step is skipped
 * entirely, going directly from Overview to Edit (starting in metadata mode).
 */
@Composable
fun WizardContainer(
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WizardContainerViewModel = koinInject(),
) {
    val state = remember { PhotoScanWizardState() }
    state.setLogger(vm.appLogger)
    val currentStep by state.navigation.currentStep.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingCurrentFile by remember { mutableStateOf("") }
    var failedExportCount by remember { mutableStateOf(0) }
    var exportResults by remember { mutableStateOf<List<ExportResult>>(emptyList()) }
    val settings by vm.settingsPort.observeSettings().collectAsState()
    var exportDestination by
        remember(settings.photoScanImportTabSettings.lastDestinationPath) {
            mutableStateOf(vm.initialExportDestination(settings))
        }
    LaunchedEffect(settings.photoScanImportTabSettings.lastDestinationPath) {
        val settingsDest = settings.photoScanImportTabSettings.lastDestinationPath
        if (settingsDest.isNotBlank() && settingsDest != exportDestination) {
            exportDestination = settingsDest
        }
    }
    val scope = rememberCoroutineScope()
    val sourceImage by state.image.collectAsState()
    LaunchedEffect(sourceImage) { vm.previewCache.clear() }

    LaunchedEffect(Unit) { vm.preloadModels() }

    Box(modifier = modifier.fillMaxSize()) {
        WizardStepContent(
            currentStep = currentStep,
            state = state,
            vm = vm,
            settings = settings,
            isLoading = { isLoading = it },
            onMessage = { loadingMessage = it },
            onError = { errorMessage = it },
            exportDestination = exportDestination,
            onExportDestinationChange = { newDest ->
                exportDestination = newDest
                scope.launch { vm.persistExportDestination(newDest) }
            },
            processingProgress = processingProgress,
            processingCurrentFile = processingCurrentFile,
            onProgress = { progress, file ->
                processingProgress = progress
                processingCurrentFile = file
                vm.appLogger.debug("Export progress: ${(progress * 100).toInt()}% - $file")
            },
            failedExportCount = failedExportCount,
            onFailedCountChange = { count -> failedExportCount = count },
            onExportResults = { results -> exportResults = results },
            exportResults = exportResults,
            onComplete = onComplete,
            onCancel = onCancel,
            scope = scope,
        )

        if (isLoading) {
            LoadingOverlay(message = loadingMessage)
        }

        ErrorBanner(
            errorMessage = errorMessage,
            onDismiss = {
                errorMessage = null
                if (state.image.value != null) {
                    state.goToOverview()
                } else {
                    state.resetToImportStep()
                }
            },
        )
    }
}

@Composable
private fun WizardStepContent(
    currentStep: WizardStep,
    state: PhotoScanWizardState,
    vm: WizardContainerViewModel,
    settings: AppSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    isLoading: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onError: (String) -> Unit,
    exportDestination: String,
    onExportDestinationChange: (String) -> Unit,
    processingProgress: Float,
    processingCurrentFile: String,
    onProgress: (Float, String) -> Unit,
    failedExportCount: Int,
    onFailedCountChange: (Int) -> Unit,
    onExportResults: (List<ExportResult>) -> Unit,
    exportResults: List<ExportResult>,
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
) {
    val s = strings()
    // Observe source list so Skip Photo appears/disappears with multi-file session state.
    val batchSourceFiles by state.batch.sourceFiles.collectAsState()
    val canOfferSkipPhoto = batchSourceFiles.size > 1
    val handleExportComplete: (List<ProcessedPhoto>) -> Unit = { processedPhotos ->
        onFailedCountChange(processedPhotos.count { it.isError })
        onExportResults(processedPhotos.map { it.toExportResult() })
        vm.appLogger.logOperationComplete(
            OperationType.EXPORT_COMPLETE,
            "Exported ${processedPhotos.size} ${if (processedPhotos.size == 1) "photo" else "photos"} to $exportDestination",
        )
        state.navigation.goToComplete()
    }

    when (currentStep) {
        WizardStep.IMPORT -> {
            PhotoScanImportScreen(
                state = state,
                settingsPort = vm.settingsPort,
                onSettingsChange = { newSettings -> scope.launch { vm.saveSettings(newSettings) } },
                onImageSelected = { file, batchFiles ->
                    startNewImport(
                        state,
                        file,
                        batchFiles,
                        vm.detectorService,
                        vm.appLogger,
                        vm.dispatcherProvider,
                        isLoading,
                        onMessage,
                        onError,
                        scope,
                    )
                },
                onCancel = onCancel,
            )
        }

        WizardStep.OVERVIEW -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                OverviewScreen(
                    state = state,
                    onBack = {
                        state.resetToImportStep()
                        onCancel()
                    },
                    onToSummary = {
                        if (settings.skipCropAndRotate) {
                            state.navigation.goToEdit()
                        } else {
                            state.navigation.goToSummary()
                        }
                    },
                    onSkipCurrentPhoto =
                        if (canOfferSkipPhoto) {
                            {
                                state.batch.markBatchIndexSkipped(
                                    state.batch.currentImageIndex.value
                                )
                                continueToNextBatchPhoto(
                                    state,
                                    vm.detectorService,
                                    vm.appLogger,
                                    vm.dispatcherProvider,
                                    isLoading,
                                    onMessage,
                                    onError,
                                    scope,
                                )
                            }
                        } else null,
                )
            } else {
                LoadingContent(message = s.t(StringKey.WIZARD_LOADING_IMAGE))
            }
        }

        WizardStep.REFINEMENT -> {
            // Refinement is now handled inline in Overview — redirect immediately
            LaunchedEffect(Unit) { state.goToOverview() }
            // Don't render content while redirecting; the step will change on next frame
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }

        WizardStep.SUMMARY -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                SummaryScreen(
                    state = state,
                    image = image,
                    perspectiveService = vm.perspectiveService,
                    previewCache = vm.previewCache,
                    onBack = { state.goToOverview() },
                    onExport = { state.navigation.goToEdit() },
                )
            } else {
                LoadingContent(message = s.t(StringKey.WIZARD_LOADING_IMAGE))
            }
        }

        WizardStep.EDIT -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                EditScreen(
                    state = state,
                    image = image,
                    perspectiveService = vm.perspectiveService,
                    previewCache = vm.previewCache,
                    metadataHistory = settings.metadataHistory,
                    onMetadataHistoryUpdate = { fieldKey, value ->
                        scope.launch { vm.addMetadataHistory(fieldKey, value) }
                    },
                    onMetadataHistoryRemove = { fieldKey, value ->
                        scope.launch { vm.removeMetadataHistory(fieldKey, value) }
                    },
                    onRecordMetadataSet = { set -> scope.launch { vm.recordMetadataSet(set) } },
                    onBack = {
                        if (settings.skipCropAndRotate) {
                            state.goToOverview()
                        } else {
                            state.navigation.goToSummary()
                        }
                    },
                    onExport = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
                            state.navigation.goToProcessing()
                            exportPhotos(
                                state = state,
                                image = image,
                                exportService = vm.exportService,
                                destinationPath = exportDestination,
                                appLogger = vm.appLogger,
                                dispatcherProvider = vm.dispatcherProvider,
                                isLoading = isLoading,
                                onMessage = onMessage,
                                onError = onError,
                                onProgress = onProgress,
                                onComplete = handleExportComplete,
                                orientationCorrection = vm.orientationCorrection,
                                imageProcessing = vm.imageProcessing,
                                localePort = vm.localePort,
                            )
                        }
                    },
                    onSkipCurrentPhoto =
                        if (canOfferSkipPhoto) {
                            {
                                // Mark current batch image as skipped and advance
                                state.batch.markBatchIndexSkipped(
                                    state.batch.currentImageIndex.value
                                )
                                continueToNextBatchPhoto(
                                    state,
                                    vm.detectorService,
                                    vm.appLogger,
                                    vm.dispatcherProvider,
                                    isLoading,
                                    onMessage,
                                    onError,
                                    scope,
                                )
                            }
                        } else null,
                    startWithMetadata = settings.skipCropAndRotate,
                    faceRegionTransformer = vm.faceRegionTransformer,
                )
            } else {
                LoadingContent(message = s.t(StringKey.WIZARD_LOADING_IMAGE))
            }
        }

        WizardStep.PROCESSING -> {
            ProcessingScreen(
                progress = processingProgress,
                currentFile = processingCurrentFile,
                totalPhotos = state.boxes.boxCount(),
                destination = exportDestination,
                onBack = { state.resetToImportStep() },
            )
        }

        WizardStep.COMPLETE -> {
            CompletionScreen(
                photoCount = state.boxes.boxCount(),
                exportDestination = exportDestination,
                isBatchMode = state.batch.isBatchMode,
                hasMoreBatchImages = state.batch.hasMoreNonSkippedBatchImages,
                currentBatchIndex = state.batch.currentImageIndex.value,
                batchTotal = state.batch.batchTotal,
                skippedCount = state.batch.skippedBatchIndices.value.size,
                failedCount = failedExportCount,
                exportResults = exportResults,
                onDone = {
                    state.resetToImportStep()
                    onCancel()
                },
                onImportFile = {
                    val path = pickImageFile(s.t(StringKey.META_DIALOG_SELECT_IMAGE))
                    if (path != null) {
                        startNewImport(
                            state,
                            File(path),
                            null,
                            vm.detectorService,
                            vm.appLogger,
                            vm.dispatcherProvider,
                            isLoading,
                            onMessage,
                            onError,
                            scope,
                        )
                    }
                },
                onImportFolder = {
                    val path = pickFolder("Select Source Folder")
                    if (path != null) {
                        val folder = File(path)
                        val images = collectImageFiles(folder)
                        if (images.isNotEmpty()) {
                            startNewImport(
                                state,
                                images.first(),
                                resolveImportBatchFiles(folder),
                                vm.detectorService,
                                vm.appLogger,
                                vm.dispatcherProvider,
                                isLoading,
                                onMessage,
                                onError,
                                scope,
                            )
                        }
                    }
                },
                nextBatchFile = state.batch.peekNextNonSkippedBatchFile(),
                onContinueToNextPhoto = {
                    continueToNextBatchPhoto(
                        state,
                        vm.detectorService,
                        vm.appLogger,
                        vm.dispatcherProvider,
                        isLoading,
                        onMessage,
                        onError,
                        scope,
                    )
                },
                onSkipNextPhoto =
                    if (canOfferSkipPhoto && state.batch.hasMoreNonSkippedBatchImages) {
                        { skipNextBatchPhoto(state) }
                    } else null,
                onCancelImport = { state.resetToImportStep() },
                onOpenFolder = { openExportFolder(exportDestination) },
            )
        }
    }
}

@Composable
private fun BoxScope.ErrorBanner(errorMessage: String?, onDismiss: () -> Unit) {
    val s = strings()
    errorMessage?.let { error ->
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        s.t(StringKey.WIZARD_DISMISS_RETRY),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedLoadingIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnimatedLoadingIndicator() {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedLoadingIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * In-progress processing screen. Shows a progress indicator and current file. Completion UI is on
 * the separate [CompletionScreen] at the COMPLETE wizard step.
 *
 * Canceling requires confirmation since it discards all export progress.
 */
@Composable
private fun ProcessingScreen(
    progress: Float,
    currentFile: String,
    totalPhotos: Int,
    destination: String,
    onBack: () -> Unit,
) {
    val s = strings()
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Derive current photo index from progress fraction
    val currentIndex =
        (progress * totalPhotos).toInt().coerceIn(1, totalPhotos).let {
            if (progress >= 1f) totalPhotos else it
        }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(s.t(StringKey.WIZARD_EXPORTING), style = MaterialTheme.typography.headlineSmall)

            if (totalPhotos > 0) {
                Text(
                    s.t(
                        StringKey.SCAN_PHOTO_LABEL,
                        "index" to "$currentIndex",
                        "total" to "$totalPhotos",
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            Text(
                if (currentFile.isNotEmpty())
                    s.t(StringKey.WIZARD_PROCESSING, "file" to currentFile)
                else s.t(StringKey.WIZARD_FINALIZING),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (destination.isNotBlank()) {
                Text(
                    "→ $destination",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { showCancelConfirm = true },
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text(s.cancel)
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(s.t(StringKey.WIZARD_CANCEL_EXPORT)) },
            text = { Text(s.t(StringKey.WIZARD_CANCEL_EXPORT_MESSAGE)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onBack()
                    }
                ) {
                    Text(
                        s.t(StringKey.WIZARD_CANCEL_EXPORT),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(s.t(StringKey.WIZARD_CONTINUE_EXPORT))
                }
            },
        )
    }
}
