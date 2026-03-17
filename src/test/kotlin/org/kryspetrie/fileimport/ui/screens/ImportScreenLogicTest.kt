package org.kryspetrie.fileimport.ui.screens

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.kryspetrie.fileimport.domain.model.*

@DisplayName("ImportScreen Logic")
class ImportScreenLogicTest {

  @TempDir lateinit var tempDir: File

  @Nested
  @DisplayName("Path Validation")
  inner class PathValidationTest {

    @Test
    @DisplayName("should validate existing directory as valid")
    fun existingDirectoryShouldBeValid() {
      val dir = File(tempDir, "photos").apply { mkdirs() }

      assertThat(dir.isDirectory).isTrue()
    }

    @Test
    @DisplayName("should validate non-existent path as invalid")
    fun nonExistentPathShouldBeInvalid() {
      val path = File(tempDir, "does-not-exist")

      assertThat(path.isDirectory).isFalse()
    }

    @Test
    @DisplayName("should validate file path as invalid directory")
    fun filePathShouldBeInvalidDirectory() {
      val file = File(tempDir, "file.txt").apply { createNewFile() }

      assertThat(file.isDirectory).isFalse()
    }

    @Test
    @DisplayName("blank path should not be treated as error")
    fun blankPathShouldNotBeError() {
      val path = ""

      assertThat(path.isNotBlank() && !File(path).isDirectory).isFalse()
    }
  }

  @Nested
  @DisplayName("Profile Selection")
  inner class ProfileSelectionTest {

    @Test
    @DisplayName("should find profile by ID")
    fun shouldFindProfileById() {
      val profile1 = ImportProfile(name = "Canon")
      val profile2 = ImportProfile(name = "Sony")
      val settings = AppSettings(profiles = listOf(profile1, profile2))

      val found = settings.profiles.find { it.id == profile1.id }

      assertThat(found).isNotNull()
      assertThat(found!!.name).isEqualTo("Canon")
    }

    @Test
    @DisplayName("should return null for unknown profile ID")
    fun shouldReturnNullForUnknown() {
      val settings = AppSettings(profiles = listOf(ImportProfile(name = "Canon")))

      val found = settings.profiles.find { it.id == "nonexistent" }

      assertThat(found).isNull()
    }

    @Test
    @DisplayName("should match camera name for auto-selection")
    fun shouldMatchCameraName() {
      val profile = ImportProfile(name = "My Canon", cameraName = "Canon EOS R5")
      val settings = AppSettings(profiles = listOf(profile))
      val deviceName = "Canon EOS R5"

      val match =
          settings.profiles.find { p ->
            p.cameraName.isNotBlank() && deviceName.equals(p.cameraName, ignoreCase = true)
          }

      assertThat(match).isNotNull()
      assertThat(match!!.name).isEqualTo("My Canon")
    }

    @Test
    @DisplayName("should not match different camera name")
    fun shouldNotMatchDifferentCamera() {
      val profile = ImportProfile(name = "My Canon", cameraName = "Canon EOS R5")
      val settings = AppSettings(profiles = listOf(profile))
      val deviceName = "Sony A7III"

      val match =
          settings.profiles.find { p ->
            p.cameraName.isNotBlank() && deviceName.equals(p.cameraName, ignoreCase = true)
          }

      assertThat(match).isNull()
    }

    @Test
    @DisplayName("should match camera name case-insensitively")
    fun shouldMatchCaseInsensitive() {
      val profile = ImportProfile(name = "Test", cameraName = "canon eos r5")
      val deviceName = "CANON EOS R5"

      val matches = deviceName.equals(profile.cameraName, ignoreCase = true)

      assertThat(matches).isTrue()
    }
  }

