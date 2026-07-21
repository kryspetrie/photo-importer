package org.kryspetrie.fileimport.ui.screens.mediaimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.WatchFolderConfig

/**
 * Dialog for adding or editing a watch folder configuration.
 *
 * @param existingConfig If non-null, editing an existing config; if null, creating a new one.
 * @param onSave Called with the completed config when the user confirms.
 * @param onDismiss Called when the user cancels.
 */
@Composable
fun WatchFolderConfigDialog(
    existingConfig: WatchFolderConfig?,
    onSave: (WatchFolderConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var watchPath by remember { mutableStateOf(existingConfig?.watchPath ?: "") }
    var destinationPath by remember { mutableStateOf(existingConfig?.destinationPath ?: "") }
    var cooldownSeconds by remember { mutableStateOf((existingConfig?.cooldownMs ?: 5000L) / 1000f) }
    var recursive by remember { mutableStateOf(existingConfig?.recursive ?: true) }
    var autoStart by remember { mutableStateOf(existingConfig?.autoStart ?: false) }
    var enabled by remember { mutableStateOf(existingConfig?.enabled ?: true) }
    var profileName by remember { mutableStateOf(existingConfig?.profileName ?: "") }

    val isValid = watchPath.isNotBlank() && destinationPath.isNotBlank()
    val isEditing = existingConfig != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (isEditing) "Edit Watch Folder" else "Add Watch Folder",
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = watchPath,
                    onValueChange = { watchPath = it },
                    label = { Text("Source folder to watch") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = destinationPath,
                    onValueChange = { destinationPath = it },
                    label = { Text("Destination folder for imports") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Cooldown slider: 1-30 seconds
                Text(
                    "Cooldown: ${cooldownSeconds.toInt()} seconds",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = cooldownSeconds,
                    onValueChange = { cooldownSeconds = it },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Wait time after last file change before triggering import",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = recursive,
                        onCheckedChange = { recursive = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Watch subdirectories recursively")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { autoStart = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-start on app launch")
                }

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Enabled")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                (existingConfig ?: WatchFolderConfig(
                                    watchPath = watchPath,
                                    destinationPath = destinationPath,
                                )).copy(
                                    watchPath = watchPath,
                                    destinationPath = destinationPath,
                                    profileName = profileName,
                                    cooldownMs = (cooldownSeconds * 1000).toLong(),
                                    recursive = recursive,
                                    autoStart = autoStart,
                                    enabled = enabled,
                                )
                            )
                        },
                        enabled = isValid,
                    ) {
                        Text(if (isEditing) "Save" else "Add")
                    }
                }
            }
        }
    }
}