package org.kryspetrie.fileimport.ui.screens.wizard.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.MetadataHistory
import org.kryspetrie.fileimport.domain.model.RecentMetadataSet
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.shared.metadata.RecentLocationDropdown
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.OverrideCheckbox

/**
 * Location section — condensed layout.
 * - **Location Name**: A colloquial/recognizable name (e.g. "Grandma's House", "Disney World")
 * - **Address**: Full geocoded address from the map picker (e.g. "Worcester, Massachusetts, United
 *   States")
 *
 * If `address` is set, it is written to IPTC SubLocation in EXIF; otherwise locationName is used.
 * City/State/Country/GPS are collapsed by default since the map picker typically fills them; they
 * remain editable for fine-grained control when expanded.
 */
@Composable
internal fun LocationSection(
    locationName: String,
    onLocationNameChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    stateVal: String,
    onStateChange: (String) -> Unit,
    country: String,
    onCountryChange: (String) -> Unit,
    gpsLatitude: String,
    onGpsLatitudeChange: (String) -> Unit,
    gpsLongitude: String,
    onGpsLongitudeChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    onPickLocation: (() -> Unit)? = null,
    onApplyRecentLocation: ((RecentMetadataSet) -> Unit)? = null,
    overrideGps: Boolean? = null,
    onOverrideGpsChange: ((Boolean) -> Unit)? = null,
    sourceGpsHint: String? = null,
) {
    val s = strings()
    // Collapse details by default
    val hasDetails =
        city.isNotBlank() ||
            stateVal.isNotBlank() ||
            country.isNotBlank() ||
            gpsLatitude.isNotBlank() ||
            gpsLongitude.isNotBlank()
    var detailsExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        // ── Section header ──
        Text(
            s.t(StringKey.FIELD_LOCATION),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )

        // ── Recent Locations ──
        if (onApplyRecentLocation != null) {
            RecentLocationDropdown(
                metadataHistory = metadataHistory,
                onApplyLocation = onApplyRecentLocation,
            )
        }

        // ── Location Name — colloquial/recognizable name ──
        MetadataField(
            label = s.t(StringKey.FIELD_LOCATION_NAME),
            placeholder = s.t(StringKey.FIELD_LOCATION_NAME_PLACEHOLDER),
            value = locationName,
            onValueChange = onLocationNameChange,
            suggestions = metadataHistory.locationName,
            onCommit = { onMetadataHistoryUpdate("locationName", locationName) },
        )

        // ── Address — full geocoded address from map picker ──
        MetadataField(
            label = s.t(StringKey.FIELD_ADDRESS),
            placeholder = s.t(StringKey.FIELD_ADDRESS_PLACEHOLDER),
            value = address,
            onValueChange = onAddressChange,
            suggestions = metadataHistory.address,
            onCommit = { onMetadataHistoryUpdate("address", address) },
            trailingIcon =
                if (onPickLocation != null) {
                    {
                        IconButton(onClick = onPickLocation, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = s.t(StringKey.FIELD_PICK_ON_MAP),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                } else null,
        )

        // ── Expand/collapse for City/State/Country/GPS details ──
        Row(
            modifier = Modifier.fillMaxWidth().clickable { detailsExpanded = !detailsExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (detailsExpanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription =
                    if (detailsExpanded) s.t(StringKey.FIELD_HIDE_DETAILS)
                    else s.t(StringKey.FIELD_SHOW_DETAILS),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (detailsExpanded) s.t(StringKey.FIELD_HIDE_DETAILS)
                else s.t(StringKey.FIELD_LOCATION_DETAILS),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!detailsExpanded) {
                val summaryParts =
                    listOfNotNull(
                        city.takeIf { it.isNotBlank() },
                        stateVal.takeIf { it.isNotBlank() },
                        country.takeIf { it.isNotBlank() },
                        gpsLatitude
                            .takeIf { it.isNotBlank() }
                            ?.let { lat ->
                                gpsLongitude.takeIf { it.isNotBlank() }?.let { lon -> "$lat, $lon" }
                            },
                    )
                if (summaryParts.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "— ${summaryParts.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (detailsExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_CITY),
                    placeholder = s.t(StringKey.FIELD_CITY_PLACEHOLDER),
                    value = city,
                    onValueChange = onCityChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.city,
                    onCommit = { onMetadataHistoryUpdate("city", city) },
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_STATE),
                    placeholder = s.t(StringKey.FIELD_STATE_PLACEHOLDER),
                    value = stateVal,
                    onValueChange = onStateChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.state,
                    onCommit = { onMetadataHistoryUpdate("state", stateVal) },
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_COUNTRY),
                    placeholder = s.t(StringKey.FIELD_COUNTRY_PLACEHOLDER),
                    value = country,
                    onValueChange = onCountryChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.country,
                    onCommit = { onMetadataHistoryUpdate("country", country) },
                )
            }

            // ── GPS Coordinates — inside details section ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(s.t(StringKey.FIELD_GPS), style = MaterialTheme.typography.labelMedium)
                if (overrideGps != null && onOverrideGpsChange != null) {
                    Spacer(Modifier.width(4.dp))
                    OverrideCheckbox(included = overrideGps, onIncludedChange = onOverrideGpsChange)
                }
                Spacer(Modifier.weight(1f))
                if (sourceGpsHint != null) {
                    Text(
                        sourceGpsHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_LAT),
                    placeholder = s.t(StringKey.FIELD_LAT_PLACEHOLDER),
                    value = gpsLatitude,
                    onValueChange = onGpsLatitudeChange,
                    suggestions = metadataHistory.gpsLatitude,
                    onCommit = { onMetadataHistoryUpdate("gpsLatitude", gpsLatitude) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_LON),
                    placeholder = s.t(StringKey.FIELD_LON_PLACEHOLDER),
                    value = gpsLongitude,
                    onValueChange = onGpsLongitudeChange,
                    suggestions = metadataHistory.gpsLongitude,
                    onCommit = { onMetadataHistoryUpdate("gpsLongitude", gpsLongitude) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
