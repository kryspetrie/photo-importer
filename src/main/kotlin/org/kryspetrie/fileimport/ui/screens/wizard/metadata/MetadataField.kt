package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A reusable metadata text field with optional autocomplete suggestions and an EXIF override
 * checkbox.
 *
 * Features:
 * - Dropdown autocomplete suggestions when [suggestions] is non-empty
 * - Strikethrough text styling when the field is excluded (NULL_OUT override)
 * - Optional [OverrideCheckbox] trailing icon for tri-state EXIF control
 * - Source EXIF hint displayed below the field
 * - Keyboard focus navigation (Tab/Enter) through sequential fields
 *
 * @param label Field label shown above the text field
 * @param placeholder Placeholder text when field is empty
 * @param value Current field value
 * @param onValueChange Callback when field value changes
 * @param modifier Optional layout modifier for the Column container
 * @param keyboardType Keyboard type for input (Text, Number, etc.)
 * @param singleLine Whether the text field is single-line (default true)
 * @param suggestions Autocomplete suggestions to show in dropdown
 * @param onCommit Callback when user selects a suggestion (Enter key)
 * @param fieldIncluded Whether the field is included in EXIF output (null = no checkbox)
 * @param onFieldIncludedChange Callback when the override checkbox is toggled
 * @param focusRequester Optional focus requester for keyboard navigation
 * @param sourceHint Source EXIF value to display as a hint below the field
 * @param alwaysNavigateFocus If true, always enable Tab/Enter focus navigation. If false (default),
 *   only navigate when [focusRequester] is provided.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    suggestions: List<String> = emptyList(),
    onCommit: (() -> Unit)? = null,
    fieldIncluded: Boolean? = null,
    onFieldIncludedChange: ((Boolean) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    sourceHint: String? = null,
    alwaysNavigateFocus: Boolean = true,
) {
    val focusManager = LocalFocusManager.current

    // Derive whether field is excluded from output (for UI styling)
    val fieldExcluded = fieldIncluded == false
    val hasOverride = fieldIncluded != null && onFieldIncludedChange != null

    // Show source hint below the field when included (not excluded/null-out)
    val showSourceHint = sourceHint != null && sourceHint.isNotBlank() && !fieldExcluded

    // Keyboard focus navigation: navigate on Tab/Enter when requested
    val keyboardActions =
        if (focusRequester != null || alwaysNavigateFocus) {
            KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.moveFocus(FocusDirection.Down) },
            )
        } else {
            KeyboardActions()
        }

    Column(modifier = modifier) {
        if (suggestions.isNotEmpty()) {
            // Use ExposedDropdownMenuBox for autocomplete suggestions
            var expanded by remember { mutableStateOf(false) }
            val filteredSuggestions =
                remember(suggestions, value) {
                    if (value.isBlank()) suggestions
                    else suggestions.filter { it.contains(value, ignoreCase = true) }
                }

            ExposedDropdownMenuBox(
                expanded = expanded && filteredSuggestions.isNotEmpty(),
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        expanded = true
                    },
                    label = { Text(label) },
                    placeholder = {
                        Text(placeholder, style = MaterialTheme.typography.labelSmall)
                    },
                    modifier =
                        Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .then(
                                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                                else Modifier
                            ),
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    keyboardActions = keyboardActions,
                    textStyle =
                        if (fieldExcluded)
                            MaterialTheme.typography.bodyMedium.copy(
                                textDecoration =
                                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        else MaterialTheme.typography.bodyMedium,
                    enabled = !fieldExcluded,
                    trailingIcon =
                        if (hasOverride) {
                            {
                                OverrideCheckbox(
                                    included = fieldIncluded,
                                    onIncludedChange = onFieldIncludedChange,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        } else null,
                )
                if (filteredSuggestions.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = expanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                    ) {
                        filteredSuggestions.take(10).forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Text(suggestion, style = MaterialTheme.typography.bodySmall)
                                },
                                onClick = {
                                    onValueChange(suggestion)
                                    expanded = false
                                    onCommit?.invoke()
                                },
                            )
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                keyboardActions = keyboardActions,
                textStyle =
                    if (fieldExcluded)
                        MaterialTheme.typography.bodyMedium.copy(
                            textDecoration =
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    else MaterialTheme.typography.bodyMedium,
                enabled = !fieldExcluded,
                trailingIcon =
                    if (hasOverride) {
                        {
                            OverrideCheckbox(
                                included = fieldIncluded,
                                onIncludedChange = onFieldIncludedChange,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    } else null,
            )
        }
        if (showSourceHint) {
            Text(
                text = "Source: $sourceHint",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 8.dp, top = 1.dp),
            )
        }
    }
}
