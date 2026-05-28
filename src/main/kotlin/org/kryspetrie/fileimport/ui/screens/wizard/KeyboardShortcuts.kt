package org.kryspetrie.fileimport.ui.screens.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanConstants
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode

/** Context for which shortcuts are available, used to show context-aware help. */
/**
 * Modifier that adds keyboard shortcuts for the Photo Scan Wizard.
 *
 * Uses Compose [Key] constants for reliable cross-platform key detection instead of reflection.
 *
 * ## Keyboard Shortcuts
 *
 * ### Arrow Keys
 * - **Arrow Keys**: Move selected corner by 1px (or pan view if no corner selected)
 * - **Shift+Arrow Keys**: Move selected corner by 10px (or pan view faster if no corner selected)
 *
 * ### Zoom
 * - **+**: Zoom in
 * - **-**: Zoom out
 * - **0**: Reset zoom to fit
 *
 * ### Box Operations
 * - **Delete / Backspace**: Delete selected box
 * - **Escape**: Deselect / Exit current mode / Cancel operation
 *
 * ### Mode Switching
 * - **F**: Enter 4-point mode for manual box creation
 * - **R**: Enter refinement mode for selected box
 * - **A**: Enter add box mode
 *
 * ### Undo/Redo
 * - **Ctrl+Z**: Undo last action
 * - **Ctrl+Shift+Z**: Redo
 *
 * ### Navigation
 * - **Enter**: Confirm / Proceed to next step
 * - **N**: Next corner (when selected) / Next box (in refinement)
 * - **P**: Previous corner (when selected) / Previous box (in refinement)
 *
 * ### Corner Cycling
 * - **N / P**: Cycle through corners (forward / backward) when a corner is selected
 *
 * @param wizardState The wizard state to manipulate
 * @param onProceed Callback when user wants to proceed (Enter key)
 * @param onCancel Callback when user wants to cancel (Escape)
 * @param onModeChange Callback when mode changes
 * @param context The screen context — controls which shortcuts are active
 */
