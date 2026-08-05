package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.isCtrlPressed

@Composable
internal fun EditScreenChrome(
    photoCount: Int,
    selectedIndices: Set<Int>,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSkipCurrentPhoto: (() -> Unit)?,
    onSelectPrevious: () -> Unit,
    onSelectNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val s = strings()
    Column(
        modifier =
            modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isMeta = isCtrlPressed(keyEvent)
                    when {
                        isMeta && keyEvent.key == Key.Comma -> {
                            onSelectPrevious()
                            true
                        }
                        isMeta && keyEvent.key == Key.Period -> {
                            onSelectNext()
                            true
                        }
                        isMeta && keyEvent.key == Key.Enter -> {
                            if (photoCount > 0) onExport()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        // Title bar
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.t(StringKey.WIZARD_EDIT_PHOTOS),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }

        // Main content
        content(Modifier.weight(1f))

        // Bottom action bar
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.height(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(s.back)
                }
                if (onSkipCurrentPhoto != null) {
                    OutlinedButton(
                        onClick = onSkipCurrentPhoto,
                        modifier = Modifier.height(32.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Icon(Icons.Default.SkipNext, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.WIZARD_SKIP_PHOTO),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                val countLabel =
                    if (photoCount == 1) s.t(StringKey.PLURAL_PHOTOS_ONE)
                    else s.t(StringKey.PLURAL_PHOTOS_OTHER, "count" to photoCount.toString())
                val currentIdx =
                    if (photoCount > 1 && selectedIndices.size == 1) selectedIndices.first()
                    else -1
                Text(
                    if (currentIdx >= 0) {
                        s.t(
                            StringKey.SCAN_PHOTO_LABEL,
                            "index" to (currentIdx + 1).toString(),
                            "total" to photoCount.toString(),
                        )
                    } else {
                        countLabel
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Button(
                    onClick = onExport,
                    enabled = photoCount > 0,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(s.next, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
    }
}
