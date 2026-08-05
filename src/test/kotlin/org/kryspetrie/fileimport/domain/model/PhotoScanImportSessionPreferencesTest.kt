package org.kryspetrie.fileimport.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PhotoScanImportSessionPreferences")
class PhotoScanImportSessionPreferencesTest {

    @Test
    fun defaultsMatchImportSettingsState() {
        val prefs = PhotoScanImportSessionPreferences()
        assertThat(prefs.cvAutoDetectEnabled).isTrue()
        assertThat(prefs.singlePhotoMode).isFalse()
        assertThat(prefs.settingsExpanded).isFalse()
        assertThat(prefs.perspectiveCorrectionEnabled).isTrue()
        assertThat(prefs.exportMarginPercent).isEqualTo(0.02)
        assertThat(prefs.defaultCorrectionStrategy).isEqualTo(CorrectionStrategy.PERSPECTIVE)
        assertThat(prefs.skipCropAndRotate).isFalse()
        assertThat(prefs.autoSkipBackFiles).isTrue()
    }

    @Test
    fun normalizedExportMarginPercentClampsOutOfRange() {
        val prefs = PhotoScanImportSessionPreferences(exportMarginPercent = 0.5)
        assertThat(prefs.normalizedExportMarginPercent()).isEqualTo(0.2)
    }

    @Test
    fun survivesAppSettingsJsonRoundTrip() {
        val original =
            AppSettings(
                photoScanImportSessionPreferences =
                    PhotoScanImportSessionPreferences(
                        cvAutoDetectEnabled = false,
                        singlePhotoMode = true,
                        settingsExpanded = true,
                        perspectiveCorrectionEnabled = false,
                        exportMarginPercent = 0.05,
                        defaultCorrectionStrategy = CorrectionStrategy.CROP,
                        skipCropAndRotate = true,
                        autoSkipBackFiles = false,
                    )
            )
        val jsonCodec = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val restored =
            jsonCodec.decodeFromString(
                AppSettings.serializer(),
                jsonCodec.encodeToString(AppSettings.serializer(), original),
            )

        assertThat(restored.photoScanImportSessionPreferences.cvAutoDetectEnabled).isFalse()
        assertThat(restored.photoScanImportSessionPreferences.singlePhotoMode).isTrue()
        assertThat(restored.photoScanImportSessionPreferences.settingsExpanded).isTrue()
        assertThat(restored.photoScanImportSessionPreferences.perspectiveCorrectionEnabled)
            .isFalse()
        assertThat(restored.photoScanImportSessionPreferences.exportMarginPercent).isEqualTo(0.05)
        assertThat(restored.photoScanImportSessionPreferences.defaultCorrectionStrategy)
            .isEqualTo(CorrectionStrategy.CROP)
        assertThat(restored.photoScanImportSessionPreferences.skipCropAndRotate).isTrue()
        assertThat(restored.photoScanImportSessionPreferences.autoSkipBackFiles).isFalse()
    }
}

@DisplayName("AppSettings lastAppTab")
class AppSettingsLastTabTest {

    @Test
    fun defaultsToPhotoScanTab() {
        assertThat(AppSettings().lastAppTab).isEqualTo("PHOTO_SCAN")
    }

    @Test
    fun withLastAppTabUpdatesValue() {
        val updated = AppSettings().withLastAppTab("MEDIA_IMPORT")
        assertThat(updated.lastAppTab).isEqualTo("MEDIA_IMPORT")
    }
}
