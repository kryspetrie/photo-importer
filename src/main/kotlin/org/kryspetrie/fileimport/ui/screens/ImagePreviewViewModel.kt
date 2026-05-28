package org.kryspetrie.fileimport.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.kryspetrie.fileimport.domain.model.ImageFile

/**
 * View model for [ImagePreviewScreen].
 *
 * Holds all UI state for the file preview/selection screen:
 * - View mode (list vs grid)
 * - Preview pane state
 * - Full-screen overlay state
 * - Split pane width
 * - Filter, sort, and search state
 * - Computed filtered + sorted image list
 */
class ImagePreviewViewModel {
    enum class ViewMode {
        LIST,
        GRID,
    }

    enum class FileFilter {
        ALL,
        PHOTOS,
        VIDEOS,
        RAW,
    }

    enum class SortMode {
        NAME,
        DATE,
        SIZE,
        TYPE,
    }

    // ── View state ──────────────────────────────────────────────

    var viewMode by mutableStateOf(ViewMode.GRID)
    var previewImage by mutableStateOf<ImageFile?>(null)
    var fullScreenImage by mutableStateOf<ImageFile?>(null)
    var paneWidthDp by mutableFloatStateOf(PANE_DEFAULT_DP)

    // ── Filter/sort state ──────────────────────────────────────

    var filterType by mutableStateOf(FileFilter.ALL)
    var sortMode by mutableStateOf(SortMode.NAME)
    var sortAscending by mutableStateOf(true)
    var searchQuery by mutableStateOf("")

    // ── Computed ───────────────────────────────────────────────

    fun filteredAndSorted(images: List<ImageFile>): List<ImageFile> {
        var result = images
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            result = result.filter { it.fileName.lowercase().contains(q) }
        }
        result =
            when (filterType) {
                FileFilter.ALL -> result
                FileFilter.PHOTOS -> result.filter { !it.fileType.isVideo && !it.fileType.isRaw }
                FileFilter.VIDEOS -> result.filter { it.fileType.isVideo }
                FileFilter.RAW -> result.filter { it.fileType.isRawFormat }
            }
        val sorted =
            when (sortMode) {
                SortMode.NAME -> result.sortedBy { it.fileName.lowercase() }
                SortMode.DATE -> result.sortedBy { it.dateTaken }
                SortMode.SIZE -> result.sortedBy { it.fileSize }
                SortMode.TYPE -> result.sortedBy { it.fileType.displayName }
            }
        return if (sortAscending) sorted else sorted.reversed()
    }

    fun toggleSortDirection() {
        sortAscending = !sortAscending
    }

    fun clearSearch() {
        searchQuery = ""
    }

    companion object {
        const val PANE_MIN_DP = 220f
        const val PANE_MAX_DP = 600f
        const val PANE_DEFAULT_DP = 320f
    }
}
