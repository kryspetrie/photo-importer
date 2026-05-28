package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.BoundingBoxList
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoConfiguration
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState

/** Left panel containing bulk actions, destination selector, and scrollable photo list. */
@Composable
fun PhotoListPanel(
    state: PhotoScanWizardState,
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedPreviewIndex: Int,
    onSelectedPreviewIndexChange: (Int) -> Unit,
    exportDestination: String,
    onDestinationChange: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        BulkActionButtons(
            onRotateAllCW = { state.rotateAllBoxesCW() },
            onRotateAllCCW = { state.rotateAllBoxesCCW() },
            onClearAll = { state.clearAllConfigurations() },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        DestinationSelector(
            destination = exportDestination,
            onDestinationChange = { onDestinationChange?.invoke(it) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        PhotoLazyList(
            boundingBoxList = boundingBoxList,
            photoConfigurations = photoConfigurations,
            selectedPreviewIndex = selectedPreviewIndex,
            onSelectedPreviewIndexChange = onSelectedPreviewIndexChange,
            state = state,
        )
    }
}

@Composable
private fun ColumnScope.PhotoLazyList(
    boundingBoxList: BoundingBoxList,
    photoConfigurations: Map<String, PhotoConfiguration>,
    selectedPreviewIndex: Int,
    onSelectedPreviewIndexChange: (Int) -> Unit,
    state: PhotoScanWizardState,
) {
    LazyColumn(
        modifier =
            Modifier.weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        itemsIndexed(boundingBoxList.boxes) { index, box ->
            PhotoSummaryCard(
                box = box,
                index = index,
                isSelected = index == selectedPreviewIndex,
                config = photoConfigurations[box.id] ?: PhotoConfiguration(),
                onSelect = { onSelectedPreviewIndexChange(index) },
                onConfigChange = { config -> state.setPhotoConfiguration(box.id, config) },
                onDelete = {
                    state.removeBox(index)
                    val newSize = boundingBoxList.size()
                    if (selectedPreviewIndex >= newSize) {
                        onSelectedPreviewIndexChange(maxOf(0, newSize - 1))
                    }
                },
            )
        }
    }
}
