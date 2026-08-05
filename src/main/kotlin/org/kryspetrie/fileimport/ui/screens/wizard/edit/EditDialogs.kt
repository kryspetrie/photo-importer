package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.regionTypeIcon
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerDialog(onDismissRequest: () -> Unit, onDateSelected: (String) -> Unit) {
    val s = strings()
    val datePickerState = rememberDatePickerState()
    EditDialog(onDismissRequest = onDismissRequest) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DatePicker(state = datePickerState)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text(s.cancel) }
                    TextButton(
                        onClick = {
                            val selectedDate =
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val instant = Instant.ofEpochMilli(millis)
                                    val localDate =
                                        instant.atZone(ZoneId.systemDefault()).toLocalDate()
                                    localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                } ?: ""
                            if (selectedDate.isNotBlank()) onDateSelected(selectedDate)
                            onDismissRequest()
                        }
                    ) {
                        Text(s.ok)
                    }
                }
            }
        }
    }
}

/** Wrapper for Dialog that works in Compose Desktop. */
@Composable
internal fun EditDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
        }
    }
}

/** Face name entry panel (extracted from inline Dialog content). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FaceNameEntryPanel(
    faceNameInput: String,
    onFaceNameInputChange: (String) -> Unit,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val s = strings()
    Surface(
        modifier = Modifier.width(220.dp),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                s.t(
                    StringKey.FIELD_NAME_THIS,
                    "type" to s.regionTypeName(selectedRegionType).lowercase(),
                ),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    regionTypeIcon(selectedRegionType),
                    contentDescription = s.regionTypeName(selectedRegionType),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${s.regionTypeName(selectedRegionType)} • ${s.faceSizeName(selectedFaceSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextField(
                value = faceNameInput,
                onValueChange = onFaceNameInputChange,
                placeholder = {
                    Text(
                        s.t(StringKey.FIELD_NAME_PLACEHOLDER),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.align(Alignment.End),
            ) {
                OutlinedButton(onClick = onCancel) {
                    Text(s.cancel, style = MaterialTheme.typography.labelSmall)
                }
                Button(onClick = onConfirm, enabled = faceNameInput.isNotBlank()) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.save, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A removable chip/tag for keywords and subjects. Shows text with an X button to remove. */
@Composable
internal fun RemovableChip(text: String, onRemove: () -> Unit) {
    val s = strings()
    Surface(
        modifier = Modifier.clickable(onClick = onRemove),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(
                Icons.Default.Close,
                contentDescription = "${s.t(StringKey.META_REMOVE)} $text",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
