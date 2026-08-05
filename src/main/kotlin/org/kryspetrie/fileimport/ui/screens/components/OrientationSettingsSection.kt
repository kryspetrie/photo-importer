package org.kryspetrie.fileimport.ui.screens.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.SectionLabel
import org.kryspetrie.fileimport.ui.components.SettingsToggle
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
fun OrientationSettingsSection(
    configuration: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    /** When false, renders contents only (for use inside a top-level [CollapsibleSettingsCard]). */
    collapsible: Boolean = true,
) {
    val s = strings()
    val content: @Composable () -> Unit = {
        SectionLabel(s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT))
        SettingsToggle(
            checked = configuration.autoOrientEnabled,
            onCheckedChange = { onConfigChange(configuration.copy(autoOrientEnabled = it)) },
            label = s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT),
            description = s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT_DESC),
        )
        if (configuration.autoOrientEnabled) {
            Text(
                s.t(StringKey.SETTINGS_ORIENTATION_AUTO_ORIENT_ENABLED_NOTE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (collapsible) {
        var expanded by remember { mutableStateOf(false) }
        CollapsibleSubsection(
            title = s.t(StringKey.SETTINGS_ORIENTATION),
            icon = Icons.Default.AutoFixHigh,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            content = { content() },
        )
    } else {
        content()
    }
}

