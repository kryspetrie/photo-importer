package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.CircularSpinner
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DuplicateScanningProgress(phase: String, current: Int, total: Int) {
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                Text(
                    phase.ifBlank { s.t(StringKey.DUP_SCANNING) },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (total > 0) {
                Text(
                    s.t(
                        StringKey.META_STATUS_PROGRESS,
                        "index" to current.toString(),
                        "total" to total.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
fun DuplicateResolvingProgress(current: Int, total: Int) {
    val s = strings()

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                Text(s.t(StringKey.DUP_RESOLVING), style = MaterialTheme.typography.titleSmall)
            }
            if (total > 0) {
                Text(
                    s.t(
                        StringKey.META_STATUS_PROGRESS,
                        "index" to current.toString(),
                        "total" to total.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { current.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
