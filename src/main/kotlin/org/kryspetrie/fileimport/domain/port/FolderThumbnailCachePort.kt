package org.kryspetrie.fileimport.domain.port

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ProcessedImage

/**
 * Disk-backed thumbnail cache stored in `<album>/.thumbs/`, keyed by source file layout.
 *
 * Thumbnails are regenerated when the source file is newer than the cached JPEG. [reconcileSources]
 * removes stale and orphan entries whenever a folder or file list is processed.
 */
interface FolderThumbnailCachePort {
    /**
     * Ensures `.thumbs` is consistent with [sourceFiles]: drops orphans, deletes stale entries for
     * known sources. No-op when [diskCacheEnabled] is false.
     */
    suspend fun reconcileSources(
        sourceFiles: List<FilePath>,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean = true,
    )

    /**
     * Returns a thumbnail for [sourceFile], loading from disk or generating as needed. When
     * [diskCacheEnabled] is false, thumbnails are generated in memory only.
     */
    suspend fun getThumbnail(
        sourceFile: FilePath,
        editorSourcePath: String?,
        maxPx: Int,
        diskCacheEnabled: Boolean = true,
    ): ProcessedImage?

    /** Removes the cached thumbnail for [sourceFile] (e.g. after overwrite save). */
    suspend fun invalidate(sourceFile: FilePath, editorSourcePath: String?, maxPx: Int)

    /**
     * Removes cached thumbnails for [sourceFiles] at their current or former locations (e.g. after
     * library reorganize moves).
     */
    suspend fun invalidateSources(sourceFiles: List<FilePath>, libraryRoot: String?)

    /** Deletes the entire `.thumbs` folder under [libraryRoot]. */
    suspend fun deleteThumbsFolder(libraryRoot: FilePath)
}
