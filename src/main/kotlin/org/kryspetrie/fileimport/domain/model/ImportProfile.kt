package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val configuration: ImportConfiguration = ImportConfiguration(),
    val cameraName: String = "",
    val lastSourcePath: String = "",
    val lastDestinationPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val profiles: List<ImportProfile> = emptyList(),
    val activeProfileId: String? = null,
    val windowState: WindowState = WindowState(),
    val theme: AppTheme = AppTheme.SYSTEM,
    val savedFolderPresets: List<PatternPreset> = emptyList(),
    val savedFilenamePresets: List<PatternPreset> = emptyList()
)

@Serializable
data class WindowState(
    val width: Int = 1200,
    val height: Int = 800,
    val x: Int? = null,
    val y: Int? = null,
    val isMaximized: Boolean = false
)

enum class AppTheme {
  LIGHT,
  DARK,
  SYSTEM
}
