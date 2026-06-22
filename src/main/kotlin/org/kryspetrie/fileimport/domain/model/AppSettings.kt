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
     * When true, the Edit step starts on the Metadata tab instead of Rotate. Useful when you only
     * need to edit metadata and don't need to rotate photos.
     */
    val alwaysEditMetadata: Boolean = false,

    /**
     * Last-used correction strategy for the photo scan. Persists across sessions so users don't
     * have to re-select their preferred strategy each time.
     */
    val lastCorrectionStrategy: CorrectionStrategy = CorrectionStrategy.PERSPECTIVE,
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
}
