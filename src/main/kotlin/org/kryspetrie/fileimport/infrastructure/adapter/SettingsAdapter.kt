package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportProfile
import org.kryspetrie.fileimport.domain.port.SettingsPort

/**
 * OS-specific settings directory for Petrie File Importer.
 *
 * Uses platform-appropriate conventions:
 * - Linux: ~/.config/petrie-file-importer/ (XDG spec)
 * - macOS: ~/Library/Application Support/PetrieFileImporter/
 * - Windows: %APPDATA%\PetrieFileImporter\
 */
object AppPaths {
  private const val APP_NAME = "petrie-file-importer"
  private const val CONFIG_DIR = ".config"
  private const val APP_SUPPORT = "Library/Application Support"

  val settingsDir: File
    get() =
        when {
          isLinux -> {
            File(System.getProperty("user.home"), "$CONFIG_DIR/$APP_NAME")
          }
          isMac -> {
            File(System.getProperty("user.home"), "$APP_SUPPORT/$APP_NAME")
          }
          isWindows -> {
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), APP_NAME)
          }
          else -> {
            // Fallback to ~/.petrie-file-importer for unknown platforms
            File(System.getProperty("user.home"), ".$APP_NAME")
          }
        }

  private val isLinux: Boolean
    get() =
        System.getProperty("os.name").lowercase().contains("linux") ||
            File("/proc/version").exists() && !isMac && !isWindows

  private val isMac: Boolean
    get() =
        System.getProperty("os.name").lowercase().contains("mac") ||
            System.getProperty("os.name").lowercase().contains("darwin")

  private val isWindows: Boolean
    get() = System.getProperty("os.name").lowercase().contains("windows")

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

class SettingsAdapter(private val settingsDir: File = AppPaths.settingsDir) : SettingsPort {
  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val settingsFile = File(settingsDir, "settings.json")
  private val _settings = MutableStateFlow(AppSettings())

  init {
    settingsDir.mkdirs()
  }

  override suspend fun loadSettings(): AppSettings =
      try {
            if (settingsFile.exists()) json.decodeFromString<AppSettings>(settingsFile.readText())
            else AppSettings()
          } catch (e: Exception) {
            AppSettings()
          }
          .also { _settings.value = it }

  override suspend fun saveSettings(settings: AppSettings) {
    try {
      settingsFile.writeText(json.encodeToString(settings))
      _settings.value = settings
    } catch (e: Exception) {}
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
              set(existing, profile.copy(updatedAt = System.currentTimeMillis()))
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
                if (settings.activeProfileId == profileId) null else settings.activeProfileId))
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
