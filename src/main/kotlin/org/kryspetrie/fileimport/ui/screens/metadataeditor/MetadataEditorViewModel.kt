package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.image.BufferedImage
import org.kryspetrie.fileimport.application.OrientationCorrectionService
import org.kryspetrie.fileimport.application.metadata.MetadataEditService
import org.kryspetrie.fileimport.application.metadata.MetadataEditUndoService
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.domain.port.DispatcherProvider
import org.kryspetrie.fileimport.domain.port.FaceDetectionPort
import org.kryspetrie.fileimport.domain.port.FaceRegionTransformerPort
import org.kryspetrie.fileimport.domain.port.FileSystemPort
import org.kryspetrie.fileimport.domain.port.FolderThumbnailCachePort
import org.kryspetrie.fileimport.domain.port.GeocodingPort
import org.kryspetrie.fileimport.domain.port.ImageProcessingPort
import org.kryspetrie.fileimport.domain.port.ImageRepositoryPort
import org.kryspetrie.fileimport.domain.port.LocalePort
import org.kryspetrie.fileimport.domain.port.LocationSearchPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadPort
import org.kryspetrie.fileimport.domain.port.ModelDownloadState
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.ui.screens.shared.metadata.MetadataEditState
import org.kryspetrie.fileimport.ui.shared.face.BulkEditFaceRegionMutator
import org.kryspetrie.fileimport.ui.shared.face.FaceRegionMutator
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

internal val THUMBNAIL_SIZE = 80

/**
 * ViewModel for the metadata editor screen.
 *
 * Hoists all business logic, service orchestration, and UI state from the MetadataEditorScreen
 * composable. The composable becomes a thin rendering shell that observes this ViewModel's state
 * and delegates all actions to it.
 *
 * ## Responsibilities
 * - Loading source files/folders and caching thumbnails
 * - Saving metadata (single file, all modified, undo, redo)
 * - Orientation detection and correction
 * - Location picking integration
 * - Multi-edit state (selection, applying batch edits)
 * - Auto-clearing status messages
 */
