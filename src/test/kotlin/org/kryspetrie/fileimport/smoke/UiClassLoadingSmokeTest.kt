package org.kryspetrie.fileimport.smoke

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Loads classes that buttons open (dialogs, overlays, pickers, screens) so [ClassNotFoundException]
 * / [NoClassDefFoundError] surfaces in CI instead of only when a user clicks.
 */
@DisplayName("UI class loading smoke")
@Tag("Smoke")
class UiClassLoadingSmokeTest {

    @Test
    fun `critical UI types used by button paths are loadable`() {
        val names =
            listOf(
                // Photo scan / edit
                "org.kryspetrie.fileimport.ui.screens.wizard.BackImageCropResult",
                "org.kryspetrie.fileimport.ui.screens.wizard.QuadCorners",
                "org.kryspetrie.fileimport.ui.screens.wizard.BackImageInteractionMode",
                "org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialogKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.BackImageCropGeometryKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.BackImageCanvasKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.edit.EditBackImagePickerKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.edit.EditLocationPickerKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.edit.EditDialogsKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.edit.EditScreenChromeKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorOverlayKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorNamingBarKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.FaceSelectorCanvasKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.KeyboardShortcutsKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.ExportSettingsCardKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.CompletionScreenKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.OverviewScreenKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.EditScreenKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.SummaryScreenKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.WizardContainerKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.PhotoScanImportScreenKt",
                // Location picker lives in LocationPickerDialog.kt
                "org.kryspetrie.fileimport.ui.screens.wizard.metadata.LocationPickerDialogKt",
                "org.kryspetrie.fileimport.ui.screens.wizard.metadata.MapTileRenderer",
                // Package declaration is wizard (not summary) for this file
                "org.kryspetrie.fileimport.ui.screens.wizard.SummaryFullscreenPreviewDialogKt",
                // Metadata editor
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.BulkSelectionDialogKt",
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.ModelDownloadDialogKt",
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.RotationPreviewOverlayKt",
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorScreenKt",
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorFileBrowserPanelKt",
                "org.kryspetrie.fileimport.ui.screens.metadataeditor.MetadataEditorCommandBarKt",
                // Media import
                "org.kryspetrie.fileimport.ui.screens.mediaimport.WatchFolderConfigDialogKt",
                "org.kryspetrie.fileimport.ui.screens.mediaimport.ClearCacheConfirmDialogKt",
                "org.kryspetrie.fileimport.ui.screens.mediaimport.MediaImportDialogsKt",
                "org.kryspetrie.fileimport.ui.screens.MediaImportScreenKt",
                "org.kryspetrie.fileimport.ui.screens.PreviewStructureScreenKt",
                // Reorganize / duplicates
                "org.kryspetrie.fileimport.ui.screens.ReorganizeScreenKt",
                "org.kryspetrie.fileimport.ui.screens.DuplicateScannerScreenKt",
                "org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateResolveConfirmDialogKt",
                "org.kryspetrie.fileimport.ui.screens.duplicatescanner.DuplicateGroupCardKt",
                // Shared (CenteredContentPane is defined in SharedUi.kt)
                "org.kryspetrie.fileimport.ui.components.SharedUiKt",
                "org.kryspetrie.fileimport.ui.components.FileDialogsKt",
                "org.kryspetrie.fileimport.ui.components.ChunkyScrollbarKt",
                "org.kryspetrie.fileimport.ui.PetrieFileImporterAppKt",
            )
        assertClassesLoadable(names)
    }

    @Test
    fun `all non-nested application classes under ui package load`() {
        val classesRoot = Path.of("build/classes/kotlin/main/org/kryspetrie/fileimport/ui")
        assertTrue(Files.isDirectory(classesRoot), "UI classes root missing: $classesRoot")
        val names = mutableListOf<String>()
        Files.walk(classesRoot).use { stream ->
            stream
                .filter { it.toString().endsWith(".class") && !it.fileName.toString().contains("$") }
                .forEach { path ->
                    val s = path.toAbsolutePath().toString()
                    val marker = "/classes/kotlin/main/"
                    val idx = s.indexOf(marker)
                    if (idx < 0) return@forEach
                    names +=
                        s.substring(idx + marker.length).removeSuffix(".class").replace('/', '.')
                }
        }
        assertTrue(names.size > 50, "expected many UI classes, found ${names.size}")
        assertClassesLoadable(names)
    }

    @Test
    fun `button related domain and application export types load`() {
        // Types commonly linked from save/export/scan action handlers
        val names =
            listOf(
                "org.kryspetrie.fileimport.application.export.PhotoScanMetadataMapper",
                "org.kryspetrie.fileimport.application.export.MetadataWritingService",
                "org.kryspetrie.fileimport.application.export.FileFormatSupport",
                "org.kryspetrie.fileimport.domain.model.PhotoScanConfiguration",
                "org.kryspetrie.fileimport.domain.model.AppSettings",
            )
        assertClassesLoadable(names)
    }

    private fun assertClassesLoadable(names: Collection<String>) {
        val failures = mutableListOf<String>()
        for (name in names) {
            try {
                Class.forName(name)
            } catch (e: Throwable) {
                var root: Throwable = e
                while (root.cause != null && root.cause !== root) {
                    root = root.cause!!
                }
                failures.add(
                    "$name: ${e.javaClass.simpleName}: ${e.message}" +
                        if (root !== e) " (root: ${root.javaClass.simpleName}: ${root.message})"
                        else ""
                )
            }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
