package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.wizard.state.PhotoScanWizardState
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/**
 * Core metadata fields: description, keywords (with chip UI + X removal), original date (with date
 * picker), year.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun QuickEditMetadataFields(
    description: String,
    onDescriptionChange: (String) -> Unit,
    keywords: String,
    onKeywordsChange: (String) -> Unit,
    originalDate: String,
    onOriginalDateChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onMetadataHistoryRemove: (String, String) -> Unit,
    onCommitKeyword: ((String) -> Unit)? = null,
    boxId: String? = null,
    state: PhotoScanWizardState? = null,
    overrideDescription: Boolean? = null,
    onOverrideDescriptionChange: ((Boolean) -> Unit)? = null,
    overrideKeywords: Boolean? = null,
    onOverrideKeywordsChange: ((Boolean) -> Unit)? = null,
    overrideOriginalDate: Boolean? = null,
    onOverrideOriginalDateChange: ((Boolean) -> Unit)? = null,
    overrideYear: Boolean? = null,
    onOverrideYearChange: ((Boolean) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
    keywordsFocusTrigger: Int = 0,
) {
    val s = strings()
    val keywordsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(keywordsFocusTrigger) {
        if (keywordsFocusTrigger > 0) {
            keywordsFocusRequester.requestFocus()
        }
    }
    val keywordList =
        remember(keywords) { keywords.split(",").map { it.trim() }.filter { it.isNotBlank() } }
    var keywordInput by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    fun commitKeyword() {
        val tag = keywordInput.trim()
        if (tag.isBlank()) return
        val updated = if (keywords.isBlank()) tag else "$keywords, $tag"
        onKeywordsChange(updated)
        onMetadataHistoryUpdate("keywords", tag)
        onCommitKeyword?.invoke(tag)
        keywordInput = ""
        keywordsFocusRequester.requestFocus()
    }

    MetadataField(
        label = s.t(StringKey.FIELD_DESCRIPTION),
        placeholder = s.t(StringKey.FIELD_DESCRIPTION_PLACEHOLDER),
        value = description,
        onValueChange = onDescriptionChange,
        suggestions = metadataHistory.description,
        onCommit = { onMetadataHistoryUpdate("description", description) },
        fieldIncluded = overrideDescription,
        onFieldIncludedChange = onOverrideDescriptionChange,
        sourceHint = sourceExif?.description,
    )

    // Keywords — chip/tag UI with X removal + suggestion dropdown
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.t(StringKey.FIELD_KEYWORDS), style = MaterialTheme.typography.labelMedium)
        if (keywordList.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                keywordList.forEach { keyword ->
                    RemovableChip(
                        text = keyword,
                        onRemove = {
                            val updated = keywordList.filter { it != keyword }
                            onKeywordsChange(updated.joinToString(", "))
                            onMetadataHistoryRemove("keywords", keyword)
                        },
                    )
                }
            }
        }
        var suggestionsExpanded by remember { mutableStateOf(false) }
        val availableSuggestions =
            remember(metadataHistory.keywords, keywordList) {
                metadataHistory.keywords.filter { it !in keywordList }
            }
        val filteredSuggestions =
            remember(availableSuggestions, keywordInput) {
                if (keywordInput.isBlank()) availableSuggestions
                else availableSuggestions.filter { it.contains(keywordInput, ignoreCase = true) }
            }
        Box {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = {
                        keywordInput = it
                        suggestionsExpanded = true
                    },
                    placeholder = {
                        Text(
                            s.t(StringKey.FIELD_ADD_KEYWORD_PLACEHOLDER),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                            .defaultMinSize(minHeight = 0.dp)
                            .focusRequester(keywordsFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                        event.key == Key.Enter &&
                                        keywordInput.isNotBlank()
                                ) {
                                    commitKeyword()
                                    true
                                } else {
                                    false
                                }
                            },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitKeyword() }),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        if (keywordInput.isNotBlank()) {
                            IconButton(
                                onClick = { commitKeyword() },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    s.t(StringKey.FIELD_ADD_KEYWORD),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
            if (availableSuggestions.isNotEmpty() && filteredSuggestions.isNotEmpty()) {
                DropdownMenu(
                    expanded = suggestionsExpanded && filteredSuggestions.isNotEmpty(),
                    onDismissRequest = { suggestionsExpanded = false },
                ) {
                    filteredSuggestions.take(10).forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Text(suggestion, style = MaterialTheme.typography.labelSmall)
                            },
                            onClick = {
                                val updated =
                                    if (keywords.isBlank()) suggestion else "$keywords, $suggestion"
                                onKeywordsChange(updated)
                                onMetadataHistoryUpdate("keywords", suggestion)
                                keywordInput = ""
                                suggestionsExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }

    // Original Date + Year on the same line
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_ORIGINAL_DATE),
                    placeholder = s.t(StringKey.FIELD_DATE_PLACEHOLDER),
                    value = originalDate,
                    onValueChange = onOriginalDateChange,
                    suggestions = metadataHistory.originalDate,
                    onCommit = { onMetadataHistoryUpdate("originalDate", originalDate) },
                    modifier = Modifier.weight(1f),
                    fieldIncluded = overrideOriginalDate,
                    onFieldIncludedChange = onOverrideOriginalDateChange,
                    sourceHint = sourceExif?.dateOriginal,
                )
                IconButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.size(32.dp).padding(top = 20.dp),
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        s.t(StringKey.FIELD_PICK_DATE),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        MetadataField(
            label = s.t(StringKey.FIELD_YEAR),
            placeholder = s.t(StringKey.FIELD_YEAR_PLACEHOLDER),
            value = year,
            onValueChange = { onYearChange(it.filter { c -> c.isDigit() }.take(4)) },
            keyboardType = KeyboardType.Number,
            suggestions = metadataHistory.year,
            onCommit = { onMetadataHistoryUpdate("year", year) },
            modifier = Modifier.width(120.dp),
            fieldIncluded = overrideYear,
            onFieldIncludedChange = onOverrideYearChange,
            sourceHint = sourceExif?.dateOriginal?.take(4),
        )
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            onDateSelected = { selectedDate ->
                onOriginalDateChange(selectedDate)
                onMetadataHistoryUpdate("originalDate", selectedDate)
                showDatePicker = false
            },
        )
    }
}