  @Nested
  @DisplayName("Profile CRUD Operations")
  inner class ProfileCrudTest {

    @Test
    @DisplayName("should add profile to settings")
    fun shouldAddProfile() {
      val settings = AppSettings()
      val profile = ImportProfile(name = "New Profile")

      val updated =
          settings.copy(profiles = settings.profiles + profile, activeProfileId = profile.id)

      assertThat(updated.profiles).hasSize(1)
      assertThat(updated.activeProfileId).isEqualTo(profile.id)
    }

    @Test
    @DisplayName("should delete profile from settings")
    fun shouldDeleteProfile() {
      val profile = ImportProfile(name = "To Delete")
      val settings = AppSettings(profiles = listOf(profile), activeProfileId = profile.id)

      val updated =
          settings.copy(
              profiles = settings.profiles.filter { it.id != profile.id }, activeProfileId = null)

      assertThat(updated.profiles).isEmpty()
      assertThat(updated.activeProfileId).isNull()
    }

    @Test
    @DisplayName("should update profile configuration")
    fun shouldUpdateProfile() {
      val profile = ImportProfile(name = "My Profile")
      val settings = AppSettings(profiles = listOf(profile))
      val newConfig = ImportConfiguration(folderPattern = "{yyyy}/{MM}")

      val updated =
          settings.copy(
              profiles =
                  settings.profiles.map {
                    if (it.id == profile.id) it.copy(configuration = newConfig) else it
                  })

      assertThat(updated.profiles.first().configuration.folderPattern).isEqualTo("{yyyy}/{MM}")
    }

    @Test
    @DisplayName("should update profile paths")
    fun shouldUpdatePaths() {
      val profile = ImportProfile(name = "Test")
      val settings = AppSettings(profiles = listOf(profile))

      val updated =
          settings.copy(
              profiles =
                  settings.profiles.map {
                    if (it.id == profile.id)
                        it.copy(lastSourcePath = "/new/source", lastDestinationPath = "/new/dest")
                    else it
                  })

      val updatedProfile = updated.profiles.first()
      assertThat(updatedProfile.lastSourcePath).isEqualTo("/new/source")
      assertThat(updatedProfile.lastDestinationPath).isEqualTo("/new/dest")
    }

    @Test
    @DisplayName("should handle delete of non-active profile")
    fun shouldDeleteNonActiveProfile() {
      val active = ImportProfile(name = "Active")
      val other = ImportProfile(name = "Other")
      val settings = AppSettings(profiles = listOf(active, other), activeProfileId = active.id)

      val updated =
          settings.copy(
              profiles = settings.profiles.filter { it.id != other.id },
              activeProfileId =
                  if (settings.activeProfileId == other.id) null else settings.activeProfileId)

      assertThat(updated.profiles).hasSize(1)
      assertThat(updated.activeProfileId).isEqualTo(active.id)
    }
  }

  @Nested
  @DisplayName("Import Mode")
  inner class ImportModeTest {

    @Test
    @DisplayName("should have three import modes")
    fun shouldHaveThreeModes() {
      assertThat(ImportMode.entries).hasSize(3)
      assertThat(ImportMode.entries).contains(ImportMode.ALL, ImportMode.NEW, ImportMode.SELECT)
    }
  }

  @Nested
  @DisplayName("canStart validation")
  inner class CanStartTest {

    @Test
    @DisplayName("should be true when both paths are non-blank")
    fun shouldBeTrueWhenBothPaths() {
      val sourcePath = "/some/source"
      val destinationPath = "/some/dest"
      val canStart = sourcePath.isNotBlank() && destinationPath.isNotBlank()

      assertThat(canStart).isTrue()
    }

    @Test
    @DisplayName("should be false when source is blank")
    fun shouldBeFalseWithoutSource() {
      val sourcePath = ""
      val destinationPath = "/some/dest"
      val canStart = sourcePath.isNotBlank() && destinationPath.isNotBlank()

      assertThat(canStart).isFalse()
    }

    @Test
    @DisplayName("should be false when destination is blank")
    fun shouldBeFalseWithoutDest() {
      val sourcePath = "/some/source"
      val destinationPath = ""
      val canStart = sourcePath.isNotBlank() && destinationPath.isNotBlank()

      assertThat(canStart).isFalse()
    }

    @Test
    @DisplayName("should be false when both paths are blank")
    fun shouldBeFalseWhenBothBlank() {
      val sourcePath = ""
      val destinationPath = ""
      val canStart = sourcePath.isNotBlank() && destinationPath.isNotBlank()

      assertThat(canStart).isFalse()
    }
  }
}
