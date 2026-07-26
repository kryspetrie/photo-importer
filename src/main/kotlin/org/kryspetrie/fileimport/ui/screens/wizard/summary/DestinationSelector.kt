package org.kryspetrie.fileimport.ui.screens.wizard.summary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.i18n.strings

/** Selector for the export destination folder with a browse button in the field. */
@Composable
fun DestinationSelector(
    destination: String,
    onDestinationChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    FolderSelectionField(
        value = destination,
        onValueChange = onDestinationChange,
        modifier = modifier.fillMaxWidth(),
        label = s.t(StringKey.WIZARD_EXPORT_DESTINATION),
        placeholder = s.t(StringKey.IMPORT_DEST_PLACEHOLDER),
        title = s.t(StringKey.WIZARD_SELECT_EXPORT_DEST),
        supportingText = {
            if (destination.isNotBlank()) {
                Text(destination, style = MaterialTheme.typography.labelSmall)
            } else {
                Text(s.t(StringKey.WIZARD_BROWSE_OR_TYPE), style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
