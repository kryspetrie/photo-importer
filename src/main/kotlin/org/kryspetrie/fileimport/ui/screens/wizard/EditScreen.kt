package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.awt.image.BufferedImage
import org.koin.compose.koinInject
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.PerspectiveCorrectionPort
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.ui.components.PreviewCache
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditBackImagePickerHost
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditCenterPreview
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditFaceFlowCallbacks
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditFaceFlowHost
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditFaceFlowUiState
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditFullscreenPreviewHost
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditLocationPickerHost
import org.kryspetrie.fileimport.ui.screens.wizard.edit.EditScreenChrome
import org.kryspetrie.fileimport.ui.screens.wizard.edit.PhotoSidebar
import org.kryspetrie.fileimport.ui.screens.wizard.edit.WizardEditMetadataPanel
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.LoadSourceExifEffect
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

/**
 * Edit screen with vertical thumbnail sidebar on the left, large preview in the center, and
 * metadata panel on the right. Rotation controls are inline in the metadata panel.
 *
 * Layout: [Thumbnail sidebar | Preview | Metadata panel]
 */
@Composable
fun EditScreen(
    state: PhotoScanWizardState,
    image: BufferedImage,
    perspectiveService: PerspectiveCorrectionPort,
    previewCache: PreviewCache,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onRecordMetadataSet: (RecentMetadataSet) -> Unit = {},
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipCurrentPhoto: (() -> Unit)? = null,
    startWithMetadata: Boolean = false,
    modifier: Modifier = Modifier,
    faceRegionTransformer: FaceRegionTransformerPort? = null,
) {
    val locationSearchService: LocationSearchPort = koinInject()
    val geocodingPort: GeocodingPort = koinInject()
    val dispatcherProvider: DispatcherProvider = koinInject()
    val imageRepository: ImageRepositoryPort = koinInject()
    val faceDetectionPort: FaceDetectionPort = koinInject()
    val appLogger: AppLogger = koinInject()
    val settingsPort: SettingsPort = koinInject()
    val settings by settingsPort.observeSettings().collectAsState(initial = AppSettings())
    val coroutineScope = rememberCoroutineScope()

    val boundingBoxList by state.boundingBoxList.collectAsState()
    val photoConfigurations by state.photoConfigurations.collectAsState()
    val selectedIndices by state.configs.selectedMetadataIndices.collectAsState()
    val sourceExif by state.sourceExif.collectAsState()
    val currentImageFile by state.imageFile.collectAsState()
    // Gate here too: file-by-filename sessions leave batch empty → no Skip Photo.
    val batchSourceFiles by state.batch.sourceFiles.collectAsState()
    val skipCurrentPhoto =
        if (batchSourceFiles.size > 1) onSkipCurrentPhoto else null

    var isMultiEditMode by remember { mutableStateOf(false) }
    var fullscreenPreviewIndex by remember { mutableStateOf<Int?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationPickerTargetIndices by remember { mutableStateOf(emptyList<Int>()) }

    var faceSelectIndex by remember { mutableStateOf<Int?>(null) }
    var pendingFaceCoords by remember { mutableStateOf<Triple<Int, Double, Double>?>(null) }
    var showFaceNamePopup by remember { mutableStateOf(false) }
    var faceNameInput by remember { mutableStateOf("") }
    var selectedRegionType by remember { mutableStateOf(RegionType.FACE) }
    var selectedFaceSize by remember { mutableStateOf(FaceSize.DEFAULT) }
    var inheritedFaceRegions by remember { mutableStateOf<List<FaceRegion>>(emptyList()) }
    var autoStartNaming by remember { mutableStateOf(false) }
    var nameSuggestions by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var showBackImagePicker by remember { mutableStateOf(false) }

    LoadSourceExifEffect(
        imageFile = currentImageFile,
        sourceExif = sourceExif,
        state = state,
        imageRepository = imageRepository,
        dispatcherProvider = dispatcherProvider,
    )

    LaunchedEffect(boundingBoxList.size()) {
        if (selectedIndices.isEmpty() && boundingBoxList.size() > 0) {
            state.configs.selectSingleMetadata(0)
        }
    }

    EditFullscreenPreviewHost(
        fullscreenPreviewIndex = fullscreenPreviewIndex,
        boundingBoxList = boundingBoxList,
        photoConfigurations = photoConfigurations,
        previewCache = previewCache,
        image = image,
        onDismiss = { fullscreenPreviewIndex = null },
    )
    EditFaceFlowHost(
        uiState =
            EditFaceFlowUiState(
                faceSelectIndex = faceSelectIndex,
                pendingFaceCoords = pendingFaceCoords,
                showFaceNamePopup = showFaceNamePopup,
                faceNameInput = faceNameInput,
                selectedRegionType = selectedRegionType,
                selectedFaceSize = selectedFaceSize,
                inheritedFaceRegions = inheritedFaceRegions,
                autoStartNaming = autoStartNaming,
                nameSuggestions = nameSuggestions,
            ),
        callbacks =
            EditFaceFlowCallbacks(
                onFaceSelectIndexChange = { faceSelectIndex = it },
                onPendingFaceCoordsChange = { pendingFaceCoords = it },
                onShowFaceNamePopupChange = { showFaceNamePopup = it },
                onFaceNameInputChange = { faceNameInput = it },
                onRegionTypeChange = { selectedRegionType = it },
                onFaceSizeChange = { selectedFaceSize = it },
                onInheritedFaceRegionsChange = { inheritedFaceRegions = it },
                onAutoStartNamingChange = { autoStartNaming = it },
            ),
        state = state,
        image = image,
        boundingBoxList = boundingBoxList,
        photoConfigurations = photoConfigurations,
        previewCache = previewCache,
        perspectiveService = perspectiveService,
        faceRegionTransformer = faceRegionTransformer,
        faceDetectionPort = faceDetectionPort,
        appLogger = appLogger,
    )
    EditBackImagePickerHost(
        showBackImagePicker = showBackImagePicker,
        state = state,
        currentImageFile = currentImageFile,
        selectedIndices = selectedIndices,
        boundingBoxList = boundingBoxList,
        settings = settings,
        onDismiss = { showBackImagePicker = false },
    )

    EditScreenChrome(
        photoCount = boundingBoxList.size(),
        selectedIndices = selectedIndices,
        onBack = onBack,
        onExport = onExport,
        onSkipCurrentPhoto = skipCurrentPhoto,
        onSelectPrevious = {
            val currentIdx = if (selectedIndices.size == 1) selectedIndices.first() else -1
            if (currentIdx > 0) state.configs.selectSingleMetadata(currentIdx - 1)
        },
        onSelectNext = {
            val currentIdx = if (selectedIndices.size == 1) selectedIndices.first() else -1
            if (currentIdx >= 0 && currentIdx < boundingBoxList.size() - 1) {
                state.configs.selectSingleMetadata(currentIdx + 1)
            }
        },
        modifier = modifier,
    ) { contentModifier ->
        Row(modifier = contentModifier.fillMaxSize()) {
            PhotoSidebar(
                image = image,
                perspectiveService = perspectiveService,
                previewCache = previewCache,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedIndices = selectedIndices,
                isMultiEditMode = isMultiEditMode,
                onToggleMultiEdit = { isMultiEditMode = !isMultiEditMode },
                onSelect = { index ->
                    if (isMultiEditMode) {
                        state.configs.toggleMetadataSelection(index)
                    } else if (index !in selectedIndices || selectedIndices.size != 1) {
                        state.configs.selectSingleMetadata(index)
                    }
                },
                onDeselectAll = { state.configs.deselectAllMetadata() },
            )
            EditCenterPreview(
                state = state,
                image = image,
                previewCache = previewCache,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedIndices = selectedIndices,
                isMultiEditMode = isMultiEditMode,
                onFullscreenPreview = { fullscreenPreviewIndex = it },
                onSelectFaces = { faceSelectIndex = it },
                onPickBackImage = { showBackImagePicker = true },
                modifier = Modifier.weight(1f),
            )
            WizardEditMetadataPanel(
                state = state,
                image = image,
                perspectiveService = perspectiveService,
                boundingBoxList = boundingBoxList,
                photoConfigurations = photoConfigurations,
                selectedIndices = selectedIndices,
                isMultiEditMode = isMultiEditMode,
                metadataHistory = metadataHistory,
                onMetadataHistoryUpdate = onMetadataHistoryUpdate,
                onMetadataHistoryRemove = onMetadataHistoryRemove,
                sourceExif = sourceExif,
                onSelectFaces = { faceSelectIndex = it },
                onPickLocation = { indices ->
                    locationPickerTargetIndices = indices
                    showLocationPicker = true
                },
                onRecordMetadataSet = onRecordMetadataSet,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }

    EditLocationPickerHost(
        showLocationPicker = showLocationPicker,
        targetIndices = locationPickerTargetIndices,
        state = state,
        boundingBoxList = boundingBoxList,
        settings = settings,
        locationSearchService = locationSearchService,
        geocodingPort = geocodingPort,
        dispatcherProvider = dispatcherProvider,
        settingsPort = settingsPort,
        coroutineScope = coroutineScope,
        onDismiss = {
            showLocationPicker = false
            locationPickerTargetIndices = emptyList()
        },
    )
}
