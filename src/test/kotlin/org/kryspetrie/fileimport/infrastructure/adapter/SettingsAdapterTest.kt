package org.kryspetrie.fileimport.infrastructure.adapter

import java.io.File
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.AppTheme
import org.kryspetrie.fileimport.domain.model.ImportProfile

@DisplayName("SettingsAdapter")
class SettingsAdapterTest {
    private lateinit var adapter: SettingsAdapter

    @TempDir lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        adapter = SettingsAdapter(tempDir)
    }

    @Nested
    @DisplayName("settings persistence")
    inner class SettingsPersistence {
        @Test
        @DisplayName("should load default settings when no file exists")
        fun shouldLoadDefaults() = runTest {
            val settings = adapter.loadSettings()

            assertThat(settings.profiles).isEmpty()
            assertThat(settings.theme).isEqualTo(AppTheme.SYSTEM)
        }

        @Test
        @DisplayName("should save and load settings")
        fun shouldSaveAndLoad() = runTest {
            val settings = AppSettings(theme = AppTheme.DARK, activeProfileId = "test")

            adapter.saveSettings(settings)
            val loaded = adapter.loadSettings()

            assertThat(loaded.theme).isEqualTo(AppTheme.DARK)
            assertThat(loaded.activeProfileId).isEqualTo("test")
        }

        @Test
        @DisplayName("should handle corrupt settings file gracefully")
        fun shouldHandleCorruptFile() = runTest {
            File(tempDir, "settings.json").writeText("invalid json")

            val settings = adapter.loadSettings()

            // When loading corrupt file, should return defaults with empty profiles and default
            // theme
            assertThat(settings.profiles).isEmpty()
            assertThat(settings.theme).isEqualTo(AppTheme.SYSTEM)
            assertThat(settings.activeProfileId).isNull()
            // PhotoScan profiles have defaults, so just verify one exists
            assertThat(settings.photoScanProfiles).isNotEmpty()
            assertThat(settings.photoScanProfiles[0].name).isEqualTo("Default")
        }
    }

    @Nested
    @DisplayName("profile management")
    inner class ProfileManagement {
        @Test
        @DisplayName("should save new profile")
        fun shouldSaveNewProfile() = runTest {
            val profile = ImportProfile(name = "Camera Import")

            adapter.saveProfile(profile)

            val profiles = adapter.getAllProfiles()
            assertThat(profiles).hasSize(1)
            assertThat(profiles[0].name).isEqualTo("Camera Import")
        }

        @Test
        @DisplayName("should update existing profile")
        fun shouldUpdateExistingProfile() = runTest {
            val profile = ImportProfile(id = "p1", name = "Original")
            adapter.saveProfile(profile)
            adapter.saveProfile(profile.copy(name = "Updated"))

            val profiles = adapter.getAllProfiles()
            assertThat(profiles).hasSize(1)
            assertThat(profiles[0].name).isEqualTo("Updated")
        }

        @Test
        @DisplayName("should delete profile")
        fun shouldDeleteProfile() = runTest {
            val profile = ImportProfile(id = "p1", name = "ToDelete")
            adapter.saveProfile(profile)

            adapter.deleteProfile("p1")

            assertThat(adapter.getAllProfiles()).isEmpty()
        }

        @Test
        @DisplayName("should clear active profile when deleted")
        fun shouldClearActiveOnDelete() = runTest {
            val profile = ImportProfile(id = "p1", name = "Active")
            adapter.saveProfile(profile)
            adapter.saveSettings(adapter.loadSettings().copy(activeProfileId = "p1"))

            adapter.deleteProfile("p1")

            assertThat(adapter.loadSettings().activeProfileId).isNull()
        }

        @Test
        @DisplayName("should load specific profile by ID")
        fun shouldLoadProfileById() = runTest {
            val p1 = ImportProfile(id = "p1", name = "Profile 1")
            val p2 = ImportProfile(id = "p2", name = "Profile 2")
            adapter.saveProfile(p1)
            adapter.saveProfile(p2)

            val loaded = adapter.loadProfile("p2")

            assertThat(loaded).isNotNull
            assertThat(loaded!!.name).isEqualTo("Profile 2")
        }

        @Test
        @DisplayName("should return null for unknown profile ID")
        fun shouldReturnNullForUnknownId() = runTest {
            assertThat(adapter.loadProfile("unknown")).isNull()
        }
    }

    @Nested
    @DisplayName("reset")
    inner class Reset {
        @Test
        @DisplayName("should reset to defaults")
        fun shouldResetToDefaults() = runTest {
            adapter.saveSettings(AppSettings(theme = AppTheme.DARK))

            adapter.resetToDefaults()

            val settings = adapter.loadSettings()
            assertThat(settings.theme).isEqualTo(AppTheme.SYSTEM)
        }
    }
}
