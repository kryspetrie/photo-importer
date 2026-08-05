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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.screens.wizard.metadata.MetadataField
import org.kryspetrie.fileimport.ui.wizard.state.SourceExifSummary

/** Camera settings section — collapsed by default since it's rarely needed. */
@Composable
internal fun CameraSection(
    cameraMake: String,
    onCameraMakeChange: (String) -> Unit,
    cameraModel: String,
    onCameraModelChange: (String) -> Unit,
    lensModel: String,
    onLensModelChange: (String) -> Unit,
    focalLength: String,
    onFocalLengthChange: (String) -> Unit,
    aperture: String,
    onApertureChange: (String) -> Unit,
    shutterSpeed: String,
    onShutterSpeedChange: (String) -> Unit,
    iso: String,
    onIsoChange: (String) -> Unit,
    metadataHistory: MetadataHistory,
    onMetadataHistoryUpdate: (String, String) -> Unit,
    overrideCameraMake: Boolean? = null,
    onOverrideCameraMakeChange: ((Boolean) -> Unit)? = null,
    overrideCameraModel: Boolean? = null,
    onOverrideCameraModelChange: ((Boolean) -> Unit)? = null,
    overrideLensModel: Boolean? = null,
    onOverrideLensModelChange: ((Boolean) -> Unit)? = null,
    overrideFocalLength: Boolean? = null,
    onOverrideFocalLengthChange: ((Boolean) -> Unit)? = null,
    overrideAperture: Boolean? = null,
    onOverrideApertureChange: ((Boolean) -> Unit)? = null,
    overrideShutterSpeed: Boolean? = null,
    onOverrideShutterSpeedChange: ((Boolean) -> Unit)? = null,
    overrideIso: Boolean? = null,
    onOverrideIsoChange: ((Boolean) -> Unit)? = null,
    sourceExif: SourceExifSummary? = null,
) {
    val s = strings()
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) ExpandLessIcon else ExpandMoreIcon,
                contentDescription =
                    if (expanded) s.t(StringKey.ACC_HIDE) else s.t(StringKey.ACC_SHOW),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                s.t(StringKey.FIELD_CAMERA_SETTINGS),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_CAMERA_MAKE),
                    placeholder = s.t(StringKey.FIELD_CAMERA_MAKE_PLACEHOLDER),
                    value = cameraMake,
                    onValueChange = onCameraMakeChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.cameraMake,
                    onCommit = { onMetadataHistoryUpdate("cameraMake", cameraMake) },
                    fieldIncluded = overrideCameraMake,
                    onFieldIncludedChange = onOverrideCameraMakeChange,
                    sourceHint = sourceExif?.cameraMake,
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_CAMERA_MODEL),
                    placeholder = s.t(StringKey.FIELD_CAMERA_MODEL_PLACEHOLDER),
                    value = cameraModel,
                    onValueChange = onCameraModelChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.cameraModel,
                    onCommit = { onMetadataHistoryUpdate("cameraModel", cameraModel) },
                    fieldIncluded = overrideCameraModel,
                    onFieldIncludedChange = onOverrideCameraModelChange,
                    sourceHint = sourceExif?.cameraModel,
                )
            }
            MetadataField(
                label = s.t(StringKey.FIELD_LENS_MODEL),
                placeholder = s.t(StringKey.FIELD_LENS_PLACEHOLDER),
                value = lensModel,
                onValueChange = onLensModelChange,
                suggestions = metadataHistory.lensModel,
                onCommit = { onMetadataHistoryUpdate("lensModel", lensModel) },
                fieldIncluded = overrideLensModel,
                onFieldIncludedChange = onOverrideLensModelChange,
                sourceHint = sourceExif?.lensModel,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_FOCAL_LENGTH),
                    placeholder = s.t(StringKey.FIELD_FOCAL_PLACEHOLDER),
                    value = focalLength,
                    onValueChange = onFocalLengthChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.focalLength,
                    onCommit = { onMetadataHistoryUpdate("focalLength", focalLength) },
                    fieldIncluded = overrideFocalLength,
                    onFieldIncludedChange = onOverrideFocalLengthChange,
                    sourceHint = sourceExif?.focalLength,
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_APERTURE),
                    placeholder = s.t(StringKey.FIELD_APERTURE_PLACEHOLDER),
                    value = aperture,
                    onValueChange = onApertureChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.aperture,
                    onCommit = { onMetadataHistoryUpdate("aperture", aperture) },
                    fieldIncluded = overrideAperture,
                    onFieldIncludedChange = onOverrideApertureChange,
                    sourceHint = sourceExif?.aperture,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetadataField(
                    label = s.t(StringKey.FIELD_SHUTTER_SPEED),
                    placeholder = s.t(StringKey.FIELD_SHUTTER_PLACEHOLDER),
                    value = shutterSpeed,
                    onValueChange = onShutterSpeedChange,
                    modifier = Modifier.weight(1f),
                    suggestions = metadataHistory.shutterSpeed,
                    onCommit = { onMetadataHistoryUpdate("shutterSpeed", shutterSpeed) },
                    fieldIncluded = overrideShutterSpeed,
                    onFieldIncludedChange = onOverrideShutterSpeedChange,
                    sourceHint = sourceExif?.shutterSpeed,
                )
                MetadataField(
                    label = s.t(StringKey.FIELD_ISO),
                    placeholder = s.t(StringKey.FIELD_ISO_PLACEHOLDER),
                    value = iso,
                    onValueChange = onIsoChange,
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                    suggestions = metadataHistory.iso,
                    onCommit = { onMetadataHistoryUpdate("iso", iso) },
                    fieldIncluded = overrideIso,
                    onFieldIncludedChange = onOverrideIsoChange,
                    sourceHint = sourceExif?.iso,
                )
            }
        }
    }
}

internal val ExpandMoreIcon = Icons.Default.ExpandMore
internal val ExpandLessIcon = Icons.Default.ExpandLess
