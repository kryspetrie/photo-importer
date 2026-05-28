package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportProfile
import org.kryspetrie.fileimport.domain.port.SettingsPort
import org.kryspetrie.fileimport.domain.port.TimeProvider

/**
 * Application paths for settings, data, and defaults.
 *
 * Delegates OS-specific path resolution to [Platform] for consistency. The settings directory
 * follows platform conventions:
 * - macOS: ~/Library/Application Support/petrie-file-importer/
 * - Linux: ~/.config/petrie-file-importer/ (XDG spec)
 * - Windows: %APPDATA%/petrie-file-importer/
 */
object AppPaths {

    /** Settings directory, resolved per-platform via [Platform]. */
    val settingsDir: File
        get() = Platform.appDataDir

    /**
     * Default destination folder suggestion for new users.
     *
     * Returns user's Pictures folder if available, otherwise home directory.
     */
    val defaultDestination: File
        get() {
            val pictures = File(System.getProperty("user.home"), "Pictures")
            return if (pictures.exists() && pictures.isDirectory) {
                File(pictures, "Imports")
            } else {
                File(System.getProperty("user.home"), "Imports")
            }
        }
}

class SettingsAdapter(
    private val settingsDir: File = AppPaths.settingsDir,
    private val timeProvider: TimeProvider = DefaultTimeProvider(),
) : SettingsPort {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val settingsFile = File(settingsDir, "settings.json")
    private val _settings = MutableStateFlow(AppSettings())

    init {
        settingsDir.mkdirs()
        // Load settings from disk on startup
        try {
            if (settingsFile.exists()) {
                val loaded = json.decodeFromString<AppSettings>(settingsFile.readText())
                _settings.value = loaded
            }
        } catch (_: Exception) {
            // Failed to load, use defaults
        }
    }

    override suspend fun loadSettings(): AppSettings =
        try {
                if (settingsFile.exists())
                    json.decodeFromString<AppSettings>(settingsFile.readText())
                else AppSettings()
            } catch (_: Exception) {
                AppSettings()
            }
            .also { _settings.value = it }

    override suspend fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
            _settings.value = settings
        } catch (_: Exception) {
            /* Settings file write failed — non-critical */
        }
    }

    override fun observeSettings(): Flow<AppSettings> = _settings

    override suspend fun loadProfile(profileId: String): ImportProfile? =
        loadSettings().profiles.find { it.id == profileId }

    override suspend fun saveProfile(profile: ImportProfile) {
        val settings = loadSettings()
        val existing = settings.profiles.indexOfFirst { it.id == profile.id }
        val profiles =
            if (existing >= 0)
                settings.profiles.toMutableList().apply {
                    set(existing, profile.copy(updatedAt = timeProvider.currentTimeMillis()))
                }
            else settings.profiles + profile
        saveSettings(settings.copy(profiles = profiles))
    }

    override suspend fun deleteProfile(profileId: String) {
        val settings = loadSettings()
        saveSettings(
            settings.copy(
                profiles = settings.profiles.filter { it.id != profileId },
                activeProfileId =
                    if (settings.activeProfileId == profileId) null else settings.activeProfileId,
            )
        )
    }

    override suspend fun getAllProfiles(): List<ImportProfile> = loadSettings().profiles

    override suspend fun exportProfile(profile: ImportProfile, file: File) {
        file.writeText(json.encodeToString(profile))
    }

    override suspend fun importProfile(file: File): ImportProfile =
        json.decodeFromString<ImportProfile>(file.readText())

    override suspend fun resetToDefaults() {
        saveSettings(AppSettings())
    }
}
