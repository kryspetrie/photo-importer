package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.regionTypeIcon

/** Tag Photo section — collapsed by default (secondary workflow). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SubjectsSection(
    subjects: String,
    onSubjectsChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onSelectFaces: (() -> Unit)? = null,
    faceRegions: List<FaceRegion> = emptyList(),
    onRemoveFace: ((Int) -> Unit)? = null,
    onClearAllFaces: (() -> Unit)? = null,
) {
    val s = strings()
    val subjectList =
        remember(subjects) { subjects.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var subjectInput by remember { mutableStateOf("") }
    var subjectsExpanded by remember { mutableStateOf(false) }
    val subjectFocusRequester = remember { FocusRequester() }

    fun commitSubjectTag() {
        val tag = subjectInput.trim()
        if (tag.isBlank()) return
        val updated = if (subjects.isBlank()) tag else "${subjects.trim()}, $tag"
        onSubjectsChange(updated)
        onMetadataHistoryUpdate("subjects", tag)
        subjectInput = ""
        // Keep focus for rapid multi-tag entry
        subjectFocusRequester.requestFocus()
    }

    androidx.compose.runtime.LaunchedEffect(faceRegions.size, subjects) {
        if (faceRegions.isNotEmpty() || subjects.isNotBlank()) {
            subjectsExpanded = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { subjectsExpanded = !subjectsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (subjectsExpanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription =
                    if (subjectsExpanded) s.t(StringKey.ACC_HIDE) else s.t(StringKey.ACC_SHOW),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_TAG_PHOTO),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            if (!subjectsExpanded && faceRegions.isNotEmpty()) {
                Text(
                    " (${faceRegions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (subjectsExpanded) {
            if (faceRegions.isNotEmpty()) {
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                s.t(StringKey.FIELD_TAG_REGIONS),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                            )
                            if (onClearAllFaces != null) {
                                Text(
                                    s.t(StringKey.FIELD_CLEAR_ALL),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { onClearAllFaces() },
                                )
                            }
                        }
                        faceRegions.forEachIndexed { idx, region ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                        contentDescription = region.type,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(region.name, style = MaterialTheme.typography.labelSmall)
                                    val xPct = kotlin.math.round(region.x * 100).toInt()
                                    val yPct = kotlin.math.round(region.y * 100).toInt()
                                    Text(
                                        "($xPct%, $yPct%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (onRemoveFace != null) {
                                    IconButton(
                                        onClick = { onRemoveFace(idx) },
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            s.t(StringKey.META_REMOVE),
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (subjectList.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    subjectList.forEach { subject ->
                        RemovableChip(
                            text = subject,
                            onRemove = {
                                val updated = subjectList.filter { it != subject }
                                onSubjectsChange(updated.joinToString(", "))
                                onMetadataHistoryRemove("subjects", subject)
                            },
                        )
                    }
                }
            }
            // ── Action buttons: Tag Photo + Add person input ──
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (onSelectFaces != null) {
                        OutlinedButton(
                            onClick = onSelectFaces,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Sell, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (faceRegions.isEmpty()) s.t(StringKey.FIELD_TAG_PHOTO)
                                else "${s.t(StringKey.WIZARD_TAG_EDITOR)} (${faceRegions.size})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    var suggestionsExpanded by remember { mutableStateOf(false) }
                    val availableSuggestions =
                        remember(metadataHistory.subjects, subjectList) {
                            metadataHistory.subjects.filter { it !in subjectList }
                        }
                    val filteredSuggestions =
                        remember(availableSuggestions, subjectInput) {
                            if (subjectInput.isBlank()) availableSuggestions
                            else
                                availableSuggestions.filter {
                                    it.contains(subjectInput, ignoreCase = true)
                                }
                        }
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = subjectInput,
                                onValueChange = {
                                    subjectInput = it
                                    suggestionsExpanded = true
                                },
                                placeholder = {
                                    Text(
                                        s.t(StringKey.FIELD_ADD_PERSON),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                                        .defaultMinSize(minHeight = 0.dp)
                                        .focusRequester(subjectFocusRequester)
                                        .onPreviewKeyEvent { event ->
                                            if (
                                                event.type == KeyEventType.KeyDown &&
                                                    event.key == Key.Enter &&
                                                    subjectInput.isNotBlank()
                                            ) {
                                                commitSubjectTag()
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { commitSubjectTag() }),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    if (subjectInput.isNotBlank()) {
                                        IconButton(
                                            onClick = { commitSubjectTag() },
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                s.t(StringKey.FIELD_ADD_SUBJECT),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        if (filteredSuggestions.isNotEmpty()) {
                            DropdownMenu(
                                expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                                onDismissRequest = { suggestionsExpanded = false },
                            ) {
                                filteredSuggestions.take(10).forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                suggestion,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        onClick = {
                                            val updated =
                                                if (subjects.isBlank()) suggestion
                                                else "${subjects.trim()}, $suggestion"
                                            onSubjectsChange(updated)
                                            onMetadataHistoryUpdate("subjects", suggestion)
                                            subjectInput = ""
                                            suggestionsExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                } // closes Column
            } // closes Surface
        }
    }
}
