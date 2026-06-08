package org.kryspetrie.fileimport.application.export

import java.io.File

/**
 * Resolves filename conflicts for photo exports by incrementing an index.
 *
 * When a filename already exists in the destination:
 * ```
 * photo.jpg          → photo_1.jpg
 * photo_1.jpg       → photo_2.jpg
 * photo_2.jpg       → photo_3.jpg
 * ```
 */
object FilenameResolver {

    /**
     * Resolves filename conflicts by incrementing an index.
     *
     * @param directory Destination directory
     * @param fileName Proposed filename
     * @return Resolved absolute path that doesn't conflict
     */
    fun resolveFilenameConflict(directory: File, fileName: String): String {
        var candidate = File(directory, fileName)
        var counter = 1

        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "jpg")

        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$counter.$extension")
            counter++
        }

        return candidate.absolutePath
    }

    /**
     * Generates a unique filename for an export, avoiding conflicts with existing files and files
     * being exported in the current batch.
     *
     * @param destinationPath Destination folder
     * @param baseName Base filename without extension
     * @param extension File extension
     * @param existingExports Set of filenames already used in this export batch
     * @return Unique filename (without path)
     */
    fun generateUniqueFileName(
        destinationPath: String,
        baseName: String,
        extension: String,
        existingExports: Set<String>,
    ): String {
        var counter = 1
        var candidate = "$baseName.$extension"
        val destDir = File(destinationPath)

        while (true) {
            val exists = File(destDir, candidate).exists() || candidate in existingExports
            if (!exists) break
            candidate = "${baseName}_$counter.$extension"
            counter++
        }

        return candidate
    }
}
