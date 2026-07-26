package org.kryspetrie.fileimport.domain.model

import kotlinx.serialization.Serializable

// PhotoScanProfile is in the same package, no import needed

/**
 * Application-wide settings and preferences.
 *
 * This is the root settings object that contains all persisted application configuration. It's
 * loaded on startup and saved whenever settings change.
 *
 * Persisted to JSON and stored in `~/.petrie-importer/settings.json` via [SettingsPort].
 */
@Serializable
data class AppSettings(
    /** All saved import profiles. */
    val profiles: List<ImportProfile> = emptyList(),

    /** ID of currently selected import profile, or null if none selected. */
    val activeProfileId: String? = null,

    /** Window state (size and position). */
    val windowState: WindowState = WindowState(),

    /** Theme preference (light/dark/system). */
    val theme: AppTheme = AppTheme.SYSTEM,

    /**
     * Locale preference for UI language (e.g., "en", "de", "ja"). Falls back to "en" if unset or
     * invalid.
     */
    val locale: String = "en",

    /** User-saved folder pattern presets. */
    val savedFolderPresets: List<PatternPreset> = emptyList(),

    /** User-saved filename pattern presets. */
    val savedFilenamePresets: List<PatternPreset> = emptyList(),

    /** All saved Photo Scan profiles. */
    val photoScanProfiles: List<PhotoScanProfile> = listOf(PhotoScanProfile.createDefault()),

    /** ID of currently active Photo Scan profile, or null to use the first profile. */
    val activePhotoScanProfileId: String? = null,

    /** Recent destination folders for Photo Scan. Maximum 5 entries. */
    val recentPhotoScanDestinations: List<String> = emptyList(),

    /** Settings for the standard Import tab. */
    val importTabSettings: TabSettings = TabSettings(),

    /** Settings for the Photo Scan Import tab. */
    val photoScanImportTabSettings: TabSettings = TabSettings(),

    /** Settings for the Photo Scan tab. */
    val photoScanTabSettings: TabSettings = TabSettings(),

    /** Recently used metadata values for photo scan EXIF fields. */
    val metadataHistory: MetadataHistory = MetadataHistory(),

    /**
     * When true, skip the Crop & Rotate (Summary) screen entirely and go directly from Overview to
     * Edit (starting in metadata mode). Useful when you don't need to crop or rotate photos.
     */
    val skipCropAndRotate: Boolean = false,

    /** Last-used map location (latitude) in the location picker. Persists across sessions. */
    val lastMapLat: Double = 39.0,

    /** Last-used map location (longitude) in the location picker. Persists across sessions. */
    val lastMapLon: Double = -78.0,

    /** Last-used map zoom level in the location picker. Persists across sessions. */
    val lastMapZoom: Double = 5.0,

    /**
     * When true, automatically skip files that have been used as "back" images during batch folder
     * processing. This prevents the wizard from presenting back-of-photo images as photos to
     * process.
     */
    val autoSkipBackFiles: Boolean = true,

    /** Recent folder paths used in the Bulk Metadata Editor. Maximum 5 entries. */
    val metadataEditorRecentPaths: List<String> = emptyList(),

    /** Preferred layout for the metadata editor (thumbnail strip vs file picker). */
    val metadataEditorLayoutMode: MetadataEditorLayoutMode = MetadataEditorLayoutMode.SIDEBAR,

    /** How files are displayed in the metadata editor file browser. */
    val metadataEditorFileViewMode: MetadataEditorFileViewMode = MetadataEditorFileViewMode.ICONS,

    /**
     * When true, automatically detect and correct photo orientation (rotation) when importing
     * photos. Uses the deep-image-orientation-angle-detection model. Requires the orientation model
     * to be available on the classpath.
     */
    val autoOrientOnImport: Boolean = false,

    /** Watch folder configurations for automatic import. Persisted across app restarts. */
    val watchConfigs: List<WatchFolderConfig> = emptyList(),

    /**
     * When true, show the auto-rotate button in the Metadata Editor. The button appears next to
     * manual rotation controls and triggers ML-based orientation detection.
     */
    val autoOrientInMetadataEditor: Boolean = true,
) {
    /** Returns the currently active Photo Scan profile, or the default if none is selected. */
    val activePhotoScanProfile: PhotoScanProfile
        get() =
            photoScanProfiles.find { it.id == activePhotoScanProfileId }
                ?: photoScanProfiles.firstOrNull()
                ?: PhotoScanProfile.createDefault()

    /** Returns the most recently used destination, or the default from the active profile. */
    val lastPhotoScanDestination: String
        get() =
            recentPhotoScanDestinations.firstOrNull() ?: activePhotoScanProfile.resolveDestination()

    /** Adds a path to the recent source paths list (max 5, deduped). */
    fun withRecentSourcePath(path: String): AppSettings =
        if (path.isBlank()) this
        else withImportTabSettings(importTabSettings.withRecentSourcePath(path))

    /** Adds a path to the recent destination paths list (max 5, deduped). */
    fun withRecentDestinationPath(path: String): AppSettings =
        if (path.isBlank()) this
        else withImportTabSettings(importTabSettings.withRecentDestinationPath(path))

    /** Adds a path to the recent Photo Scan destinations list (max 5, deduped). */
    fun withRecentPhotoScanDestination(path: String): AppSettings =
        if (path.isBlank()) this
        else {
            val updated = recentPhotoScanDestinations.filter { it != path }.take(4)
            copy(recentPhotoScanDestinations = listOf(path) + updated)
        }

    /** Updates the Import tab settings. */
    fun withImportTabSettings(tabSettings: TabSettings): AppSettings =
        copy(importTabSettings = tabSettings)

    /** Updates the Photo Import tab settings. */
    fun withPhotoScanImportTabSettings(tabSettings: TabSettings): AppSettings =
        copy(photoScanImportTabSettings = tabSettings)

    /** Updates the Photo Scan tab settings. */
    fun withPhotoScanTabSettings(tabSettings: TabSettings): AppSettings =
        copy(photoScanTabSettings = tabSettings)

    /** Adds a metadata value to history. */
    fun addMetadataHistory(fieldKey: String, value: String): AppSettings =
        copy(metadataHistory = metadataHistory.addValue(fieldKey, value))

    /** Removes a metadata value from history. */
    fun removeMetadataHistory(fieldKey: String, value: String): AppSettings =
        copy(metadataHistory = metadataHistory.removeValue(fieldKey, value))

    /** Adds a complete metadata set to history (for "apply recent values" feature). */
    fun addMetadataSet(set: RecentMetadataSet): AppSettings =
        copy(metadataHistory = metadataHistory.addSet(set))

    /** Removes a metadata set from history by timestamp. */
    fun removeMetadataSet(timestamp: Long): AppSettings =
        copy(metadataHistory = metadataHistory.removeSet(timestamp))

    /** Adds a folder path to the metadata editor recent paths list (max 5, deduped). */
    fun withMetadataEditorRecentPath(path: String): AppSettings =
        if (path.isBlank()) this
        else {
            val updated = metadataEditorRecentPaths.filter { it != path }.take(4)
            copy(metadataEditorRecentPaths = listOf(path) + updated)
        }

    /** Updates the metadata editor layout mode. */
    fun withMetadataEditorLayoutMode(mode: MetadataEditorLayoutMode): AppSettings =
        copy(metadataEditorLayoutMode = mode)

    /** Updates the metadata editor file browser view mode. */
    fun withMetadataEditorFileViewMode(mode: MetadataEditorFileViewMode): AppSettings =
        copy(metadataEditorFileViewMode = mode)

    /** Updates the locale setting. */
    fun withLocale(localeCode: String): AppSettings = copy(locale = localeCode)

    /** Adds or updates a watch folder config. Replaces any existing config with the same [id]. */
    fun withWatchConfig(config: WatchFolderConfig): AppSettings {
        val updated = watchConfigs.map { if (it.id == config.id) config else it }
        return copy(
            watchConfigs = if (updated.any { it.id == config.id }) updated else updated + config
        )
    }

    /** Removes a watch folder config by ID. */
    fun withoutWatchConfig(configId: String): AppSettings =
        copy(watchConfigs = watchConfigs.filter { it.id != configId })
}
