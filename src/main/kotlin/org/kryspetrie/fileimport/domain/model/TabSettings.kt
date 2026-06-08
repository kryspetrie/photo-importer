package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-tab import settings. Encapsulates all persisted settings for a specific workflow tab.
 *
 * Each tab (Import, Photo Import Wizard, Photo Scan) has its own isolated settings so users can
 * configure each workflow independently without affecting others.
 */
@Serializable
data class TabSettings(
    // Paths
    val lastSourcePath: String = "",
    val lastDestinationPath: String = "",
    val recentSourcePaths: List<String> = emptyList(),
    val recentDestinationPaths: List<String> = emptyList(),

    // Import configuration
    val configuration: ImportConfiguration = ImportConfiguration(),
) {
    fun withRecentSourcePath(path: String): TabSettings =
        if (path.isBlank()) this
        else
            copy(
                lastSourcePath = path,
                recentSourcePaths = listOf(path) + recentSourcePaths.filter { it != path }.take(4),
            )

    fun withRecentDestinationPath(path: String): TabSettings =
        if (path.isBlank()) this
        else
            copy(
                lastDestinationPath = path,
                recentDestinationPaths =
                    listOf(path) + recentDestinationPaths.filter { it != path }.take(4),
            )

    fun withConfiguration(config: ImportConfiguration): TabSettings = copy(configuration = config)
}
