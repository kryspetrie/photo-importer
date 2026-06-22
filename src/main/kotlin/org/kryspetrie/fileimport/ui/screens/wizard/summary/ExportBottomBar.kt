package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Bottom bar with photo count, back button, next button, and optional "Export Now" button. */
@Composable
fun ExportBottomBar(
    photoCount: Int,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipMetadata: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$photoCount ${if (photoCount == 1) "photo" else "photos"} ready", style = MaterialTheme.typography.bodyMedium)
            ExportButtons(
                onBack = onBack,
                onExport = onExport,
                onSkipMetadata = onSkipMetadata,
                enabled = photoCount > 0,
            )
        }
    }
}

@Composable
private fun ExportButtons(
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipMetadata: (() -> Unit)?,
    enabled: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.height(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Back")
        }
        if (onSkipMetadata != null) {
            OutlinedButton(
                onClick = onSkipMetadata,
                enabled = enabled,
                modifier = Modifier.height(40.dp),
            ) {
                Text("Export Now")
            }
        }
        Button(onClick = onExport, enabled = enabled, modifier = Modifier.height(40.dp)) {
            Text("Next")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}
