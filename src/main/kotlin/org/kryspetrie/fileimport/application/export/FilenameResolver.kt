package org.kryspetrie.fileimport.application.export

import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.port.FileSystemPort

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

    /** Maximum number of filename conflict resolution attempts before throwing. */
    private const val MAX_RENAME_ATTEMPTS = 1000

    /**
     * Resolves filename conflicts by incrementing an index.
     *
     * @param fileSystem File system port for existence checks
     * @param directory Destination directory path
     * @param fileName Proposed filename
     * @return Resolved absolute path that doesn't conflict
     * @throws IllegalStateException if no unique filename can be found after [MAX_RENAME_ATTEMPTS]
     *   tries
     */
    suspend fun resolveFilenameConflict(
        fileSystem: FileSystemPort,
        directory: FilePath,
        fileName: String,
    ): String {
        var candidate = directory.resolve(fileName)
        var counter = 1

        val baseName = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "jpg")

        while (fileSystem.exists(candidate)) {
            if (counter > MAX_RENAME_ATTEMPTS) {
                throw IllegalStateException(
                    "Cannot resolve filename conflict for $fileName after $MAX_RENAME_ATTEMPTS attempts"
                )
            }
            candidate = directory.resolve("${baseName}_$counter.$extension")
            counter++
        }

        return fileSystem.absolutePath(candidate)
    }

    /**
     * Generates a unique filename for an export, avoiding conflicts with existing files and files
     * being exported in the current batch.
     *
     * @param fileSystem File system port for existence checks
     * @param destinationPath Destination folder path
     * @param baseName Base filename without extension
     * @param extension File extension
     * @param existingExports Set of filenames already used in this export batch
     * @return Unique filename (without path)
     * @throws IllegalStateException if no unique filename can be found after [MAX_RENAME_ATTEMPTS]
     *   tries
     */
    suspend fun generateUniqueFileName(
        fileSystem: FileSystemPort,
        destinationPath: FilePath,
        baseName: String,
        extension: String,
        existingExports: Set<String>,
    ): String {
        var counter = 1
        var candidate = "$baseName.$extension"

        while (true) {
            if (counter > MAX_RENAME_ATTEMPTS) {
                throw IllegalStateException(
                    "Cannot generate unique filename for $baseName.$extension after $MAX_RENAME_ATTEMPTS attempts"
                )
            }
            val exists =
                fileSystem.exists(destinationPath.resolve(candidate)) ||
                    candidate in existingExports
            if (!exists) break
            candidate = "${baseName}_$counter.$extension"
            counter++
        }

        return candidate
    }
}
