package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Minimum viewport width for configuration + work to sit side-by-side. */
val ConfigWorkSideBySideMinWidth: Dp = 960.dp

/** Preferred width of the work (actions / status) panel when side-by-side. */
val ConfigWorkPanelWidth: Dp = 340.dp

/** Max width of the combined config+work content when wide. */
val ConfigWorkContentMaxWidth: Dp = 1120.dp

/**
 * Two-region layout for top-level tabs:
 * - **configuration**: path, settings, history (does not include primary run controls)
 * - **work**: primary action button(s), progress, and run results
 *
 * When [sideBySideMinWidth] fits, panels sit side-by-side (config flexible, work fixed-ish on the
 * right). Otherwise work stacks below configuration.
 */
@Composable
fun ConfigWorkLayout(
    modifier: Modifier = Modifier,
    sideBySideMinWidth: Dp = ConfigWorkSideBySideMinWidth,
    workPanelWidth: Dp = ConfigWorkPanelWidth,
    contentMaxWidth: Dp = ConfigWorkContentMaxWidth,
    contentSpacing: Dp = 16.dp,
    configuration: @Composable ColumnScope.() -> Unit,
    work: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sideBySide = maxWidth >= sideBySideMinWidth
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (sideBySide) {
                Row(
                    modifier = Modifier.widthIn(max = contentMaxWidth).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(contentSpacing / 2),
                        content = configuration,
                    )
                    WorkPanel(
                        modifier = Modifier.width(workPanelWidth).widthIn(min = 280.dp, max = 400.dp)
                    ) {
                        work()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.widthIn(max = DefaultContentPaneMaxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(contentSpacing / 2),
                        content = configuration,
                    )
                    WorkPanel(modifier = Modifier.fillMaxWidth()) { work() }
                }
            }
        }
    }
}

/**
 * Framed work region for primary actions and run status. Keeps the "start execution" cluster
 * visually distinct from configuration / history content.
 */
@Composable
fun WorkPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Optional small heading inside a [WorkPanel] (e.g. "Actions" / "Status"). */
@Composable
fun WorkPanelHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Vertical gap that keeps a primary filled button block from secondary status content. */
@Composable
fun WorkPanelSectionSpacer() {
    Spacer(Modifier.height(4.dp))
}
