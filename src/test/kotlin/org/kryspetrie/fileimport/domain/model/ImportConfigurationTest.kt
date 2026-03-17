package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@DisplayName("ImportConfiguration")
class ImportConfigurationTest {

  @Test
  @DisplayName("should have sensible defaults")
  fun shouldHaveSensibleDefaults() {
    val config = ImportConfiguration()

    assertThat(config.folderPattern).isEqualTo("{yyyy-MM-dd}")
    assertThat(config.preserveOriginalName).isTrue()
    assertThat(config.verifyAfterCopy).isTrue()
    assertThat(config.deleteAfterImport).isFalse()
    assertThat(config.conflictResolution).isEqualTo(ConflictResolution.RENAME)
    assertThat(config.rawJpegPairMode).isEqualTo(RawJpegPairMode.IMPORT_BOTH)
    assertThat(config.keepPairsTogether).isTrue()
    assertThat(config.importSidecars).isTrue()
  }

  @Test
  @DisplayName("FolderPresets should have matching examples")
  fun folderPresetsShouldHaveExamples() {
    FolderPresets.builtIn.forEach { preset ->
      assertThat(preset.isBuiltIn).isTrue()
      assertThat(preset.name).isNotBlank()
      assertThat(preset.pattern).isNotBlank()
    }
  }

  @Test
  @DisplayName("FilenamePresets should all be built-in")
  fun filenamePresetsShouldBeBuiltIn() {
    FilenamePresets.builtIn.forEach { preset ->
      assertThat(preset.isBuiltIn).isTrue()
      assertThat(preset.name).isNotBlank()
      assertThat(preset.pattern).isNotBlank()
    }
  }

  @Test
  @DisplayName("NamePlaceholders should have folder and file placeholders")
  fun shouldHavePlaceholders() {
    assertThat(NamePlaceholders.folderPlaceholders).isNotEmpty()
    assertThat(NamePlaceholders.filePlaceholders).isNotEmpty()
    assertThat(NamePlaceholders.filePlaceholders).containsKey("{original}")
    assertThat(NamePlaceholders.filePlaceholders).containsKey("{counter}")
    assertThat(NamePlaceholders.filePlaceholders).containsKey("{type}")
    assertThat(NamePlaceholders.filePlaceholders).containsKey("{duration}")
  }

  @Test
  @DisplayName("SidecarExtensions should recognize common sidecars")
  fun shouldRecognizeSidecars() {
    assertThat(SidecarExtensions.isSidecar("xmp")).isTrue()
    assertThat(SidecarExtensions.isSidecar("XMP")).isTrue()
    assertThat(SidecarExtensions.isSidecar("thm")).isTrue()
    assertThat(SidecarExtensions.isSidecar("lrv")).isTrue()
    assertThat(SidecarExtensions.isSidecar("aae")).isTrue()
    assertThat(SidecarExtensions.isSidecar("pp3")).isTrue()
    assertThat(SidecarExtensions.isSidecar("jpg")).isFalse()
    assertThat(SidecarExtensions.isSidecar("png")).isFalse()
    assertThat(SidecarExtensions.isSidecar("")).isFalse()
  }

  @Test
  @DisplayName("DeduplicationSettings should have sensible defaults")
  fun deduplicationSettingsShouldHaveDefaults() {
    val settings = DeduplicationSettings()

    assertThat(settings.enableHashDeduplication).isTrue()
    assertThat(settings.enablePerceptualHash).isTrue()
    assertThat(settings.enableExifDeduplication).isTrue()
    assertThat(settings.enableSurfMatching).isFalse()
    assertThat(settings.surfMatchThreshold).isEqualTo(30)
  }
}
