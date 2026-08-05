package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

/**
 * A consistent path selection field with an inline folder browse icon button.
 *
 * Uses a [OutlinedTextField] with a folder icon leading and an icon-only browse button trailing,
 * with a hover tooltip for accessibility.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    title: String? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Default.FolderOpen,
) {
    val s = strings()
    val resolvedLabel = label ?: s.t(StringKey.ACC_FOLDER)
    val resolvedPlaceholder = placeholder ?: s.t(StringKey.ACTION_SELECT_FOLDER)
    val resolvedTitle = title ?: s.t(StringKey.ACTION_SELECT_FOLDER)
    val selectFolderLabel = s.t(StringKey.ACTION_SELECT_FOLDER)
    val tooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(resolvedLabel) },
        placeholder = { Text(resolvedPlaceholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = s.t(StringKey.ACC_FOLDER),
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(selectFolderLabel) } },
                state = tooltipState,
            ) {
                IconButton(onClick = { pickFolder(resolvedTitle)?.let(onValueChange) }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = selectFolderLabel)
                }
            }
        },
        supportingText = supportingText,
    )
}

/** A consistent path selection field with an inline file browse icon button. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    title: String? = null,
    extensionFilter: List<String>? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
) {
    val s = strings()
    val resolvedLabel = label ?: s.t(StringKey.ACC_FILE)
    val resolvedPlaceholder = placeholder ?: s.t(StringKey.ACTION_SELECT_FILE)
    val resolvedTitle = title ?: s.t(StringKey.ACTION_SELECT_FILE)
    val selectFileLabel = s.t(StringKey.ACTION_SELECT_FILE)
    val tooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(resolvedLabel) },
        placeholder = { Text(resolvedPlaceholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = s.t(StringKey.ACC_FILE),
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(selectFileLabel) } },
                state = tooltipState,
            ) {
                IconButton(
                    onClick = { pickFile(resolvedTitle, extensionFilter)?.let(onValueChange) }
                ) {
                    Icon(leadingIcon, contentDescription = selectFileLabel)
                }
            }
        },
        supportingText = supportingText,
    )
}

/** A consistent source path field that supports both file and folder selection. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SourcePathField(
    value: String,
    onValueChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Default.Image,
    fileIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
    folderIcon: ImageVector = Icons.Default.CreateNewFolder,
) {
    val s = strings()
    val resolvedLabel = label ?: s.t(StringKey.META_SOURCE_LABEL)
    val resolvedPlaceholder = placeholder ?: s.t(StringKey.META_SOURCE_PLACEHOLDER)
    val selectFileLabel = s.t(StringKey.ACTION_SELECT_FILE)
    val selectFolderLabel = s.t(StringKey.ACTION_SELECT_FOLDER)
    val fileTooltipState = rememberTooltipState()
    val folderTooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(resolvedLabel) },
        placeholder = { Text(resolvedPlaceholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(
                leadingIcon,
                contentDescription = s.t(StringKey.META_SOURCE_LABEL),
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            Row {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(selectFileLabel) } },
                    state = fileTooltipState,
                ) {
                    IconButton(onClick = onPickFile) {
                        Icon(fileIcon, contentDescription = selectFileLabel)
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(selectFolderLabel) } },
                    state = folderTooltipState,
                ) {
                    IconButton(onClick = onPickFolder) {
                        Icon(folderIcon, contentDescription = selectFolderLabel)
                    }
                }
            }
        },
        supportingText = supportingText,
    )
}
