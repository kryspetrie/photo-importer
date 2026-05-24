package org.kryspetrie.fileimport.domain.model

/**
 * Import mode selection — determines which files from the source are included.
 *
 * - **ALL**: Import all files found in source
 * - **NEW**: Import only files not previously imported (checks import history/hash cache)
 * - **SELECT**: User manually selects which files to import
 *
 * @see ImportConfiguration.importMode
 */
enum class ImportMode {
    ALL,
    NEW,
    SELECT,
}
