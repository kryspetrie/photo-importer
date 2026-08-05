@file:Suppress("MagicNumber")

package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kryspetrie.fileimport.domain.model.FaceRegion
import org.kryspetrie.fileimport.domain.model.RegionType
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.wizard.state.FaceSize

@Composable
internal fun FaceSelectorSidebar(
    faceRegions: List<FaceRegion>,
    inheritedFaceRegions: List<FaceRegion>,
    namingFaceIndex: Int,
    selectedRegionType: RegionType,
    selectedFaceSize: FaceSize,
    onRegionTypeChange: (RegionType) -> Unit,
    onFaceSizeChange: (FaceSize) -> Unit,
    onAutoDetectFaces: (() -> Unit)?,
    onClearAll: () -> Unit,
    onAdoptRegion: (FaceRegion) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = strings()
    Surface(
        modifier = Modifier.fillMaxHeight().width(220.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    s.t(StringKey.WIZARD_TAG_EDITOR),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(s.t(StringKey.META_DONE), style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider()
            Text(
                when {
                    namingFaceIndex in faceRegions.indices ->
                        s.t(
                            StringKey.WIZARD_TAG_PROGRESS,
                            "current" to (namingFaceIndex + 1).toString(),
                            "total" to faceRegions.size.toString(),
                            "named" to faceRegions.count { it.name.isNotBlank() }.toString(),
                        )
                    faceRegions.isEmpty() ->
                        s.t(
                            StringKey.WIZARD_CLICK_TO_TAG,
                            "type" to s.regionTypeName(selectedRegionType).lowercase(),
                        )
                    else -> s.t(StringKey.WIZARD_TAG_HINT)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Text(
                s.t(StringKey.WIZARD_REGION_TYPE),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RegionType.entries.forEach { type ->
                    RegionTypeOption(
                        type = type,
                        isSelected = selectedRegionType == type,
                        onClick = { onRegionTypeChange(type) },
                    )
                }
            }

            HorizontalDivider()
            Text(
                s.t(StringKey.WIZARD_SIZE),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceSize.entries.forEach { size ->
                    FaceSizeOption(
                        size = size,
                        isSelected = selectedFaceSize == size,
                        onClick = { onFaceSizeChange(size) },
                    )
                }
            }

            HorizontalDivider()
            if (onAutoDetectFaces != null) {
                SidebarAction(
                    text = s.t(StringKey.WIZARD_AUTO_DETECT_FACES),
                    icon = Icons.Default.Face,
                    iconDescription = s.t(StringKey.WIZARD_AUTO_DETECT),
                    color = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primaryContainer,
                    onClick = onAutoDetectFaces,
                )
            }
            if (faceRegions.isNotEmpty()) {
                SidebarAction(
                    text = s.t(StringKey.WIZARD_CLEAR_ALL),
                    icon = Icons.Default.Close,
                    iconDescription = s.t(StringKey.FIELD_CLEAR_ALL),
                    color = MaterialTheme.colorScheme.error,
                    background = MaterialTheme.colorScheme.errorContainer,
                    onClick = onClearAll,
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))
            if (inheritedFaceRegions.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    s.t(
                        StringKey.WIZARD_INHERITED,
                        "count" to inheritedFaceRegions.size.toString(),
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    s.t(StringKey.WIZARD_CLICK_TO_ADOPT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    inheritedFaceRegions.forEach { region ->
                        Surface(
                            modifier = Modifier.clickable { onAdoptRegion(region) },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    regionTypeIcon(RegionType.fromMwgRs(region.type)),
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                                Text(
                                    region.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionTypeOption(type: RegionType, isSelected: Boolean, onClick: () -> Unit) {
    val s = strings()
    val typeName = s.regionTypeName(type)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color =
            if (isSelected) regionTypeColor(type).copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant,
        border =
            if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, regionTypeColor(type))
            else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                regionTypeIcon(type),
                contentDescription = typeName,
                modifier = Modifier.size(14.dp),
                tint =
                    if (isSelected) regionTypeColor(type)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                typeName,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FaceSizeOption(size: FaceSize, isSelected: Boolean, onClick: () -> Unit) {
    val s = strings()
    val sizeName = s.faceSizeName(size)
    val circleColor =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color =
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.surfaceVariant,
        border =
            if (isSelected)
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(circleColor, (size.radius.toFloat() * 60f).coerceIn(2f, 5f))
            }
            Text(
                sizeName,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SidebarAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String,
    color: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = iconDescription,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Text(text, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
