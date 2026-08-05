package org.kryspetrie.fileimport.application.thumbnails

import java.io.File
import org.kryspetrie.fileimport.domain.model.FilePath

/** Path conventions for per-album `.thumbs` disk caches. */
object FolderThumbnailPaths {
    const val THUMBS_DIR = ".thumbs"
    const val CACHE_EXTENSION = "jpg"

    /**
     * The album directory that owns the `.thumbs` folder for [sourceFile].
     *
     * When [editorSourcePath] is a folder, that folder is the album root. When it is a single file,
     * the file's parent is used. Otherwise each image's parent directory is the album root.
     */
    fun albumRoot(sourceFile: FilePath, editorSourcePath: String?): FilePath {
        if (!editorSourcePath.isNullOrBlank()) {
            val editor = File(editorSourcePath)
            if (editor.exists()) {
                if (editor.isDirectory) return FilePath(editor.canonicalPath)
                if (editor.isFile) {
                    val parent = editor.parentFile ?: return FilePath(editor.canonicalPath)
                    return FilePath(parent.canonicalPath)
                }
            }
        }
        val parent = sourceFile.toFile().parentFile ?: return FilePath(sourceFile.path)
        return FilePath(parent.canonicalPath)
    }

    /** Relative path inside `.thumbs` mirroring the source file layout (always `.jpg`). */
    fun cacheRelativePath(albumRoot: FilePath, sourceFile: FilePath): String {
        val root = albumRoot.toFile().canonicalFile
        val source = sourceFile.toFile().canonicalFile
        val relative = root.toURI().relativize(source.toURI()).path
        val withoutExt = relative.substringBeforeLast('.').ifEmpty { relative }
        return "$withoutExt.$CACHE_EXTENSION"
    }

    fun cacheFilePath(albumRoot: FilePath, sourceFile: FilePath): FilePath =
        albumRoot.resolve("$THUMBS_DIR/${cacheRelativePath(albumRoot, sourceFile)}")

    fun thumbsDirectory(albumRoot: FilePath): FilePath = albumRoot.resolve(THUMBS_DIR)
}
