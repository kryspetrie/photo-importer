package org.kryspetrie.fileimport.domain.port

import kotlinx.coroutines.flow.Flow
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.FilePath
import org.kryspetrie.fileimport.domain.model.ImportProfile

interface SettingsPort {
    suspend fun loadSettings(): AppSettings

    suspend fun saveSettings(settings: AppSettings)

    fun observeSettings(): Flow<AppSettings>

    suspend fun loadProfile(profileId: String): ImportProfile?

    suspend fun saveProfile(profile: ImportProfile)

    suspend fun deleteProfile(profileId: String)

    suspend fun getAllProfiles(): List<ImportProfile>

    suspend fun exportProfile(profile: ImportProfile, file: FilePath)

    suspend fun importProfile(file: FilePath): ImportProfile

    suspend fun resetToDefaults()
}
