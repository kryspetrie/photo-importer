package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * Primary import modes for the Media Import work panel. Designed to wrap in a narrow column as well
 * as a wide action strip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaImportActionBar(canStart: Boolean, onStartFlow: (Boolean, ImportMode) -> Unit) {
    val s = strings()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onStartFlow(false, ImportMode.ALL) },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.IMPORT_ALL))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { onStartFlow(false, ImportMode.NEW) }, enabled = canStart) {
                Icon(Icons.Default.NewReleases, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.t(StringKey.IMPORT_NEW))
            }
            OutlinedButton(
                onClick = { onStartFlow(false, ImportMode.SELECT) },
                enabled = canStart,
            ) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.t(StringKey.IMPORT_SELECT))
            }
            OutlinedButton(onClick = { onStartFlow(true, ImportMode.ALL) }, enabled = canStart) {
                Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.t(StringKey.IMPORT_PREVIEW_FIRST))
            }
        }
    }
}
