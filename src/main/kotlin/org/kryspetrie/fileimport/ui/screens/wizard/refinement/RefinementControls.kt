package org.kryspetrie.fileimport.ui.screens.wizard.refinement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.Corner

@Composable
internal fun RefinementControls(
    currentIndex: Int,
    totalCount: Int,
    selectedCorner: Corner?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDeselect: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Navigation: ← Image N of M →
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = totalCount > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous", Modifier.size(18.dp))
                }
                Text(
                    "Photo ${currentIndex + 1} of $totalCount",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = onNext,
                    enabled = totalCount > 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", Modifier.size(18.dp))
                }
            }

            // Corner selection info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedCorner != null) {
                    Text(
                        "Selected: ${selectedCorner.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = onDeselect, modifier = Modifier.height(32.dp)) {
                        Text("Deselect", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        "Click a corner to select",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
