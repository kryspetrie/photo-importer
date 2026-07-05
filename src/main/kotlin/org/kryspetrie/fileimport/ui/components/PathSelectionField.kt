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

/**
 * A consistent path selection field with an inline folder browse icon button.
 *
 * Uses a [OutlinedTextField] with a folder icon leading and an icon-only browse button trailing,
 * with a hover tooltip for accessibility.
 *
 * @param value The current folder path text
 * @param onValueChange Callback when the path changes (from typing or picker selection)
 * @param modifier Optional modifier
 * @param label The field label (default "Folder")
 * @param placeholder Placeholder text when empty
 * @param title Title for the folder picker dialog
 * @param isError Whether the field should display an error state
 * @param supportingText Optional supporting text below the field
 * @param leadingIcon Optional leading icon (default: [Icons.Default.FolderOpen])
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Folder",
    placeholder: String = "Select folder...",
    title: String = "Select Folder",
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Default.FolderOpen,
) {
    val tooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(leadingIcon, contentDescription = "Folder", modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Select Folder") } },
                state = tooltipState,
            ) {
                IconButton(onClick = { pickFolder(title)?.let(onValueChange) }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Select Folder")
                }
            }
        },
        supportingText = supportingText,
    )
}

/**
 * A consistent path selection field with an inline file browse icon button.
 *
 * @param value The current file path text
 * @param onValueChange Callback when the path changes (from typing or picker selection)
 * @param modifier Optional modifier
 * @param label The field label (default "File")
 * @param placeholder Placeholder text when empty
 * @param title Title for the file picker dialog
 * @param extensionFilter Optional list of extensions to filter (e.g. listOf("jpg", "png"))
 * @param isError Whether the field should display an error state
 * @param supportingText Optional supporting text below the field
 * @param leadingIcon Optional leading icon (default: [Icons.AutoMirrored.Filled.InsertDriveFile])
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "File",
    placeholder: String = "Select file...",
    title: String = "Select File",
    extensionFilter: List<String>? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
) {
    val tooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(leadingIcon, contentDescription = "File", modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Select File") } },
                state = tooltipState,
            ) {
                IconButton(onClick = { pickFile(title, extensionFilter)?.let(onValueChange) }) {
                    Icon(leadingIcon, contentDescription = "Select File")
                }
            }
        },
        supportingText = supportingText,
    )
}

/**
 * A consistent source path field that supports both file and folder selection.
 *
 * For cases where the user can select either a file (e.g., a single image) or a folder (e.g., a
 * folder of images). Provides two icon-only trailing buttons with hover tooltips — file picker and
 * folder picker.
 *
 * @param value The current path text
 * @param onValueChange Callback when the path changes
 * @param onPickFile Called when the user clicks the file browse icon.
 * @param onPickFolder Called when the user clicks the folder browse icon.
 * @param modifier Optional modifier
 * @param label The field label
 * @param placeholder Placeholder text when empty
 * @param isError Whether the field should display an error state
 * @param supportingText Optional supporting text below the field
 * @param leadingIcon Optional leading icon (default: [Icons.Default.Image])
 * @param fileIcon Icon for the file browse button (default:
 *   [Icons.AutoMirrored.Filled.InsertDriveFile])
 * @param folderIcon Icon for the folder browse button (default: [Icons.Default.CreateNewFolder])
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SourcePathField(
    value: String,
    onValueChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Source",
    placeholder: String = "Select file or folder...",
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Default.Image,
    fileIcon: ImageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
    folderIcon: ImageVector = Icons.Default.CreateNewFolder,
) {
    val fileTooltipState = rememberTooltipState()
    val folderTooltipState = rememberTooltipState()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        isError = isError,
        leadingIcon = {
            Icon(leadingIcon, contentDescription = "Source", modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            Row {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Select File") } },
                    state = fileTooltipState,
                ) {
                    IconButton(onClick = onPickFile) {
                        Icon(fileIcon, contentDescription = "Select File")
                    }
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Select Folder") } },
                    state = folderTooltipState,
                ) {
                    IconButton(onClick = onPickFolder) {
                        Icon(folderIcon, contentDescription = "Select Folder")
                    }
                }
            }
        },
        supportingText = supportingText,
    )
}