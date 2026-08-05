package org.kryspetrie.fileimport.ui.screens.reorganize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.ReorganizeViewModel
import org.kryspetrie.fileimport.ui.theme.DefaultSpacing

/** Primary run controls for Reorganize; rendered inside the shared work panel. */
@Composable
fun ReorganizeActionBar(
    step: ReorganizeViewModel.ReorgStep,
    canPreview: Boolean,
    changeCount: Int,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
) {
    val s = strings()
    if (
        step != ReorganizeViewModel.ReorgStep.SETUP &&
            step != ReorganizeViewModel.ReorgStep.PREVIEW
    ) {
        return
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (step == ReorganizeViewModel.ReorgStep.PREVIEW) {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(s.t(StringKey.ACTION_BACK))
            }
        }
        if (step == ReorganizeViewModel.ReorgStep.SETUP) {
            Button(
                onClick = onPreview,
                enabled = canPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Preview, null, Modifier.size(DefaultSpacing.iconMedium))
                Spacer(Modifier.width(DefaultSpacing.sm + DefaultSpacing.xs))
                Text(s.t(StringKey.REORG_PREVIEW_CHANGES))
            }
        }
        if (step == ReorganizeViewModel.ReorgStep.PREVIEW && changeCount > 0) {
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    null,
                    Modifier.size(DefaultSpacing.iconMedium),
                )
                Spacer(Modifier.width(DefaultSpacing.sm + DefaultSpacing.xs))
                Text(s.t(StringKey.REORG_APPLY_CHANGES, "count" to "$changeCount"))
            }
        }
    }
}
