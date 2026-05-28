package org.kryspetrie.fileimport.infrastructure.adapter

import org.kryspetrie.fileimport.domain.model.FilePath

/**
 * Extension functions for converting between [FilePath] and JVM I/O types.
 *
 * The [FilePath.toFile] method is defined directly on the value class. These extensions provide
 * additional convenience conversions.
 */

/** Convert a [java.io.File] to a [FilePath] for passing into domain layer. */
fun java.io.File.toFilePath(): FilePath = FilePath(absolutePath)

/** Convert a list of [java.io.File] to a list of [FilePath]. */
fun List<java.io.File>.toFilePaths(): List<FilePath> = map { it.toFilePath() }

/** Convert a list of [FilePath] to a list of [java.io.File]. */
fun List<FilePath>.toFiles(): List<java.io.File> = map { it.toFile() }
