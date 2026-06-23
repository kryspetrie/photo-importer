package org.kryspetrie.fileimport.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardStep

/**
 * Horizontal step indicator showing progress through the wizard flow.
 *
 * Displays all visible steps as a compact row of numbered circles:
 * completed steps are dimmed primary, the current step is highlighted primary,
 * and future steps are outlined. The REFINEMENT step is hidden since it
 * redirects to OVERVIEW inline.
 */
@Composable
fun WizardStepIndicator(
    currentStep: WizardStep,
    modifier: Modifier = Modifier,
) {
    val steps = VISIBLE_STEPS
    val currentIndex = steps.indexOf(currentStep).takeIf { it >= 0 } ?: 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = index < currentIndex
            val isCurrent = index == currentIndex

            StepDot(
                label = step.shortLabel,
                isCompleted = isCompleted,
                isCurrent = isCurrent,
            )

            if (index < steps.lastIndex) {
                Text(
                    "›",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index < currentIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** A single numbered step dot with filled or outlined styling. */
@Composable
private fun StepDot(
    label: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
) {
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimary
        isCompleted -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier.size(22.dp).clip(CircleShape),
        color = containerColor,
        shape = CircleShape,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(2.dp),
        )
    }
}

/** Visible steps for the indicator (excludes REFINEMENT which redirects to OVERVIEW). */
private val VISIBLE_STEPS = listOf(
    WizardStep.IMPORT,
    WizardStep.OVERVIEW,
    WizardStep.SUMMARY,
    WizardStep.EDIT,
    WizardStep.PROCESSING,
    WizardStep.COMPLETE,
)

/** Short labels for each step displayed inside the indicator dot. */
private val WizardStep.shortLabel: String
    get() = when (this) {
        WizardStep.IMPORT -> "1"
        WizardStep.OVERVIEW -> "2"
        WizardStep.REFINEMENT -> "2"
        WizardStep.SUMMARY -> "3"
        WizardStep.EDIT -> "4"
        WizardStep.PROCESSING -> "5"
        WizardStep.COMPLETE -> "6"
    }
