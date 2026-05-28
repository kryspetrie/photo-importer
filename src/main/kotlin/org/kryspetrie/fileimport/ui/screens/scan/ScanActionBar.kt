package org.kryspetrie.fileimport.ui.screens.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScanActionBar(
    canNavigateNext: Boolean,
    onRedetect: () -> Unit,
    onAddPhoto: () -> Unit,
    onSkip: () -> Unit,
    onExportAll: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(8.dp),
    ) {
        OutlinedButton(onClick = onRedetect, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Repeat, "Re-detect")
            Text("Re-detect")
        }
        OutlinedButton(onClick = onAddPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, "Add")
            Text("Add Photo")
        }
        OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
        OutlinedButton(onClick = onExportAll, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Upload, "Export")
            Text("Export All")
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        OutlinedButton(
            onClick = onNext,
            enabled = canNavigateNext,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.NavigateNext, "Next")
            Text("Next")
        }
    }
}
