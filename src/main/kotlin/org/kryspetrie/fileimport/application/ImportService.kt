package org.kryspetrie.fileimport.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.kryspetrie.fileimport.domain.model.CameraDevice
import org.kryspetrie.fileimport.domain.model.DeduplicationSettings
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.FolderIndex
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageFileType
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.ImportProgress
import org.kryspetrie.fileimport.domain.model.ImportResult
import org.kryspetrie.fileimport.domain.model.IndexProgress
import org.kryspetrie.fileimport.domain.model.RawJpegPairMode
import org.kryspetrie.fileimport.domain.port.DeduplicationPort
import org.kryspetrie.fileimport.domain.port.DevicePort
import org.kryspetrie.fileimport.domain.port.FileStructurePreview
import org.kryspetrie.fileimport.domain.port.NamingPort

/**
 * Main application service orchestrating the photo/video import workflow.
 *
 * This service coordinates the entire import process by delegating to focused services:
 * - [ImportScanner]: File discovery, metadata extraction, hash caching
 * - [ImportExecutor]: File copying, verification, deletion
 * - [DeduplicationPort]: Duplicate detection
 * - [NamingPort]: Destination path generation
 * - [DevicePort]: Camera detection
 *
 * ## Import Workflow
 *
 * ```
 * 1. scanSource()              → ImportScanner discovers files + metadata
 * 2. filterAlreadyTransferred() → Remove already-imported files
 * 3. findVisualDuplicates()     → DeduplicationPort detects duplicates
 * 4. previewStructure()         → NamingPort generates destination paths
 * 5. executeImport()            → ImportExecutor copies files with verification
 * 6. Update import history      → Record import results
 * ```
 *
 * @property importScanner Handles scanning and hash resolution
 * @property importExecutor Handles file copy/verify/delete operations
 * @property deduplicationPort Duplicate detection
 * @property namingPort Folder/filename pattern resolution
 * @property devicePort Camera detection (optional)
 */
class ImportService(
    private val importScanner: ImportScanner,
    private val importExecutor: ImportExecutor,
    private val deduplicationPort: DeduplicationPort,
    private val namingPort: NamingPort,
    private val devicePort: DevicePort? = null,
) {
    /**
     * Mutex preventing concurrent imports from corrupting [importProgress].
     * Without this, two overlapping `executeImport` calls would both write to the same
     * [MutableStateFlow], causing one import's progress to overwrite the other's.
     */
    private val importMutex = Mutex()

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress

    /**
     * Scans a source directory for media files with metadata extraction. Delegates to
     * [ImportScanner.scanSource].
     */
    suspend fun scanSource(
        sourcePath: String,
        recursive: Boolean = true,
        onProgress: (scanned: Int, total: Int, file: String) -> Unit = { _, _, _ -> },
    ): List<ImageFile> = importScanner.scanSource(sourcePath, recursive, onProgress)

    /** Indexes a folder for fast duplicate detection. Delegates to [ImportScanner]. */
    suspend fun indexFolder(
        folderPath: String,
        recursive: Boolean = true,
        onProgress: (IndexProgress) -> Unit = {},
    ): FolderIndex = importScanner.indexFolder(folderPath, recursive, onProgress)

    /** Gets set of all file hashes in a destination folder. Delegates to [ImportScanner]. */
    fun getDestinationHashes(folderPath: String): Set<String> =
        importScanner.getDestinationHashes(folderPath)

    /** Clears the hash index for a specific folder. */
    suspend fun clearIndex(folderPath: String) = importScanner.clearIndex(folderPath)

    /** Clears all hash indexes. */
    suspend fun clearAllIndexes() = importScanner.clearAllIndexes()

    /** Filters out already-transferred files from a list. */
    fun filterAlreadyTransferred(
        images: List<ImageFile>,
        destinationHashes: Set<String>,
        configuration: ImportConfiguration,
    ): List<ImageFile> {
        if (!configuration.detectTransferredByHash && !configuration.detectTransferredByExif)
            return images
        return images.filter { image ->
            val hashMatch =
                configuration.detectTransferredByHash &&
                    image.hash != null &&
                    image.hash in destinationHashes
            !hashMatch
        }
    }

    /** Finds visual duplicates in a list of images. Delegates to [DeduplicationPort]. */
    suspend fun findVisualDuplicates(
        images: List<ImageFile>,
        configuration: ImportConfiguration,
    ): List<DuplicateInfo> {
        if (!configuration.detectVisualDuplicates) return emptyList()
        val settings =
            DeduplicationSettings(
                enableHashDeduplication = false,
                enablePerceptualHash = true,
                enableExifDeduplication = false,
                enableSurfMatching = configuration.useSurfMatching,
                surfMatchThreshold = configuration.surfMatchThreshold,
                perceptualHashThreshold = configuration.perceptualHashThreshold,
            )
        return deduplicationPort.findDuplicates(images, settings)
    }

    /** Scans for connected camera devices. */
    suspend fun scanDevices(): List<CameraDevice> = devicePort?.detectDevices() ?: emptyList()

    /** Previews the destination file structure. Delegates to [NamingPort]. */
    fun previewStructure(
        images: List<ImageFile>,
        destinationPath: String,
        configuration: ImportConfiguration,
    ): List<FileStructurePreview> =
        namingPort.previewFileStructure(images, destinationPath, configuration)

    /** Executes the import operation. Delegates to [ImportExecutor.executeImport].
     *
     * Protected by [importMutex] to prevent concurrent imports from corrupting
     * [importProgress] — two overlapping imports would both write to the same
     * [MutableStateFlow], causing one import's progress to overwrite the other's.
     */
    suspend fun executeImport(
        images: List<ImageFile>,
        destinationPath: String,
        configuration: ImportConfiguration,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult = importMutex.withLock {
        importExecutor.executeImport(
            images,
            destinationPath,
            configuration,
            _importProgress,
            onProgress,
        )
    }

    /** Detect RAW+JPEG pairs by matching base filename and timestamp. */
    fun detectRawJpegPairs(images: List<ImageFile>): List<Pair<ImageFile, ImageFile>> {
        val raws = images.filter { it.fileType.isRawFormat }
        val jpegs = images.filter { it.fileType.isJpeg || it.fileType == ImageFileType.JPEG }
        val pairs = mutableListOf<Pair<ImageFile, ImageFile>>()
        val pairedIds = mutableSetOf<String>()

        for (raw in raws) {
            val match =
                jpegs.find { jpeg ->
                    jpeg.id !in pairedIds &&
                        jpeg.path.nameWithoutExtension.equals(
                            raw.path.nameWithoutExtension,
                            ignoreCase = true,
                        )
                }
            if (match != null) {
                pairs.add(raw to match)
                pairedIds.add(raw.id)
                pairedIds.add(match.id)
            }
        }
        return pairs
    }

    /** Apply RAW+JPEG pair filtering based on config. */
    fun applyPairFilter(images: List<ImageFile>, config: ImportConfiguration): List<ImageFile> {
        if (config.rawJpegPairMode == RawJpegPairMode.IMPORT_BOTH) return images

        val pairs = detectRawJpegPairs(images)
        if (pairs.isEmpty()) return images

        val excludeIds = mutableSetOf<String>()
        for ((raw, jpeg) in pairs) {
            when (config.rawJpegPairMode) {
                RawJpegPairMode.RAW_ONLY -> excludeIds.add(jpeg.id)
                RawJpegPairMode.JPEG_ONLY -> excludeIds.add(raw.id)
                RawJpegPairMode.IMPORT_BOTH -> {}
            }
        }
        return images.filter { it.id !in excludeIds }
    }
}
