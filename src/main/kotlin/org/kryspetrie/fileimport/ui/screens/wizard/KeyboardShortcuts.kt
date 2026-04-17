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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.lang.reflect.Method
import org.kryspetrie.fileimport.infrastructure.wizard.PhotoScanWizardState
import org.kryspetrie.fileimport.infrastructure.wizard.WizardMode

/**
 * Modifier that adds keyboard shortcuts for the Photo Scan Wizard.
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
 * - **U**: Undo last action
 * - **O**: Redo (Opposite of undo)
 *
 * ### Navigation
 * - **Enter**: Confirm / Proceed to next step
 * - **N**: Next box (in refinement)
 * - **P**: Previous box (in refinement)
 *
 * @param wizardState The wizard state to manipulate
 * @param onProceed Callback when user wants to proceed (Enter key)
 * @param onCancel Callback when user wants to cancel (Escape)
 * @param onModeChange Callback when mode changes
 */
fun Modifier.withWizardKeyboardShortcuts(
    wizardState: PhotoScanWizardState,
    onProceed: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onModeChange: ((WizardMode) -> Unit)? = null
): Modifier =
    this.onKeyEvent { keyEvent ->
      // Only handle key press events (not key release)
      if (keyEvent.type != KeyEventType.KeyDown) {
        return@onKeyEvent false
      }

      // Check shift key using reflection (since the property access varies across platforms)
      val isShiftPressed = checkShiftPressed(keyEvent)
      
      // Use reflection to get the key since the property access is causing issues
      val keyName = getKeyName(keyEvent)

      // Arrow keys handling - check if corner is selected
      val cornerDelta = if (isShiftPressed) 10.0 else 1.0
      val panDelta = if (isShiftPressed) 100.0 else 50.0
      
      when (keyName) {
        "DirectionUp" -> {
          if (wizardState.selectedCorner.value != null) {
            wizardState.moveSelectedCorner(0.0, -cornerDelta)
          } else {
            wizardState.pan(0.0, panDelta)
          }
          return@onKeyEvent true
        }
        "DirectionDown" -> {
          if (wizardState.selectedCorner.value != null) {
            wizardState.moveSelectedCorner(0.0, cornerDelta)
          } else {
            wizardState.pan(0.0, -panDelta)
          }
          return@onKeyEvent true
        }
        "DirectionLeft" -> {
          if (wizardState.selectedCorner.value != null) {
            wizardState.moveSelectedCorner(-cornerDelta, 0.0)
          } else {
            wizardState.pan(panDelta, 0.0)
          }
          return@onKeyEvent true
        }
        "DirectionRight" -> {
          if (wizardState.selectedCorner.value != null) {
            wizardState.moveSelectedCorner(cornerDelta, 0.0)
          } else {
            wizardState.pan(-panDelta, 0.0)
          }
          return@onKeyEvent true
        }
        "Delete",
        "Backspace" -> {
          wizardState.removeSelectedBox()
          return@onKeyEvent true
        }
        "Escape" -> {
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
        "F" -> {
          if (wizardState.wizardMode.value != WizardMode.REFINEMENT) {
            wizardState.enterFourPointMode()
            onModeChange?.invoke(WizardMode.FOUR_POINT)
          }
          return@onKeyEvent true
        }
        "R" -> {
          if (wizardState.selectedBoxIndex.value >= 0) {
            wizardState.enterRefinement(wizardState.selectedBoxIndex.value)
          }
          return@onKeyEvent true
        }
        "A" -> {
          if (wizardState.wizardMode.value != WizardMode.REFINEMENT) {
            wizardState.enterAddBoxMode()
            onModeChange?.invoke(WizardMode.ADD_BOX)
          }
          return@onKeyEvent true
        }
        "U" -> {
          wizardState.undo()
          return@onKeyEvent true
        }
        "O" -> {
          wizardState.redo()
          return@onKeyEvent true
        }
        "Enter" -> {
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
        "Equals" -> {
          wizardState.zoomIn()
          return@onKeyEvent true
        }
        "Minus" -> {
          wizardState.zoomOut()
          return@onKeyEvent true
        }
        "Zero" -> {
          wizardState.fitToView()
          return@onKeyEvent true
        }
        "Home" -> {
          wizardState.fitToView()
          return@onKeyEvent true
        }
        "N" -> {
          if (wizardState.refinementBoxIndex.value >= 0) {
            wizardState.nextBox()
          }
          return@onKeyEvent true
        }
        "P" -> {
          if (wizardState.refinementBoxIndex.value >= 0) {
            wizardState.previousBox()
          }
          return@onKeyEvent true
        }
        "Comma" -> {
          if (wizardState.refinementBoxIndex.value >= 0) {
            wizardState.rotateBox(wizardState.refinementBoxIndex.value, -5.0)
          }
          return@onKeyEvent true
        }
        "Period" -> {
          if (wizardState.refinementBoxIndex.value >= 0) {
            wizardState.rotateBox(wizardState.refinementBoxIndex.value, 5.0)
          }
          return@onKeyEvent true
        }
        "Tab" -> {
          cycleCorner(wizardState)
          return@onKeyEvent true
        }
      }

      false
    }

/** Get key name from KeyEvent using reflection as a workaround. */
private fun getKeyName(keyEvent: Any): String {
  try {
    // Try to get the key property via reflection
    val keyMethod: Method = keyEvent.javaClass.getMethod("getKey")
    val key = keyMethod.invoke(keyEvent)
    val nameMethod: Method = key.javaClass.getMethod("getKeyName")
    return nameMethod.invoke(key) as String
  } catch (e: Exception) {
    return ""
  }
}

/** Check if shift key is pressed using reflection. */
private fun checkShiftPressed(keyEvent: Any): Boolean {
  try {
    // Try to get the modifiers property
    val modifiersMethod: Method = keyEvent.javaClass.getMethod("getModifiers")
    val modifiers = modifiersMethod.invoke(keyEvent) as Int
    // Shift key flag is 1 << 16
    val shiftMask = 1 shl 16
    return (modifiers and shiftMask) != 0
  } catch (e: Exception) {
    // Fallback: try native key event
    try {
      val nativeMethod: Method = keyEvent.javaClass.getMethod("getNativeKeyEvent")
      val native = nativeMethod.invoke(keyEvent)
      val isShiftMethod: Method = native.javaClass.getMethod("isShiftDown")
      return isShiftMethod.invoke(native) as Boolean
    } catch (e2: Exception) {
      return false
    }
  }
}

/** Cycles through the corners of the selected box. */
private fun cycleCorner(state: PhotoScanWizardState) {
  val currentCorner = state.selectedCorner.value
  val corners = org.kryspetrie.fileimport.infrastructure.wizard.Corner.entries

  if (currentCorner == null) {
    // Select first corner
    state.selectCorner(corners.first())
  } else {
    val currentIndex = corners.indexOf(currentCorner)
    val nextIndex = (currentIndex + 1) % corners.size
    state.selectCorner(corners[nextIndex])
  }
}

/**
 * Shows a keyboard shortcut help dialog overlay.
 *
 * This dialog explains all available keyboard shortcuts in the wizard screens.
 *
 * ## Shortcuts Documented
 *
 * ### Navigation
 * - Enter: Confirm / Proceed to next step
 * - Escape: Cancel / Back / Exit mode
 *
 * ### Box Selection
 * - Click: Select box
 * - Tab: Cycle through corners
 *
 * ### Box Manipulation
 * - Arrow keys: Move selected corner (1px)
 * - Delete: Delete selected box
 *
 * ### Modes
 * - F: 4-point mode (manual box creation)
 * - A: Add box mode
 * - R: Refinement mode
 *
 * ### Zoom
 * - + / -: Zoom in / out
 * - 0 or Home: Fit to view
 *
 * ### Undo/Redo
 * - U: Undo
 * - O: Redo
 */
@Composable
fun KeyboardShortcutHelpDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = modifier,
      title = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Keyboard, contentDescription = null)
              Text("Keyboard Shortcuts")
            }
      },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              // Navigation section
              ShortcutSection(title = "Navigation") {
                ShortcutRow("Enter", "Confirm / Proceed")
                ShortcutRow("Escape", "Cancel / Back / Exit mode")
              }

              HorizontalDivider()

              // Selection section
              ShortcutSection(title = "Selection") {
                ShortcutRow("Tab", "Cycle through corners")
                ShortcutRow("Shift+Arrows", "Move corner 10px")
              }

              HorizontalDivider()

              // Manipulation section
              ShortcutSection(title = "Box Manipulation") {
                ShortcutRow("Arrow keys", "Move selected corner (1px)")
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

              // Undo/Redo section
              ShortcutSection(title = "Undo/Redo") {
                ShortcutRow("U", "Undo")
                ShortcutRow("O", "Redo")
              }

              HorizontalDivider()

              // Overview section
              ShortcutSection(title = "Overview") {
                ShortcutRow("O", "Open overview")
                ShortcutRow("H", "Show help")
              }
            }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun ShortcutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
    content()
  }
}

@Composable
private fun ShortcutRow(shortcutKey: String, description: String) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Start,
      verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.padding(end = 8.dp)) {
              Text(
                  text = shortcutKey,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold)
            }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = description, style = MaterialTheme.typography.bodySmall)
      }
}
