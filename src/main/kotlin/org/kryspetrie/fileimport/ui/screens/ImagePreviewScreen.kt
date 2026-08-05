package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------

@Composable
fun ImagePreviewScreen(
    images: List<ImageFile>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    selectedCount: Int,
    viewModel: ImagePreviewViewModel = remember { ImagePreviewViewModel() },
) {
    val s = strings()
    val totalSelectedSize = images.filter { it.isSelected }.sumOf { it.fileSize }
    val density = LocalDensity.current
    val filteredAndSorted =
        remember(
            images,
            viewModel.filterType,
            viewModel.sortMode,
            viewModel.sortAscending,
            viewModel.searchQuery,
        ) {
            viewModel.filteredAndSorted(images)
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ImagePreviewHeader(
                viewModel = viewModel,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
            )
            Spacer(Modifier.height(4.dp))
            FilterAndSortBar(viewModel = viewModel)
            Spacer(Modifier.height(8.dp))
            SelectionStatusBar(
                selectedCount = selectedCount,
                totalImages = images.size,
                filteredCount = filteredAndSorted.size,
                totalSelectedSize = totalSelectedSize,
            )
            Spacer(Modifier.height(8.dp))
            ImageContentArea(
                filteredAndSorted = filteredAndSorted,
                viewModel = viewModel,
                density = density,
                onToggleSelection = onToggleSelection,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(onClick = onBack) { Text(s.t(StringKey.ACTION_BACK)) }
                Button(onClick = onContinue, enabled = selectedCount > 0) {
                    Text(s.t(StringKey.IMPORT_CONTINUE))
                }
            }
        }
        viewModel.fullScreenImage?.let { img ->
            FullScreenOverlay(image = img, onDismiss = { viewModel.fullScreenImage = null })
        }
    }
}

// ---------------------------------------------------------------------------
// Extracted sub-composables
// ---------------------------------------------------------------------------
