package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Compact override checkbox for EXIF metadata fields.
 *
 * Two states:
 * - **Checked** (default): Field is included in output. If the user typed a value, it overrides the
 *   source. If not, the source EXIF value is preserved.
 * - **Unchecked**: Field is explicitly removed from output EXIF.
 *
 * This replaces the previous tri-state `OverrideIndicator` (eye-off icon). The tri-state
 * distinction between KEEP_SOURCE and OVERRIDE is now auto-derived: if the user has a non-blank
 * value, it's an OVERRIDE; otherwise it's KEEP_SOURCE. The UI only exposes a boolean:
 * include/exclude.
 *
 * @param included Whether the field is included in output (true = keep/override, false = null out)
 * @param onIncludedChange Callback when user toggles the checkbox
 * @param modifier Optional modifier
 * @param enabled Whether the checkbox is interactive
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OverrideCheckbox(
    included: Boolean,
    onIncludedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tooltipState = rememberTooltipState()

    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.extraSmall,
                shadowElevation = 4.dp,
            ) {
                Text(
                    if (included) "Field included in output — uncheck to remove from output"
                    else "Field removed from output — check to restore",
                    modifier = Modifier,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        state = tooltipState,
    ) {
        Checkbox(
            checked = included,
            onCheckedChange = onIncludedChange,
            modifier =
                modifier.size(20.dp).semantics {
                    contentDescription =
                        if (included) "Include field in EXIF output"
                        else "Exclude field from EXIF output"
                },
            enabled = enabled,
            colors =
                CheckboxDefaults.colors(
                    uncheckedColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                ),
        )
    }
}
