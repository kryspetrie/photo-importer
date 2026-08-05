package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun SourceDestinationFields(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    sourceValid: Boolean,
    destValid: Boolean,
    destCanCreate: Boolean,
    sourceDirName: String?,
    destDirName: String?,
) {
    val s = strings()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderSelectionField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            modifier = Modifier.weight(1f),
            label = s.t(StringKey.IMPORT_SOURCE_LABEL),
            placeholder = s.t(StringKey.IMPORT_SOURCE_PLACEHOLDER),
            title = s.t(StringKey.ACTION_SELECT_FOLDER),
            isError = sourcePath.isNotBlank() && !sourceValid,
            supportingText = {
                when {
                    sourcePath.isBlank() ->
                        Text(
                            s.t(StringKey.IMPORT_PATH_HINT),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    !sourceValid ->
                        Text(
                            s.t(StringKey.IMPORT_PATH_NOT_FOUND),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    else ->
                        Text(
                            sourceDirName.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                        )
                }
            },
        )

        FolderSelectionField(
            value = destinationPath,
            onValueChange = onDestinationPathChange,
            modifier = Modifier.weight(1f),
            label = s.t(StringKey.IMPORT_DESTINATION_LABEL),
            placeholder = s.t(StringKey.IMPORT_DEST_PLACEHOLDER),
            title = s.t(StringKey.ACTION_SELECT_FOLDER),
            isError = destinationPath.isNotBlank() && !destValid && !destCanCreate,
            supportingText = {
                when {
                    destinationPath.isBlank() ->
                        Text(
                            s.t(StringKey.IMPORT_PATH_HINT),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    !destValid && !destCanCreate ->
                        Text(
                            s.t(StringKey.IMPORT_PATH_NOT_ACCESSIBLE),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    !destValid && destCanCreate ->
                        Text(
                            s.t(StringKey.IMPORT_PATH_WILL_CREATE),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0),
                        )
                    else ->
                        Text(
                            destDirName ?: destinationPath,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                        )
                }
            },
        )
    }
}
