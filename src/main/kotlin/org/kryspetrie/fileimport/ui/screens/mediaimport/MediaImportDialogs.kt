package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.kryspetrie.fileimport.domain.model.DuplicateInfo
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.ui.screens.DuplicateReviewScreen
import org.kryspetrie.fileimport.ui.screens.ImagePreviewScreen
import org.kryspetrie.fileimport.ui.screens.PreviewStructureScreen

@Composable
fun ImageSelectionDialog(
    images: List<ImageFile>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedCount: Int,
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxSize(0.95f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            ImagePreviewScreen(
                images = images,
                onToggleSelection = onToggleSelection,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
                onContinue = onContinue,
                onBack = onBack,
                selectedCount = selectedCount,
            )
        }
    }
}

@Composable
fun DuplicateReviewDialog(
    duplicates: List<DuplicateInfo>,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize(0.95f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            DuplicateReviewScreen(
                duplicates = duplicates,
                onResolution = { _, _ -> },
                onContinue = onContinue,
                onBack = onBack,
            )
        }
    }
}

@Composable
fun PreviewStructureDialog(
    images: List<ImageFile>,
    sourcePath: String,
    destinationPath: String,
    configuration: ImportConfiguration,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxSize(0.95f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            PreviewStructureScreen(
                images = images,
                sourcePath = sourcePath,
                destinationPath = destinationPath,
                configuration = configuration,
                onImport = onImport,
                onBack = onBack,
            )
        }
    }
}
