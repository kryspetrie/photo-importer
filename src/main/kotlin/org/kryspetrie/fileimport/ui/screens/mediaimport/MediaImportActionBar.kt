package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhotoLibrary
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
import org.kryspetrie.fileimport.domain.model.ImportMode
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun MediaImportActionBar(
    canStart: Boolean,
    importMode: ImportMode,
    onImportModeChange: (ImportMode) -> Unit,
    onStartFlow: (Boolean, ImportMode) -> Unit,
) {
    val s = strings()
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onStartFlow(false, ImportMode.ALL) }, enabled = canStart) {
            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.IMPORT_ALL))
        }
        OutlinedButton(onClick = { onStartFlow(false, ImportMode.NEW) }, enabled = canStart) {
            Icon(Icons.Default.NewReleases, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.IMPORT_NEW))
        }
        OutlinedButton(onClick = { onStartFlow(false, ImportMode.SELECT) }, enabled = canStart) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.IMPORT_SELECT))
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { onStartFlow(true, importMode) }, enabled = canStart) {
            Icon(Icons.Default.Preview, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(s.t(StringKey.IMPORT_PREVIEW_FIRST))
        }
    }
}
