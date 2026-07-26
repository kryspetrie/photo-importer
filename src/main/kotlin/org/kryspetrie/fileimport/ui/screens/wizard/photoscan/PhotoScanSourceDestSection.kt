package org.kryspetrie.fileimport.ui.screens.wizard.photoscan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.components.SourcePathField
import org.kryspetrie.fileimport.ui.components.isImageFile
import org.kryspetrie.fileimport.ui.components.pickFolder
import org.kryspetrie.fileimport.ui.components.pickImageFile
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun ScanModeCard(
    cvAutoDetectEnabled: Boolean,
    onCvAutoDetectChange: (Boolean) -> Unit,
    singlePhotoMode: Boolean,
    onSinglePhotoModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = cvAutoDetectEnabled,
                    onCheckedChange = onCvAutoDetectChange,
                    label = s.t(StringKey.WIZARD_AUTO_DETECT),
                    description = s.t(StringKey.WIZARD_AUTO_DETECT_DESC),
                    icon = Icons.Default.AutoAwesome,
                )
            }
            Column(Modifier.weight(1f)) {
                SettingsToggle(
                    checked = singlePhotoMode,
                    onCheckedChange = onSinglePhotoModeChange,
                    label = s.t(StringKey.WIZARD_SINGLE_PHOTO),
                    description = s.t(StringKey.WIZARD_SINGLE_PHOTO_DESC),
                    icon = Icons.Default.PhotoCamera,
                )
            }
        }
    }
}

@Composable
fun SourceDestRow(
    sourcePath: String,
    onSourcePathChange: (String) -> Unit,
    sourceFile: File?,
    destinationPath: String,
    onDestinationPathChange: (String) -> Unit,
    destValid: Boolean,
    destCanCreate: Boolean = false,
    destDirName: String?,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SourcePathField(
            value = sourcePath,
            onValueChange = onSourcePathChange,
            onPickFile = {
                pickImageFile(s.t(StringKey.META_DIALOG_SELECT_IMAGE))?.let(onSourcePathChange)
            },
            onPickFolder = {
                pickFolder(s.t(StringKey.META_DIALOG_SELECT_FOLDER))?.let(onSourcePathChange)
            },
            modifier = Modifier.weight(1f),
            label = s.t(StringKey.META_SOURCE_LABEL),
            placeholder = s.t(StringKey.META_SOURCE_PLACEHOLDER),
            isError = sourcePath.isNotBlank() && sourceFile == null,
            supportingText = {
                when {
                    sourcePath.isBlank() -> Text(s.t(StringKey.WIZARD_FILE_OR_FOLDER))
                    sourceFile == null ->
                        Text(s.t(StringKey.IMPORT_PATH_NOT_FOUND), color = MaterialTheme.colorScheme.error)
                    sourceFile.isDirectory -> {
                        val imageCount =
                            sourceFile.listFiles { f -> f.isFile && isImageFile(f) }?.size ?: 0
                        Text(s.t(StringKey.WIZARD_IMAGE_COUNT, "count" to "$imageCount"))
                    }
                    else -> Text(sourceFile.name)
                }
            },
        )

        FolderSelectionField(
            value = destinationPath,
            onValueChange = onDestinationPathChange,
            modifier = Modifier.weight(1f),
            label = s.t(StringKey.IMPORT_DESTINATION_LABEL),
            placeholder = s.t(StringKey.IMPORT_DEST_PLACEHOLDER),
            title = s.t(StringKey.META_DIALOG_SELECT_OUTPUT),
            isError = destinationPath.isNotBlank() && !destValid && !destCanCreate,
            supportingText = {
                when {
                    destinationPath.isBlank() -> Text(s.t(StringKey.IMPORT_PATH_HINT))
                    !destValid && !destCanCreate ->
                        Text(s.t(StringKey.IMPORT_PATH_NOT_ACCESSIBLE), color = MaterialTheme.colorScheme.error)
                    !destValid && destCanCreate ->
                        Text(s.t(StringKey.IMPORT_PATH_WILL_CREATE), color = MaterialTheme.colorScheme.primary)
                    else -> Text(destDirName.orEmpty())
                }
            },
        )
    }
}
