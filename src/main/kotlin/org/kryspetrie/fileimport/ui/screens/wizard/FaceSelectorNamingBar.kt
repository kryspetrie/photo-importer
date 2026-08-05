@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun FaceSelectorNamingBar(
    faceRegions: List<FaceRegion>,
    namingFaceIndex: Int,
    namingInput: String,
    namingFocusRequester: FocusRequester,
    isLastUnnamed: Boolean,
    nameSuggestions: Map<Int, String>,
    onNamingInputChange: (String) -> Unit,
    onSaveAndAdvance: () -> Unit,
    onDeleteEmpty: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (namingFaceIndex !in faceRegions.indices) return
    val s = strings()
    val currentRegion = faceRegions[namingFaceIndex]
    val hasMoreUnnamedFaces =
        faceRegions.indices.any { i -> i != namingFaceIndex && faceRegions[i].name.isBlank() }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier =
                Modifier.padding(horizontal = 8.dp, vertical = 6.dp).onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (keyEvent.key) {
                            Key.Enter -> {
                                onSaveAndAdvance()
                                true
                            }
                            Key.Backspace -> {
                                if (namingInput.isEmpty()) {
                                    onDeleteEmpty()
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                regionTypeIcon(RegionType.fromMwgRs(currentRegion.type)),
                contentDescription = currentRegion.type,
                modifier = Modifier.size(16.dp),
                tint = regionTypeColor(RegionType.fromMwgRs(currentRegion.type)),
            )
            Text(
                s.t(
                    StringKey.WIZARD_TAG_LABEL,
                    "current" to (namingFaceIndex + 1).toString(),
                    "total" to faceRegions.size.toString(),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
            OutlinedTextField(
                value = namingInput,
                onValueChange = onNamingInputChange,
                placeholder = {
                    Text(
                        nameSuggestions[namingFaceIndex] ?: s.t(StringKey.FIELD_NAME_PLACEHOLDER),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.width(120.dp).focusRequester(namingFocusRequester),
                textStyle = MaterialTheme.typography.labelSmall,
                singleLine = true,
            )
            Button(onClick = onSaveAndAdvance, enabled = namingInput.isNotBlank()) {
                Text(
                    if (hasMoreUnnamedFaces) s.t(StringKey.WIZARD_SAVE_AND_NEXT)
                    else s.t(StringKey.ACTION_SAVE),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (!isLastUnnamed) {
                OutlinedButton(onClick = onSkip) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = s.t(StringKey.WIZARD_SKIP),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(s.t(StringKey.WIZARD_SKIP), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                if (hasMoreUnnamedFaces) s.t(StringKey.WIZARD_KEYBOARD_HINT)
                else s.t(StringKey.WIZARD_KEYBOARD_HINT_LAST),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}
