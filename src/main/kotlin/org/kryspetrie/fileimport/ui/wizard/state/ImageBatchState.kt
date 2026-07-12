package org.kryspetrie.fileimport.ui.wizard.state

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages batch/folder image processing state. Extracted from [PhotoScanWizardState] to separate
 * batch concerns from single-image and box manipulation state.
 *
 * Note: [PhotoScanWizardState.switchToImage] is a coordination method that remains on the parent
 * because it touches multiple sub-states (image, boxes, selection, undo, zoom, etc.).
 * [PhotoScanWizardState.nextImage] and [PhotoScanWizardState.prevImage] similarly delegate to the
 * parent's coordination method.
 */
class ImageBatchState {

    /** List of all source image files for batch processing. Empty for single-image mode. */
    private val _sourceFiles = MutableStateFlow<List<File>>(emptyList())
    val sourceFiles: StateFlow<List<File>> = _sourceFiles.asStateFlow()

    /** Index of the currently displayed image within [sourceFiles]. */
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    /** Cache of pre-processed images (loaded + detected). Keyed by index in sourceFiles. */
    private val _preProcessedCache = MutableStateFlow<Map<Int, PreProcessedImage>>(emptyMap())
    val preProcessedCache: StateFlow<Map<Int, PreProcessedImage>> = _preProcessedCache.asStateFlow()

    /** Progress of batch pre-processing: count of images that have been processed. */
    private val _preProcessCount = MutableStateFlow(0)
    val preProcessCount: StateFlow<Int> = _preProcessCount.asStateFlow()

    /** Whether batch pre-processing is currently running. */
    private val _preProcessing = MutableStateFlow(false)
    val preProcessing: StateFlow<Boolean> = _preProcessing.asStateFlow()

    /**
     * Set of batch file indices that should be automatically skipped during folder processing.
     * These are files identified as "backs" of photographs.
     */
    private val _skippedBatchIndices = MutableStateFlow<Set<Int>>(emptySet())
    val skippedBatchIndices: StateFlow<Set<Int>> = _skippedBatchIndices.asStateFlow()

    /** True when in batch mode (multiple source files). */
    val isBatchMode: Boolean
        get() = _sourceFiles.value.size > 1

    /** Total number of source images in the batch. */
    val batchTotal: Int
        get() = _sourceFiles.value.size

    /** Initializes batch mode with a list of source files. */
    fun initializeBatch(files: List<File>) {
        _sourceFiles.value = files
        _currentImageIndex.value = 0
        _preProcessedCache.value = emptyMap()
        _preProcessCount.value = 0
    }

    /** Stores a pre-processed result in the cache. */
    fun putPreProcessed(index: Int, result: PreProcessedImage) {
        _preProcessedCache.value = _preProcessedCache.value + (index to result)
        _preProcessCount.value = _preProcessedCache.value.size
    }

    /** Marks batch pre-processing as started or finished. */
    fun setPreProcessing(active: Boolean) {
        _preProcessing.value = active
    }

    /**
     * Advances the batch index to the next image and returns that file, or null if there are no
     * more. Does not load or detect — caller is responsible for calling initializeWithImage or
     * loadImageAndDetect afterwards.
     */
    fun advanceToNextBatchFile(): File? {
        val nextIndex = _currentImageIndex.value + 1
        if (nextIndex >= _sourceFiles.value.size) return null
        _currentImageIndex.value = nextIndex
        return _sourceFiles.value[nextIndex]
    }

    /**
     * Peeks at the next file in the batch without advancing the index. Returns null if there is no
     * next file. Note: this does NOT skip already-skipped files — use [peekNextNonSkippedBatchFile]
     * to find the next non-skipped file for preview purposes.
     */
    fun peekNextBatchFile(): File? {
        val nextIndex = _currentImageIndex.value + 1
        return if (nextIndex < _sourceFiles.value.size) _sourceFiles.value[nextIndex] else null
    }

    /**
     * Finds the next batch file that is not in the skipped set, starting from the next index after
     * the current one. Used for preview purposes to show the user what they'll process next.
     * Returns null if no more non-skipped files remain.
     */
    fun peekNextNonSkippedBatchFile(): File? {
        val skipped = _skippedBatchIndices.value
        for (i in (_currentImageIndex.value + 1) until _sourceFiles.value.size) {
            if (i !in skipped) return _sourceFiles.value[i]
        }
        return null
    }

    /** Returns true when in batch mode and there are more non-skipped images to process. */
    val hasMoreNonSkippedBatchImages: Boolean
        get() = isBatchMode && peekNextNonSkippedBatchFile() != null

    /**
     * Skips the next file in the batch by advancing the index without loading or detecting. Returns
     * the new "next file" after skipping, or null if there are no more files to process.
     */
    fun skipNextBatchFile(): File? {
        val skippedIndex = _currentImageIndex.value + 1
        if (skippedIndex >= _sourceFiles.value.size) return null
        _currentImageIndex.value = skippedIndex
        _skippedBatchIndices.value = _skippedBatchIndices.value + skippedIndex
        return peekNextBatchFile()
    }

    /** Returns true when in batch mode and there are more images after the current one. */
    val hasMoreBatchImages: Boolean
        get() = isBatchMode && _currentImageIndex.value < _sourceFiles.value.size - 1

    /** Returns true if the next image in the batch is pre-processed and ready. */
    val isNextImageReady: Boolean
        get() =
            _currentImageIndex.value < _sourceFiles.value.size - 1 &&
                _preProcessedCache.value.containsKey(_currentImageIndex.value + 1)

    /** Returns true if the previous image in the batch is pre-processed and ready. */
    val isPrevImageReady: Boolean
        get() =
            _currentImageIndex.value > 0 &&
                _preProcessedCache.value.containsKey(_currentImageIndex.value - 1)

    /**
     * Marks a batch file index as skipped (it's a "back" of an already-processed photo). The file
     * will be skipped during automatic batch progression.
     */
    fun markBatchIndexSkipped(index: Int) {
        _skippedBatchIndices.value = _skippedBatchIndices.value + index
    }

    /** Removes a batch file index from the skipped set. */
    fun unmarkBatchIndexSkipped(index: Int) {
        _skippedBatchIndices.value = _skippedBatchIndices.value - index
    }

    /** Returns the set of source files that are marked as skipped (backs of photos). */
    val skippedBatchFiles: Set<File>
        get() = _skippedBatchIndices.value.mapNotNull { _sourceFiles.value.getOrNull(it) }.toSet()

    /** Returns the pre-processed image at the given index, or null if not cached. */
    fun getCachedImage(index: Int): PreProcessedImage? {
        if (index < 0 || index >= _sourceFiles.value.size) return null
        return _preProcessedCache.value[index]
    }

    /** Sets the current image index (used by parent's switchToImage coordination). */
    fun setCurrentImageIndex(index: Int) {
        _currentImageIndex.value = index
    }

    /**
     * Resets batch state for a new import. Called during [PhotoScanWizardState.resetToImportStep].
     */
    fun reset() {
        _sourceFiles.value = emptyList()
        _currentImageIndex.value = 0
        _preProcessedCache.value = emptyMap()
        _preProcessCount.value = 0
        _preProcessing.value = false
        _skippedBatchIndices.value = emptySet()
    }
}
