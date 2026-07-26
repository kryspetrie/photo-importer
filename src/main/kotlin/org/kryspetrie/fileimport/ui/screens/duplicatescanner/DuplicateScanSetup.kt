package org.kryspetrie.fileimport.ui.screens.duplicatescanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun DuplicateScanSetup(
    folderPath: String,
    onFolderPathChange: (String) -> Unit,
    enableHash: Boolean,
    onEnableHashChange: (Boolean) -> Unit,
    enableExif: Boolean,
    onEnableExifChange: (Boolean) -> Unit,
    enableSurf: Boolean,
    onEnableSurfChange: (Boolean) -> Unit,
    errorMessage: String?,
) {
    val s = strings()

    FolderSelectionField(
        value = folderPath,
        onValueChange = onFolderPathChange,
        modifier = Modifier.fillMaxWidth(),
        label = s.t(StringKey.DUP_LIBRARY_FOLDER),
        placeholder = s.t(StringKey.DUP_LIBRARY_PLACEHOLDER),
        title = s.t(StringKey.ACTION_SELECT_FOLDER),
        supportingText = {
            Text(s.t(StringKey.IMPORT_PATH_HINT), style = MaterialTheme.typography.labelSmall)
        },
    )

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                s.t(StringKey.DUP_DETECTION_METHODS),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = enableHash,
                        onCheckedChange = onEnableHashChange,
                        label = s.t(StringKey.DUP_EXACT_HASH),
                        description = s.t(StringKey.DUP_MD5_MATCH),
                    )
                }
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = enableExif,
                        onCheckedChange = onEnableExifChange,
                        label = s.t(StringKey.DUP_EXIF_METADATA),
                        description = s.t(StringKey.DUP_COMPARE_EXIF),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    SettingsToggle(
                        checked = enableSurf,
                        onCheckedChange = onEnableSurfChange,
                        label = s.t(StringKey.DUP_SURF_VISUAL),
                        description = s.t(StringKey.DUP_NEAR_DUPLICATES),
                    )
                }
            }
        }
    }

    errorMessage?.let {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Warning,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
