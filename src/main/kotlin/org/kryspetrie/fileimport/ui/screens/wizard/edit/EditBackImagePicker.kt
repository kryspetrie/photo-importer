package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import org.kryspetrie.fileimport.domain.model.AppSettings
import org.kryspetrie.fileimport.domain.model.geometry.BoundingBoxList
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.screens.wizard.BackImagePickerDialog
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState

@Composable
internal fun EditBackImagePickerHost(
    showBackImagePicker: Boolean,
    state: PhotoScanWizardState,
    currentImageFile: File?,
    selectedIndices: Set<Int>,
    boundingBoxList: BoundingBoxList,
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    if (!showBackImagePicker) return

    val backImageBatchFiles =
        remember(state.batch.sourceFiles.value, currentImageFile) {
            val batch = state.batch.sourceFiles.value
            if (batch.isNotEmpty()) {
                batch
            } else {
                val parentDir = currentImageFile?.parentFile
                if (parentDir != null && parentDir.isDirectory) {
                    parentDir
                        .listFiles()
                        ?.filter { it.isFile && isImageFile(it) }
                        ?.sortedBy { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            }
        }
    val preSelectedBackPath =
        remember(
            state.lastBackImageSourcePath.value,
            state.batch.peekNextBatchFile()?.absolutePath,
            currentImageFile?.absolutePath,
            backImageBatchFiles,
        ) {
            state.lastBackImageSourcePath.value
                ?: state.batch.peekNextBatchFile()?.absolutePath
                ?: run {
                    val currentPath = currentImageFile?.absolutePath
                    if (currentPath != null && backImageBatchFiles.isNotEmpty()) {
                        val currentIdx =
                            backImageBatchFiles.indexOfFirst { it.absolutePath == currentPath }
                        if (currentIdx >= 0 && currentIdx + 1 < backImageBatchFiles.size) {
                            backImageBatchFiles[currentIdx + 1].absolutePath
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }

    BackImagePickerDialog(
        batchFiles = backImageBatchFiles.ifEmpty { null },
        preSelectedPath = preSelectedBackPath,
        onConfirm = { sourcePath, cropResult, rotation, mode ->
            val idx = selectedIndices.firstOrNull() ?: return@BackImagePickerDialog
            if (idx < boundingBoxList.size()) {
                val boxId = boundingBoxList.boxes[idx].id
                state.configs.updatePhotoScanConfiguration(boxId) {
                    it.copy(
                        backImageMode = mode,
                        backImageSourcePath = sourcePath,
                        backCropNormalized = cropResult?.toNormalizedList(),
                        backCropRotation = rotation,
                    )
                }
                state.setLastBackImageSourcePath(sourcePath)
                if (settings.autoSkipBackFiles) {
                    state.batch.sourceFiles.value
                        .indexOfFirst { it.absolutePath == sourcePath }
                        .takeIf { it >= 0 }
                        ?.let { state.batch.markBatchIndexSkipped(it) }
                }
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