class MetadataEditorViewModel(
    val dispatcherProvider: DispatcherProvider,
    val imageRepository: ImageRepositoryPort,
    val imageProcessing: ImageProcessingPort,
    val locationSearchService: LocationSearchPort,
    val geocodingPort: GeocodingPort,
    val settingsPort: SettingsPort,
    val editService: MetadataEditService,
    val undoService: MetadataEditUndoService,
    val faceRegionTransformer: FaceRegionTransformerPort,
    val fileSystemAdapter: FileSystemPort,
    val orientationCorrection: OrientationCorrectionService,
    val modelDownloadPort: ModelDownloadPort,
    val faceDetectionPort: FaceDetectionPort,
    val folderThumbnailCache: FolderThumbnailCachePort,
    private val localePort: LocalePort,
) {
    internal fun t(key: StringKey, vararg params: Pair<String, String>): String =
        localePort.t(key, *params)

    // ── Core state ──────────────────────────────────────────────

    /** Bulk edit state (file list, configs, selection, output mode, messages). */
    val state = BulkEditState()

    /** Metadata field edit state (single & multi-edit field values). */
    val editState = MetadataEditState()

    /**
     * Face region mutations for [org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlay].
     */
    val faceRegionMutator: FaceRegionMutator =
        BulkEditFaceRegionMutator(state) { index, config ->
            if (index == state.selectedIndex) {
                editState.subjects = config.subjects
            }
        }

    // ── Image loading ───────────────────────────────────────────

    /** Currently loaded full-resolution image for the selected file. */
    var currentImage by mutableStateOf<BufferedImage?>(null)
        internal set

    /** Whether an image is currently being loaded. */
    var isLoadingImage by mutableStateOf(false)
        internal set

    /** Source EXIF data for the current file. */
    var sourceExif by mutableStateOf<SourceExifSummary?>(null)
        internal set

    /** In-memory thumbnail cache keyed by file absolute path (lazy-loaded). */
    val thumbnailCache = java.util.concurrent.ConcurrentHashMap<String, BufferedImage>()

    /** Bumped when [thumbnailCache] changes so Compose recomposes thumbnail cards. */
    var thumbnailCacheRevision by mutableStateOf(0)
        internal set

    internal val thumbnailsInFlight =
        java.util.Collections.newSetFromMap(
            java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        )

    // ── Dialog state ─────────────────────────────────────────────

    /** Whether the face name entry popup is visible. */
    var showFaceNamePopup by mutableStateOf(false)
        internal set

    /** Pending face coordinates for the name entry popup: (index, normX, normY). */
    var pendingFaceCoords by mutableStateOf<Triple<Int, Double, Double>?>(null)

    /** Current face name input text. */
    var faceNameInput by mutableStateOf("")

    /** Selected face region type in the name entry popup. */
    var selectedRegionType by mutableStateOf(org.kryspetrie.fileimport.domain.model.RegionType.FACE)

    /** Selected face size in the name entry popup. */
    var selectedFaceSize by
        mutableStateOf(org.kryspetrie.fileimport.ui.wizard.state.FaceSize.DEFAULT)

    /** Whether the bulk selection dialog is visible. */
    var showBulkSelectionDialog by mutableStateOf(false)

    /** Whether the rotation preview overlay is visible. */
    var showRotationPreview by mutableStateOf(false)
        internal set

    /** Whether orientation detection is running (batch scan for all files). */
    var isDetectingOrientation by mutableStateOf(false)
        internal set

    /** Per-file orientation detection results, keyed by file absolute path. */
    var orientationResults by
        mutableStateOf<Map<String, OrientationCorrectionService.CorrectionResult>>(emptyMap())
        internal set

    /** Set of file paths that the user has unchecked (excluded from rotation). */
    var rotationExcludedPaths by mutableStateOf<Set<String>>(emptySet())
        internal set

    /** Index of the file currently shown in the large preview within the rotation overlay. */
    var rotationPreviewIndex by mutableStateOf(-1)
        internal set

    /** The full-resolution image loaded for the rotation preview overlay. */
    var rotationPreviewImage by mutableStateOf<BufferedImage?>(null)
        internal set

    /** Whether to show the model download prompt dialog. */
    var showModelDownloadDialog by mutableStateOf(false)

    /** Download progress state (null = not downloading). */
    var modelDownloadState by mutableStateOf<ModelDownloadState?>(null)

    /** Whether the orientation model is available (on disk). */
    val isOrientationModelAvailable: Boolean
        get() = modelDownloadPort.isModelDownloaded(ModelDownloadPort.ORIENTATION_MODEL_ID)

    /** Progress counter for orientation detection ("Analyzing photo x / y"). */
    var orientationDetectCurrent by mutableStateOf(0)
        internal set

    /** Total count for orientation detection progress. */
    var orientationDetectTotal by mutableStateOf(0)
        internal set

    /** Whether the location picker overlay is visible. */
    var showLocationPicker by mutableStateOf(false)
        internal set

    /** Indices targeted by the location picker. */
    var locationPickerTargetIndices by mutableStateOf(emptyList<Int>())

    // ── Multi-edit state ─────────────────────────────────────────

    /** Whether multi-edit mode is active. */
    var isMultiEditMode by mutableStateOf(false)

    /** Set of selected indices in multi-edit mode. */
    var selectedIndices by mutableStateOf<Set<Int>>(emptySet())

    // ── File browser navigation ───────────────────────────────────

    /** Folder path stack for list/icons views. */
    var browserFolderPathStack by mutableStateOf<List<String>>(emptyList())

    /** Folder row highlighted by arrow keys; open with Right or Enter. */
    var browserFocusedFolderPath by mutableStateOf<String?>(null)

    /** Whether inline face tagging is active on the preview pane. */
    var showFaceTagging by mutableStateOf(false)

    /** Whether the file browser drawer is visible on narrow layouts. */
    var browserDrawerOpen by mutableStateOf(true)

    /** Incremented to request focus on the keywords field. */
    var keywordsFocusTrigger by mutableStateOf(0)

    // ── Current settings ─────────────────────────────────────────

    /** Cached current settings, observed from settingsPort. */
    var currentSettings by mutableStateOf(AppSettings())

    // ── Actions ──────────────────────────────────────────────────

}
