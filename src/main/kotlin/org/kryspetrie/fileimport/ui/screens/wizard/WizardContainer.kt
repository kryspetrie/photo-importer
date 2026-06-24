package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.application.FaceRegionTransformer
import org.kryspetrie.fileimport.application.PerspectiveCorrectionService
import org.kryspetrie.fileimport.application.PhotoScanExportService
import org.kryspetrie.fileimport.domain.model.AppSettings

import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.PhotoScanDetectorPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.adapter.AppPaths

import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardStep
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile

/**
 * Main container for the Photo Import Wizard. Manages the step-by-step workflow: Import → Overview
 * → Summary → Processing → Complete
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardContainer(
    onComplete: (List<ProcessedPhoto>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detectorService: PhotoScanDetectorPort = koinInject(),
    exportService: PhotoScanExportService = koinInject(),
    perspectiveService: PerspectiveCorrectionService = koinInject(),
    appLogger: AppLogger = koinInject(),
    settingsPort: SettingsPort = koinInject(),
    dispatcherProvider: DispatcherProvider = koinInject(),
    faceRegionTransformer: FaceRegionTransformer = koinInject(),
) {
    val state = remember { PhotoScanWizardState() }
    state.setLogger(appLogger)
    val currentStep by state.navigation.currentStep.collectAsState()
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingCurrentFile by remember { mutableStateOf("") }
    var failedExportCount by remember { mutableStateOf(0) }
    var exportResults by remember { mutableStateOf<List<ExportResult>>(emptyList()) }
    val settings by settingsPort.observeSettings().collectAsState()
    var exportDestination by remember {
        mutableStateOf(
            settings.photoScanImportTabSettings.lastDestinationPath.ifBlank {
                AppPaths.defaultDestination.absolutePath
            }
        )
    }
    // Sync exportDestination when settings change (e.g. user changed destination on import screen)
    LaunchedEffect(settings.photoScanImportTabSettings.lastDestinationPath) {
        val settingsDest = settings.photoScanImportTabSettings.lastDestinationPath
        if (settingsDest.isNotBlank() && settingsDest != exportDestination) {
            exportDestination = settingsDest
        }
    }
    val scope = rememberCoroutineScope()
    val previewCache = remember { PreviewCache(perspectiveService) }

    // Clear preview cache when the source image changes (new import)
    val sourceImage by state.image.collectAsState()
    LaunchedEffect(sourceImage) {
        previewCache.clear()
    }

    // Initialize correction strategy from persisted settings (once)
    LaunchedEffect(Unit) { state.setDefaultCorrectionStrategy(settings.lastCorrectionStrategy) }

    Box(modifier = modifier.fillMaxSize()) {
        WizardStepContent(
            currentStep = currentStep,
            state = state,
            settingsPort = settingsPort,
            settings = settings,
            detectorService = detectorService,
            exportService = exportService,
            perspectiveService = perspectiveService,
            previewCache = previewCache,
            appLogger = appLogger,
            dispatcherProvider = dispatcherProvider,
            faceRegionTransformer = faceRegionTransformer,
            scope = scope,
            isLoading = { isLoading = it },
            onMessage = { loadingMessage = it },
            onError = { errorMessage = it },
            exportDestination = exportDestination,
            onExportDestinationChange = { newDest ->
                exportDestination = newDest
                // Persist to settings so the import screen stays in sync
                scope.launch {
                    val currentSettings = settingsPort.observeSettings().first()
                    val updated =
                        currentSettings.withPhotoScanImportTabSettings(
                            currentSettings.photoScanImportTabSettings.withRecentDestinationPath(
                                newDest
                            )
                        )
                    settingsPort.saveSettings(updated)
                }
            },
            processingProgress = processingProgress,
            processingCurrentFile = processingCurrentFile,
            onProgress = { progress, file ->
                processingProgress = progress
                processingCurrentFile = file
                appLogger.debug("Export progress: ${(progress * 100).toInt()}% - $file")
            },
            failedExportCount = failedExportCount,
            onFailedCountChange = { count -> failedExportCount = count },
            onExportResults = { results -> exportResults = results },
            exportResults = exportResults,
            onComplete = onComplete,
            onCancel = onCancel,
        )

        if (isLoading) {
            LoadingOverlay(message = loadingMessage)
        }

        ErrorSnackbar(
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
    settingsPort: SettingsPort,
    settings: AppSettings,
    detectorService: PhotoScanDetectorPort,
    exportService: PhotoScanExportService,
    perspectiveService: PerspectiveCorrectionService,
    previewCache: PreviewCache,
    appLogger: AppLogger,
    dispatcherProvider: DispatcherProvider,
    faceRegionTransformer: FaceRegionTransformer,
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
    // Shared completion handler for all export flows
    val handleExportComplete: (List<ProcessedPhoto>) -> Unit = { processedPhotos ->
        onFailedCountChange(processedPhotos.count { it.isError })
        onExportResults(processedPhotos.map { it.toExportResult() })
        appLogger.logOperationComplete(
            OperationType.EXPORT_COMPLETE,
            "Exported ${processedPhotos.size} ${if (processedPhotos.size == 1) "photo" else "photos"} to $exportDestination",
        )
        state.navigation.goToComplete()
    }

    when (currentStep) {
        WizardStep.IMPORT -> {
            PhotoScanImportScreen(
                state = state,
                settingsPort = settingsPort,
                onSettingsChange = { newSettings ->
                    scope.launch { settingsPort.saveSettings(newSettings) }
                },
                onImageSelected = { file, batchFiles ->
                    startNewImport(
                        state,
                        file,
                        batchFiles,
                        detectorService,
                        appLogger,
                        dispatcherProvider,
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
                    onToSummary = { state.navigation.goToSummary() },
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        WizardStep.REFINEMENT -> {
            // Refinement is now handled inline in Overview — redirect immediately
            LaunchedEffect(Unit) { state.goToOverview() }
            // Don't render content while redirecting; the step will change on next frame
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        WizardStep.SUMMARY -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                SummaryScreen(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    onBack = { state.goToOverview() },
                    onExport = { state.navigation.goToEdit() },
                    onSkipMetadata = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
                            state.navigation.goToProcessing()
                            exportPhotos(
                                state = state,
                                image = image,
                                exportService = exportService,
                                destinationPath = exportDestination,
                                appLogger = appLogger,
                                dispatcherProvider = dispatcherProvider,
                                isLoading = isLoading,
                                onMessage = onMessage,
                                onError = onError,
                                onProgress = onProgress,
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                )
            } else {
                LoadingContent(message = "Loading image...")
            }
        }

        WizardStep.EDIT -> {
            val image = state.image.collectAsState().value
            if (image != null) {
                EditScreen(
                    state = state,
                    image = image,
                    perspectiveService = perspectiveService,
                    previewCache = previewCache,
                    metadataHistory = settings.metadataHistory,
                    onMetadataHistoryUpdate = { fieldKey, value ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.addMetadataHistory(fieldKey, value)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onMetadataHistoryRemove = { fieldKey, value ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.removeMetadataHistory(fieldKey, value)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onRecordMetadataSet = { set ->
                        scope.launch {
                            val currentSettings = settingsPort.observeSettings().first()
                            val updated = currentSettings.addMetadataSet(set)
                            settingsPort.saveSettings(updated)
                        }
                    },
                    onBack = { state.navigation.goToSummary() },
                    onExport = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
                            state.navigation.goToProcessing()
                            exportPhotos(
                                state = state,
                                image = image,
                                exportService = exportService,
                                destinationPath = exportDestination,
                                appLogger = appLogger,
                                dispatcherProvider = dispatcherProvider,
                                isLoading = isLoading,
                                onMessage = onMessage,
                                onError = onError,
                                onProgress = onProgress,
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                    onSkipToExport = {
                        scope.launch {
                            onFailedCountChange(0)
                            onExportResults(emptyList())
                            state.navigation.goToProcessing()
                            exportPhotos(
                                state = state,
                                image = image,
                                exportService = exportService,
                                destinationPath = exportDestination,
                                appLogger = appLogger,
                                dispatcherProvider = dispatcherProvider,
                                isLoading = isLoading,
                                onMessage = onMessage,
                                onError = onError,
                                onProgress = onProgress,
                                onComplete = handleExportComplete,
                            )
                        }
                    },
                    startWithMetadata = settings.alwaysEditMetadata,
                    faceRegionTransformer = faceRegionTransformer,
                )
            } else {
                LoadingContent(message = "Loading image...")
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
                hasMoreBatchImages = state.batch.hasMoreBatchImages,
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
                    val path = pickImageFile("Select Image File")
                    if (path != null) {
                        startNewImport(
                            state,
                            File(path),
                            null,
                            detectorService,
                            appLogger,
                            dispatcherProvider,
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
                        val batchFiles = collectImageFiles(folder)
                        if (batchFiles.isNotEmpty()) {
                            val batch = if (batchFiles.size > 1) batchFiles else null
                            startNewImport(
                                state,
                                batchFiles.first(),
                                batch,
                                detectorService,
                                appLogger,
                                dispatcherProvider,
                                isLoading,
                                onMessage,
                                onError,
                                scope,
                            )
                        }
                    }
                },
                nextBatchFile = state.batch.peekNextBatchFile(),
                onContinueToNextPhoto = {
                    continueToNextBatchPhoto(
                        state,
                        detectorService,
                        appLogger,
                        dispatcherProvider,
                        isLoading,
                        onMessage,
                        onError,
                        scope,
                    )
                },
                onSkipNextPhoto =
                    if (state.batch.hasMoreBatchImages) {
                        { skipNextBatchPhoto(state) }
                    } else null,
                onCancelImport = { state.resetToImportStep() },
                onOpenFolder = { openExportFolder(exportDestination) },
            )
        }
    }
}

@Composable
private fun BoxScope.ErrorSnackbar(errorMessage: String?, onDismiss: () -> Unit) {
    errorMessage?.let { error ->
        Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            action = { TextButton(onClick = onDismiss) { Text("Dismiss & Retry") } },
        ) {
            Text(error)
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
            // Animated loading indicator
            AnimatedLoadingIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnimatedLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "rotation",
        )

    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 4.dp)
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
    var showCancelConfirm by remember { mutableStateOf(false) }

    // Derive current photo index from progress fraction
    val currentIndex = (progress * totalPhotos).toInt().coerceIn(1, totalPhotos).let {
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

            Text("Exporting", style = MaterialTheme.typography.headlineSmall)

            if (totalPhotos > 0) {
                Text(
                    "Photo $currentIndex of $totalPhotos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            Text(
                if (currentFile.isNotEmpty()) "Processing: $currentFile" else "Finalizing...",
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
                Text("Cancel")
            }
        }
    }

    // Cancel confirmation dialog
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Export?") },
            text = {
                Text(
                    "Canceling will discard all progress and return to the import screen. " +
                        "Any photos already exported to disk will remain."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onBack()
                    }
                ) {
                    Text("Cancel Export", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Continue Export") }
            },
        )
    }
}

