package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A consistent path selection field with an inline folder browse button.
 *
 * Uses a [OutlinedTextField] with a folder icon leading, a create-folder browse button trailing,
 * and consistent styling across the app.
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
            IconButton(onClick = { pickFolder(title)?.let(onValueChange) }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = "Browse",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        supportingText = supportingText,
    )
}

/**
 * A consistent path selection field with an inline file browse button.
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
            IconButton(onClick = { pickFile(title, extensionFilter)?.let(onValueChange) }) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = "Browse",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        supportingText = supportingText,
    )
}

/**
 * A consistent source path field that supports both file and folder selection.
 *
 * For cases where the user can select either a file (e.g., a single image) or a folder (e.g., a
 * folder of images). Provides two trailing action buttons — one for file browsing and one for
 * folder browsing.
 *
 * @param value The current path text
 * @param onValueChange Callback when the path changes
 * @param onPickFile Called with the file picker title, should invoke [pickFile] or [pickImageFile]
 *   and call [onValueChange] with the result.
 * @param onPickFolder Called with the folder picker title, should invoke [pickFolder] and call
 *   [onValueChange] with the result.
 * @param modifier Optional modifier
 * @param label The field label
 * @param placeholder Placeholder text when empty
 * @param isError Whether the field should display an error state
 * @param supportingText Optional supporting text below the field
 * @param leadingIcon Optional leading icon (default: [Icons.Default.Image])
 * @param fileIcon Icon for the file browse button (default:
 *   [Icons.AutoMirrored.Filled.InsertDriveFile])
 * @param folderIcon Icon for the folder browse button (default: [Icons.Default.FolderOpen])
 */
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
                IconButton(onClick = onPickFile) {
                    Icon(
                        fileIcon,
                        contentDescription = "Select File",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onPickFolder) {
                    Icon(
                        folderIcon,
                        contentDescription = "Select Folder",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        supportingText = supportingText,
    )
}
