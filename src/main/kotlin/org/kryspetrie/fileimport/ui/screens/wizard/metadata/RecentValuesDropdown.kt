package org.kryspetrie.fileimport.ui.screens.wizard.metadata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet

/**
 * A dropdown button that shows recently used metadata value sets and allows the user
 * to apply one to the current fields. This is the primary "apply recent values" UI.
 *
 * Shows a compact dropdown of recent sets, each displaying a label (location/date/camera)
 * and a summary line. When selected, calls [onApplySet] with the chosen set.
 *
 * @param recentSets The list of recent metadata sets to display (MRU-first).
 * @param onApplySet Callback invoked when the user selects a set to apply.
 * @param onRemoveSet Optional callback to remove a set from history (e.g., swipe-to-delete).
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentValuesDropdown(
    recentSets: List<RecentMetadataSet>,
    onApplySet: (RecentMetadataSet) -> Unit,
    onRemoveSet: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (recentSets.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Apply Recent Values", style = MaterialTheme.typography.labelSmall)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            recentSets.take(10).forEach { set ->
                Card(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    onClick = {
                        onApplySet(set)
                        expanded = false
                    },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            set.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (set.summary.isNotBlank() && set.summary != set.label) {
                            Text(
                                set.summary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A dropdown button specifically for applying recent location values.
 * Shows recent sets that have location data, and applies only the location fields
 * (locationName, city, state, country, gpsLatitude, gpsLongitude).
 *
 * @param metadataHistory The full metadata history containing recent sets.
 * @param onApplyLocation Callback invoked when the user selects a location set.
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentLocationDropdown(
    metadataHistory: MetadataHistory,
    onApplyLocation: (RecentMetadataSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locationSets = remember(metadataHistory) { metadataHistory.getLocationSets() }
    if (locationSets.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Recent Locations", style = MaterialTheme.typography.labelSmall)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            locationSets.take(10).forEach { set ->
                Card(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                    onClick = {
                        onApplyLocation(set)
                        expanded = false
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            val locLabel =
                                listOfNotNull(
                                    if (set.locationName.isNotBlank()) set.locationName else null,
                                    if (set.city.isNotBlank()) set.city else null,
                                    if (set.state.isNotBlank()) set.state else null,
                                    if (set.country.isNotBlank()) set.country else null,
                                ).joinToString(", ")
                            Text(
                                locLabel.ifBlank { set.label },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (set.gpsLatitude.isNotBlank() && set.gpsLongitude.isNotBlank()) {
                                Text(
                                    "${set.gpsLatitude}, ${set.gpsLongitude}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}