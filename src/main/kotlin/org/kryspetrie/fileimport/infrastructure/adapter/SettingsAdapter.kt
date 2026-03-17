package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.ImportProfile
import org.kryspetrie.fileimport.domain.port.SettingsPort

class SettingsAdapter(
    private val settingsDir: File = File(System.getProperty("user.home"), ".petriefi")
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
