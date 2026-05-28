package org.kryspetrie.fileimport.ui.screens.reorganize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.ui.screens.ReorganizeViewModel

/**
 * Bottom action bar for the reorganize screen.
 *
 * Shows contextual action buttons (Preview Changes, Back, Apply Changes) based on the current step.
 * Extracted from [ReorganizeScreen] to reduce method length and complexity.
 */
@Composable
fun ReorganizeActionBar(
    step: ReorganizeViewModel.ReorgStep,
    canPreview: Boolean,
    changeCount: Int,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
) {
    if (
        step == ReorganizeViewModel.ReorgStep.SETUP || step == ReorganizeViewModel.ReorgStep.PREVIEW
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step == ReorganizeViewModel.ReorgStep.PREVIEW) {
                OutlinedButton(onClick = onBack, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Back")
                }
            }
            if (step == ReorganizeViewModel.ReorgStep.SETUP) {
                Button(onClick = onPreview, enabled = canPreview) {
                    Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Preview Changes")
                }
            }
            if (step == ReorganizeViewModel.ReorgStep.PREVIEW && changeCount > 0) {
                Button(onClick = onApply) {
                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply $changeCount Changes")
                }
            }
        }
    }
}