fun Modifier.withWizardKeyboardShortcuts(
    wizardState: PhotoScanWizardState,
    onProceed: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onModeChange: ((WizardMode) -> Unit)? = null,
    context: ShortcutContext = ShortcutContext.CANVAS,
): Modifier =
    this.onKeyEvent { keyEvent ->
        // Only handle key press events (not key release)
        if (keyEvent.type != KeyEventType.KeyDown) {
            return@onKeyEvent false
        }

        val isShiftPressed = keyEvent.isShiftPressed
        val isCtrlPressed = isCtrlPressed(keyEvent)

        // Arrow keys handling — check if a corner is selected
        val cornerDelta =
            if (isShiftPressed) PhotoScanConstants.SHIFT_ARROW_KEY_STEP
            else PhotoScanConstants.ARROW_KEY_STEP
        val panDelta =
            if (isShiftPressed) PhotoScanConstants.SHIFT_KEYBOARD_PAN_STEP
            else PhotoScanConstants.KEYBOARD_PAN_STEP

        // Canvas-only shortcuts (Overview & Refinement screens)
        if (context == ShortcutContext.CANVAS) {
            when (keyEvent.key) {
                Key.DirectionUp -> {
                    if (wizardState.selectedCorner.value != null) {
                        wizardState.moveSelectedCorner(0.0, -cornerDelta)
                    } else {
                        wizardState.pan(0.0, panDelta)
                    }
                    return@onKeyEvent true
                }
                Key.DirectionDown -> {
                    if (wizardState.selectedCorner.value != null) {
                        wizardState.moveSelectedCorner(0.0, cornerDelta)
                    } else {
                        wizardState.pan(0.0, -panDelta)
                    }
                    return@onKeyEvent true
                }
                Key.DirectionLeft -> {
                    if (wizardState.selectedCorner.value != null) {
                        wizardState.moveSelectedCorner(-cornerDelta, 0.0)
                    } else {
                        wizardState.pan(panDelta, 0.0)
                    }
                    return@onKeyEvent true
                }
                Key.DirectionRight -> {
                    if (wizardState.selectedCorner.value != null) {
                        wizardState.moveSelectedCorner(cornerDelta, 0.0)
                    } else {
                        wizardState.pan(-panDelta, 0.0)
                    }
                    return@onKeyEvent true
                }
                Key.Delete,
                Key.Backspace -> {
                    wizardState.removeSelectedBox()
                    return@onKeyEvent true
                }
                Key.F -> {
                    if (wizardState.wizardMode.value != WizardMode.REFINEMENT) {
                        wizardState.enterFourPointMode()
                        onModeChange?.invoke(WizardMode.FOUR_POINT)
                    }
                    return@onKeyEvent true
                }
                Key.R -> {
                    if (wizardState.selectedBoxIndex.value >= 0) {
                        wizardState.enterRefinement(wizardState.selectedBoxIndex.value)
                    }
                    return@onKeyEvent true
                }
                Key.A -> {
                    if (wizardState.wizardMode.value != WizardMode.REFINEMENT) {
                        wizardState.enterRectangleMode()
                        onModeChange?.invoke(WizardMode.FOUR_POINT)
                    }
                    return@onKeyEvent true
                }
                Key.Equals,
                Key.Plus -> {
                    wizardState.zoomIn()
                    return@onKeyEvent true
                }
                Key.Minus -> {
                    wizardState.zoomOut()
                    return@onKeyEvent true
                }
                Key.Zero,
                Key.MoveHome -> {
                    wizardState.fitToView()
                    return@onKeyEvent true
                }
                Key.N -> {
                    // Context-aware: cycle corners forward when one is selected, otherwise next box
                    if (wizardState.selectedCorner.value != null) {
                        cycleCorner(wizardState, reverse = false)
                    } else {
                        val current = wizardState.selectedBoxIndex.value
                        val count = wizardState.boxCount()
                        if (count > 0 && current >= 0) {
                            wizardState.selectBox((current + 1) % count)
                        }
                    }
                    return@onKeyEvent true
                }
                Key.P -> {
                    // Context-aware: cycle corners backward when one is selected, otherwise
                    // previous box
                    if (wizardState.selectedCorner.value != null) {
                        cycleCorner(wizardState, reverse = true)
                    } else {
                        val current = wizardState.selectedBoxIndex.value
                        val count = wizardState.boxCount()
                        if (count > 0 && current >= 0) {
                            wizardState.selectBox((current - 1 + count) % count)
                        }
                    }
                    return@onKeyEvent true
                }
                // Tab is not consumed — passes through for focus traversal
                else -> {
                    /* handled below */
                }
            }
        }

        // Shortcuts available on all screens
        when (keyEvent.key) {
            Key.Escape -> {
                when (wizardState.wizardMode.value) {
                    WizardMode.FOUR_POINT -> {
                        wizardState.exitFourPointMode()
                    }
                    WizardMode.ADD_BOX -> {
                        wizardState.exitAddBoxMode()
                    }
                    else -> {
                        if (wizardState.selectedBoxIndex.value >= 0) {
                            wizardState.deselectAll()
                        } else {
                            onCancel?.invoke()
                        }
                    }
                }
                return@onKeyEvent true
            }
            Key.Enter -> {
                when (wizardState.wizardMode.value) {
                    WizardMode.FOUR_POINT -> {
                        if (wizardState.fourPointState.value.canConfirm()) {
                            wizardState.confirmFourPoint()
                        }
                    }
                    else -> {
                        onProceed?.invoke()
                    }
                }
                return@onKeyEvent true
            }
            Key.Z -> {
                // Ctrl+Z = Undo, Ctrl+Shift+Z = Redo
                if (isCtrlPressed) {
                    if (isShiftPressed) {
                        wizardState.redo()
                    } else {
                        wizardState.undo()
                    }
                    return@onKeyEvent true
                }
                // Without Ctrl, fall through (let 'Z' be typed in text fields)
                return@onKeyEvent false
            }
            else -> {}
        }

        false
    }

