package org.kryspetrie.fileimport.ui.screens.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale

/**
 * Shared header for metadata editor panels (standalone bulk editor + Photo Scan wizard editor).
 *
 * Always shows [title] + Clear. In multi-select / batch mode also shows Apply and an optional hint.
 */
@Composable
fun MetadataEditorPanelHeader(
    title: String,
    onClear: () -> Unit,
    onApply: (() -> Unit)? = null,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val density = LocalUiDensityScale.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(density.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(density.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.height(density.controlMinHeight),
            ) {
                Text(s.t(StringKey.META_CLEAR), style = MaterialTheme.typography.labelSmall)
            }
            if (onApply != null) {
                Button(onClick = onApply, modifier = Modifier.height(density.controlMinHeight)) {
                    Text(s.apply, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
