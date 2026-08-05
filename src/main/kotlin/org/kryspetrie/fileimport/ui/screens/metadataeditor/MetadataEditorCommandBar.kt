package org.kryspetrie.fileimport.ui.screens.metadataeditor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.domain.model.i18n.StringKey
import org.kryspetrie.fileimport.ui.components.FolderSelectionField
import org.kryspetrie.fileimport.ui.i18n.strings
import org.kryspetrie.fileimport.ui.theme.LocalUiDensityScale
import org.kryspetrie.fileimport.ui.theme.UiDensityDefaults

@Composable
fun MetadataEditorCommandBar(
    state: BulkEditState,
    vm: MetadataEditorViewModel,
    autoOrientEnabled: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleBrowserDrawer: () -> Unit,
    onAutoRotate: () -> Unit,
    onBulkSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings()
    val density = LocalUiDensityScale.current
    val barHeight = density.commandBarHeight
    val scrollState = rememberScrollState()

    Surface(tonalElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < UiDensityDefaults.metadataEditorNarrowBreakpoint
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.height(barHeight)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        s.t(StringKey.ACTION_BACK),
                        Modifier.size(density.iconSize),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(s.t(StringKey.ACTION_BACK), style = MaterialTheme.typography.labelSmall)
                }

                if (isNarrow) {
                    OutlinedButton(
                        onClick = onToggleBrowserDrawer,
                        modifier = Modifier.height(barHeight),
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            s.t(StringKey.META_BROWSER_TOGGLE),
                            Modifier.size(density.iconSize),
                        )
                    }
                }

                val breadcrumb =
                    when {
                        vm.isMultiEditMode && vm.selectedIndices.size > 1 ->
                            s.t(
                                StringKey.META_PHOTOS_SELECTED,
                                "count" to vm.selectedIndices.size.toString(),
                            )
                        state.selectedFile != null -> state.selectedFile!!.name
                        else -> state.sourcePath.substringAfterLast('/')
                    }
                Text(
                    breadcrumb,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(140.dp),
                )

                RadioButton(
                    selected = state.outputMode == OutputMode.OVERWRITE,
                    onClick = { state.outputMode = OutputMode.OVERWRITE },
                    modifier = Modifier.size(20.dp),
                )
                Text(s.t(StringKey.META_OVERWRITE), style = MaterialTheme.typography.labelSmall)
                RadioButton(
                    selected = state.outputMode == OutputMode.SAVE_NEW,
                    onClick = { state.outputMode = OutputMode.SAVE_NEW },
                    modifier = Modifier.size(20.dp),
                )
                Text(s.t(StringKey.META_SAVE_NEW), style = MaterialTheme.typography.labelSmall)
                if (state.outputMode == OutputMode.SAVE_NEW) {
                    FolderSelectionField(
                        value = state.outputDirectory,
                        onValueChange = { state.outputDirectory = it },
                        modifier = Modifier.width(180.dp).height(barHeight),
                        label = s.t(StringKey.META_OUTPUT_LABEL),
                        placeholder = s.t(StringKey.META_OUTPUT_PLACEHOLDER),
                        title = s.t(StringKey.META_DIALOG_SELECT_OUTPUT),
                    )
                }

                if (vm.isMultiEditMode) {
                    OutlinedButton(onClick = onBulkSelect, modifier = Modifier.height(barHeight)) {
                        Text(
                            s.t(StringKey.META_SELECT_ELLIPSIS),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (autoOrientEnabled && state.fileCount > 0 && !vm.isDetectingOrientation) {
                    OutlinedButton(onClick = onAutoRotate, modifier = Modifier.height(barHeight)) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            s.t(StringKey.ACC_AUTO_ROTATE),
                            Modifier.size(density.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.META_AUTO_ROTATE_ELLIPSIS),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (autoOrientEnabled && vm.isDetectingOrientation) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }

                if (state.canUndo) {
                    OutlinedButton(onClick = onUndo, modifier = Modifier.height(barHeight)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            s.t(StringKey.META_UNDO),
                            Modifier.size(density.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.t(StringKey.META_UNDO), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (state.canRedo) {
                    OutlinedButton(onClick = onRedo, modifier = Modifier.height(barHeight)) {
                        Icon(
                            Icons.AutoMirrored.Filled.Redo,
                            s.t(StringKey.META_REDO),
                            Modifier.size(density.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.t(StringKey.META_REDO), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (state.modifiedCount > 1) {
                    Button(onClick = onSaveAll, modifier = Modifier.height(barHeight)) {
                        Icon(
                            Icons.Default.Save,
                            s.t(StringKey.ACC_SAVE_ALL),
                            Modifier.size(density.iconSize),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.t(StringKey.META_SAVE_ALL, "count" to state.modifiedCount.toString()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = state.selectedFile != null,
                    modifier = Modifier.height(barHeight),
                ) {
                    Icon(
                        Icons.Default.Save,
                        s.t(StringKey.META_SAVE_BUTTON),
                        Modifier.size(density.iconSize),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        s.t(StringKey.META_SAVE_BUTTON),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                OutlinedButton(
                    onClick = onPrev,
                    enabled = state.selectedIndex > 0,
                    modifier = Modifier.height(barHeight),
                ) {
                    Text(s.t(StringKey.ACTION_PREV), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = state.selectedIndex < state.fileCount - 1,
                    modifier = Modifier.height(barHeight),
                ) {
                    Text(s.t(StringKey.ACTION_NEXT), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        Modifier.size(density.iconSize),
                    )
                }

                state.message?.let { msg ->
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (msg.severity) {
                                MessageSeverity.ERROR -> MaterialTheme.colorScheme.error
                                MessageSeverity.INFO -> MaterialTheme.colorScheme.primary
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(180.dp),
                    )
                }
            }
        }
    }
}