/** Checks if Ctrl (or Cmd on macOS) is pressed using reflection for cross-platform support. */
private fun isCtrlPressed(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
    return try {
        val nativeMethod = keyEvent.javaClass.getMethod("getNativeKeyEvent")
        val native = nativeMethod.invoke(keyEvent)
        val isMetaDownMethod = native.javaClass.getMethod("isMetaDown")
        val isCtrlDownMethod = native.javaClass.getMethod("isControlDown")
        isMetaDownMethod.invoke(native) as Boolean || isCtrlDownMethod.invoke(native) as Boolean
    } catch (_: Exception) {
        false
    }
}

/** Cycles through the corners of the selected box. */
private fun cycleCorner(state: PhotoScanWizardState, reverse: Boolean = false) {
    val currentCorner = state.selectedCorner.value
    val corners = org.kryspetrie.fileimport.infrastructure.wizard.Corner.entries

    if (currentCorner == null) {
        state.selectCorner(if (reverse) corners.last() else corners.first())
    } else {
        val currentIndex = corners.indexOf(currentCorner)
        val nextIndex =
            if (reverse) {
                (currentIndex - 1).coerceAtLeast(0)
            } else {
                (currentIndex + 1) % corners.size
            }
        state.selectCorner(corners[nextIndex])
    }
}

/**
 * Shows a keyboard shortcut help dialog overlay.
 *
 * Context-aware: shows only shortcuts relevant to the current screen.
 *
 * @param context The screen context to tailor displayed shortcuts
 */
@Composable
fun KeyboardShortcutHelpDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    context: ShortcutContext = ShortcutContext.CANVAS,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Text("Keyboard Shortcuts")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Navigation section — always shown
                ShortcutSection(title = "Navigation") {
                    ShortcutRow("Enter", "Confirm / Proceed")
                    ShortcutRow("Escape", "Cancel / Back / Exit mode")
                }

                if (context == ShortcutContext.CANVAS) {
                    HorizontalDivider()

                    // Selection section
                    ShortcutSection(title = "Selection") {
                        ShortcutRow("N", "Next corner / Next box")
                        ShortcutRow("P", "Previous corner / Previous box")
                        ShortcutRow("Shift+Arrows", "Move corner 10px / Pan faster")
                    }

                    HorizontalDivider()

                    // Manipulation section
                    ShortcutSection(title = "Box Manipulation") {
                        ShortcutRow("Arrow keys", "Move selected corner 1px / Pan view")
                        ShortcutRow("Delete", "Delete selected box")
                    }

                    HorizontalDivider()

                    // Modes section
                    ShortcutSection(title = "Modes") {
                        ShortcutRow("F", "4-point mode (manual box creation)")
                        ShortcutRow("A", "Add box mode")
                        ShortcutRow("R", "Refinement mode")
                    }

                    HorizontalDivider()

                    // Zoom section
                    ShortcutSection(title = "Zoom") {
                        ShortcutRow("+ / -", "Zoom in / out")
                        ShortcutRow("0 or Home", "Fit to view")
                    }

                    HorizontalDivider()

                    // Refinement section
                    ShortcutSection(title = "Refinement") {
                        ShortcutRow(", / .", "Rotate -5\u00B0 / +5\u00B0")
                    }
                }

                HorizontalDivider()

                // Undo/Redo section — always shown
                ShortcutSection(title = "Undo/Redo") {
                    ShortcutRow("Ctrl+Z", "Undo")
                    ShortcutRow("Ctrl+Shift+Z", "Redo")
                }

                HorizontalDivider()

                // Accessibility section — always shown
                ShortcutSection(title = "Accessibility") {
                    ShortcutRow("Tab", "Move focus between controls")
                    ShortcutRow("Shift+Tab", "Move focus backward")
                    ShortcutRow("Space / Enter", "Activate focused button")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ShortcutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun ShortcutRow(shortcutKey: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            Text(
                text = shortcutKey,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodySmall)
    }
}
