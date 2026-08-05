/**
 * Settings panels for the photo scan import screen.
 *
 * Orientation and Organization are top-level collapsible cards (same style as Import).
 */
package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.ImportConfiguration
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.configSummary
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.components.CollapsibleSettingsCard
import org.kryspetrie.fileimport.ui.screens.components.OrganizationSettingsSection
import org.kryspetrie.fileimport.ui.screens.components.OrientationSettingsSection

@Composable
fun PhotoScanSettingsSection(
    config: ImportConfiguration,
    onConfigChange: (ImportConfiguration) -> Unit,
    settingsExpanded: Boolean,
    onSettingsExpandedChange: (Boolean) -> Unit,
) {
    val s = strings()
    // Fully local expand flags. Never re-seed from [settingsExpanded] on every change — that used
    // to fight SessionPreferencesEffect and flicker Organization open/closed.
    var orientationExpanded by remember { mutableStateOf(false) }
    var organizationExpanded by remember { mutableStateOf(false) }
    var userControlled by remember { mutableStateOf(false) }

    // One-shot: if session restore prefers "settings open", open Organization once before the user
    // interacts with either card.
    LaunchedEffect(settingsExpanded) {
        if (!userControlled && settingsExpanded) {
            organizationExpanded = true
        }
    }

    fun setOrientationExpanded(next: Boolean) {
        userControlled = true
        orientationExpanded = next
        onSettingsExpandedChange(next || organizationExpanded)
    }

    fun setOrganizationExpanded(next: Boolean) {
        userControlled = true
        organizationExpanded = next
        onSettingsExpandedChange(orientationExpanded || next)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CollapsibleSettingsCard(
            title = s.t(StringKey.IMPORT_ORIENTATION),
            icon = Icons.Default.AutoFixHigh,
            expanded = orientationExpanded,
            onToggle = { setOrientationExpanded(!orientationExpanded) },
            summary =
                if (config.autoOrientEnabled) s.t(StringKey.IMPORT_SUMMARY_AUTO_ORIENT) else null,
        ) {
            OrientationSettingsSection(
                configuration = config,
                onConfigChange = onConfigChange,
                collapsible = false,
            )
        }

        CollapsibleSettingsCard(
            title = s.t(StringKey.IMPORT_ORGANIZATION),
            icon = Icons.Default.Folder,
            expanded = organizationExpanded,
            onToggle = { setOrganizationExpanded(!organizationExpanded) },
            summary = s.configSummary(config),
        ) {
            OrganizationSettingsSection(
                configuration = config,
                onConfigChange = onConfigChange,
                collapsible = false,
            )
        }
    }
}
