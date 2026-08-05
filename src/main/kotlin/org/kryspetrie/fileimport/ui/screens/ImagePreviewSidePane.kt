package org.kryspetrie.fileimport.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.io.File
import java.util.Locale
import org.kryspetrie.fileimport.domain.model.ImageFile
import org.kryspetrie.fileimport.domain.model.ImageMetadata
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.ChunkyScrollbar
import org.kryspetrie.fileimport.ui.components.ThumbnailImage
import org.kryspetrie.fileimport.ui.components.formatFileSize
import org.kryspetrie.fileimport.ui.i18n.Strings
import org.kryspetrie.fileimport.ui.i18n.strings

@Composable
internal fun PreviewSidePane(
    image: ImageFile,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onFullScreen: () -> Unit,
) {
    val s = strings()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.t(StringKey.IMPORT_PREVIEW), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        s.t(StringKey.IMPORT_CLOSE_PREVIEW),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            PreviewImageWithHover(
                file = image.file,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onFullScreen = onFullScreen,
            )

            Spacer(Modifier.height(12.dp))

            ChunkyScrollbar {
                Column(modifier = Modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        image.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MetadataRow(s, StringKey.IMPORT_PATH, image.file.parent.orEmpty())
                    MetadataRow(s, StringKey.IMPORT_TYPE, image.fileType.displayName)
                    MetadataRow(s, StringKey.IMPORT_SIZE, formatFileSize(image.fileSize))
                    MetadataRow(s, StringKey.IMPORT_DATE, image.dateTakenFormatted)
                    image.metadata?.let { m ->
                        m.cameraModel
                            .takeIf { it.isNotBlank() }
                            ?.let { MetadataRow(s, StringKey.IMPORT_CAMERA, it) }
                        m.lensInfo
                            .takeIf { it != "Unknown" }
                            ?.let { MetadataRow(s, StringKey.IMPORT_LENS, it) }
                        if (m.imageWidth != null && m.imageHeight != null) {
                            MetadataRow(
                                s,
                                StringKey.IMPORT_DIMENSIONS,
                                "${m.imageWidth} \u00D7 ${m.imageHeight}",
                            )
                        }
                        m.durationFormatted?.let { MetadataRow(s, StringKey.IMPORT_DURATION, it) }
                        m.videoCodec?.let { MetadataRow(s, StringKey.IMPORT_CODEC, it) }
                        m.frameRate?.let {
                            MetadataRow(s, StringKey.IMPORT_FRAME_RATE, "%.1f fps".format(it))
                        }
                    }

                    image.metadata?.let { m ->
                        val detailEntries = buildDetailEntries(s, m, image.fileType.isVideo)
                        if (detailEntries.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            CollapsibleExifSection(
                                entries = detailEntries,
                                title =
                                    if (image.fileType.isVideo) s.t(StringKey.IMPORT_VIDEO_DETAILS)
                                    else s.t(StringKey.IMPORT_EXIF_DETAILS),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreviewImageWithHover(
    file: File,
    modifier: Modifier = Modifier,
    onFullScreen: () -> Unit,
) {
    val s = strings()
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .hoverable(hoverInteraction)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .clickable(onClick = onFullScreen),
        contentAlignment = Alignment.Center,
    ) {
        ThumbnailImage(
            file = file,
            maxPx = IMAGE_PREVIEW_MAX_PX,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // Hover overlay
        if (isHovered) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White.copy(alpha = 0.9f),
                    )
                    Text(
                        s.t(StringKey.IMPORT_CLICK_ENLARGE),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

internal fun buildDetailEntries(
    s: Strings,
    m: ImageMetadata,
    isVideo: Boolean,
): List<Pair<String, String>> {
    val specific = if (isVideo) buildVideoDetailEntries(s, m) else buildPhotoDetailEntries(s, m)
    return specific + buildCommonDetailEntries(s, m)
}

internal fun buildVideoDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> =
    buildList {
        m.durationFormatted?.let { add(s.t(StringKey.IMPORT_DURATION) to it) }
        m.frameRate?.let { add(s.t(StringKey.IMPORT_FRAME_RATE) to "%.1f fps".format(it)) }
        m.videoCodec?.let { add(s.t(StringKey.IMPORT_CODEC) to it) }
        m.audioCodec?.let { add(s.t(StringKey.FIELD_AUDIO_CODEC) to it) }
        m.bitrate?.let { add(s.t(StringKey.FIELD_BITRATE) to "${it / 1000} kbps") }
        m.rotation?.let { add(s.t(StringKey.FIELD_ROTATION) to "${it}\u00B0") }
    }

internal fun buildPhotoDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> =
    buildList {
        m.iso?.let { add(s.t(StringKey.FIELD_ISO) to it.toString()) }
        m.aperture?.let { add(s.t(StringKey.FIELD_APERTURE) to "f/$it") }
        m.shutterSpeed?.let { add(s.t(StringKey.FIELD_SHUTTER_SPEED) to it) }
        m.focalLength?.let {
            val text = buildString {
                append("${it}mm")
                m.focalLength35mm?.let { eq -> append(" (${eq}mm eq.)") }
            }
            add(s.t(StringKey.FIELD_FOCAL_LENGTH) to text)
        }
        m.exposureProgram?.let { add(s.t(StringKey.FIELD_EXPOSURE_PROGRAM) to it) }
        m.exposureCompensation?.let { add(s.t(StringKey.FIELD_EXPOSURE_COMP) to "${it} EV") }
        m.meteringMode?.let { add(s.t(StringKey.FIELD_METERING) to it) }
        m.flash?.let { add(s.t(StringKey.FIELD_FLASH) to it) }
        m.whiteBalance?.let { add(s.t(StringKey.FIELD_WHITE_BALANCE) to it) }
        m.colorSpace?.let { add(s.t(StringKey.FIELD_COLOR_SPACE) to it) }
        m.orientation?.let { add(s.t(StringKey.FIELD_ORIENTATION) to it.toString()) }
    }

internal fun buildCommonDetailEntries(s: Strings, m: ImageMetadata): List<Pair<String, String>> =
    buildList {
        m.software?.let { add(s.t(StringKey.FIELD_SOFTWARE) to it) }
        m.artist?.let { add(s.t(StringKey.FIELD_ARTIST) to it) }
        m.copyright?.let { add(s.t(StringKey.FIELD_COPYRIGHT) to it) }
        m.description?.let { add(s.t(StringKey.FIELD_DESCRIPTION) to it) }
        if (m.hasGpsData) {
            add(s.t(StringKey.FIELD_LAT) to String.format(Locale.US, "%.6f", m.latitude))
            add(s.t(StringKey.FIELD_LON) to String.format(Locale.US, "%.6f", m.longitude))
            m.altitude?.let {
                add(s.t(StringKey.FIELD_ALTITUDE) to String.format(Locale.US, "%.1fm", it))
            }
        }
        m.dateTimeDigitized?.let { add(s.t(StringKey.FIELD_DATE_DIGITIZED) to it.toString()) }
        m.dateTimeModified?.let { add(s.t(StringKey.META_MODIFIED) to it.toString()) }
    }

@Composable
internal fun CollapsibleExifSection(entries: List<Pair<String, String>>, title: String) {
    val s = strings()
    var expanded by remember { mutableStateOf(false) }

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(2.dp))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                s.t(StringKey.IMPORT_TOGGLE_DETAILS),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$title (${entries.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                entries.forEach { (label, value) -> MetadataRow(label, value) }
            }
        }
    }
}

@Composable
internal fun MetadataRow(s: Strings, labelKey: StringKey, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "${s.t(labelKey)}: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
