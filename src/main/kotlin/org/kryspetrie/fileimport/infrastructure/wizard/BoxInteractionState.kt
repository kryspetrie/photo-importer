package org.kryspetrie.fileimport.infrastructure.wizard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kryspetrie.fileimport.infrastructure.logging.AppLogger
import org.kryspetrie.fileimport.infrastructure.logging.OperationType

/**
 * Manages bounding box interaction state: selection, refinement, drag throttle, CRUD operations,
 * move/resize/rotate, and undo/redo. Extracted from [PhotoScanWizardState] to separate box
 * interaction concerns from navigation, batch, and configuration state.
 *
 * @param _boundingBoxList Shared reference to the bounding box list (mutated by this class and others).
 * @param _undoRedoManager Shared undo/redo manager.
 * @param _undoRedoVersion Shared version counter for undo/redo (incremented on undo/redo ops).
 */
class BoxInteractionState(
    private val _boundingBoxList: MutableStateFlow<BoundingBoxList>,
    private val _undoRedoManager: UndoRedoManager<BoundingBox>,
    private val _undoRedoVersion: MutableStateFlow<Int>,
) {
    /** Logger for operation tracking. Set externally. */
    var appLogger: AppLogger? = null

    // ========== Box Selection State ==========

    private val _selectedBoxIndex = MutableStateFlow(-1)
    val selectedBoxIndex: StateFlow<Int> = _selectedBoxIndex.asStateFlow()

    private val _refinementBoxIndex = MutableStateFlow(-1)
    val refinementBoxIndex: StateFlow<Int> = _refinementBoxIndex.asStateFlow()

    private val _selectedCorner = MutableStateFlow<Corner?>(null)
    val selectedCorner: StateFlow<Corner?> = _selectedCorner.asStateFlow()

    // ========== Throttled Drag State (4Hz for performance) ==========

    private val _displayRefinementBox = MutableStateFlow<BoundingBox?>(null)
    val displayRefinementBox: StateFlow<BoundingBox?> = _displayRefinementBox.asStateFlow()

    private val _pendingDragX = MutableStateFlow(0.0)
    val pendingDragX: Double
        get() = _pendingDragX.value

    private val _pendingDragY = MutableStateFlow(0.0)
    val pendingDragY: Double
        get() = _pendingDragY.value

    /** True when user is actively dragging a corner (used for 4Hz throttle loop) */
    val isDragging: Boolean
        get() = _selectedCorner.value != null && _refinementBoxIndex.value >= 0

    /** True when there's a pending drag position to sync */
    val hasPendingDrag: Boolean
        get() = _selectedCorner.value != null

    /** Updates the pending drag position (called on every Move event) */
    fun updatePendingDrag(newX: Double, newY: Double) {
        _pendingDragX.value = newX
        _pendingDragY.value = newY
    }

    /** Syncs the display state to show the actual box (called after drag ends). */
    fun syncDisplayBox() {
        val index = _refinementBoxIndex.value
        if (index >= 0 && index < _boundingBoxList.value.size()) {
            _displayRefinementBox.value = _boundingBoxList.value.boxes[index]
        } else {
            _displayRefinementBox.value = null
        }
    }

    /**
     * Syncs the pending drag position to the display state at 4Hz. This is a visual preview only -
     * actual box state updated on release. Called periodically from the LaunchedEffect throttle
     * loop.
     */
    fun syncPendingDrag(boxIndex: Int): Long {
        val startNanos = if (DEBUG_TIMING) System.nanoTime() else 0L

        val corner = _selectedCorner.value ?: return 0
        val imgX = _pendingDragX.value
        val imgY = _pendingDragY.value

        val list = _boundingBoxList.value
        if (boxIndex < 0 || boxIndex >= list.size()) return 0

        val box = list.boxes[boxIndex]
        val moved = box.moveCorner(corner, Point(imgX, imgY))

        // Validate to prevent rendering invalid shapes
        if (moved.corners.wouldCreateInvalidShape()) {
            return 0
        }

        _displayRefinementBox.value = moved

        val elapsed = if (DEBUG_TIMING) (System.nanoTime() - startNanos) / 1000 else 0L
        if (DEBUG_TIMING && elapsed > 500) { // Only log if > 0.5ms
            println("⚠️ syncPendingDrag: ${elapsed}μs (boxIndex=$boxIndex)")
        }
        return elapsed
    }

    // ========== Box CRUD ==========

    /** Adds a bounding box. Respects overlap detection from [BoundingBoxList]. */
    fun addBox(box: BoundingBox): Boolean {
        val currentList = _boundingBoxList.value
        if (!currentList.canAdd(box)) {
            appLogger?.warn("Box not added: overlaps with existing box")
            return false
        }
        _boundingBoxList.value = currentList.copy(boxes = currentList.boxes + box)
        appLogger?.logOperationComplete(
            OperationType.BOX_CREATION,
            "Box ${currentList.size() + 1} at (${box.center().x.toInt()}, " +
                "${box.center().y.toInt()}), size: ${box.width().toInt()}x${box.height().toInt()}",
        )
        return true
    }

    /** Removes a bounding box by index. */
    fun removeBox(index: Int) {
        val list = _boundingBoxList.value
        if (index >= 0 && index < list.size()) {
            val box = list.boxes[index]
            val boxId = box.id
            // Save for undo
            _undoRedoManager.push(box.id, box)
            // Remove
            _boundingBoxList.value = list.remove(boxId)

            // Clear selection if this was selected
            if (_selectedBoxIndex.value == index) {
                _selectedBoxIndex.value = -1
            }
            if (_refinementBoxIndex.value == index) {
                _refinementBoxIndex.value = -1
            }
            appLogger?.logOperationComplete(OperationType.BOX_DELETION, "Removed box $boxId")
        }
    }

    /** Updates a bounding box at the given index. */
    fun updateBox(index: Int, box: BoundingBox) {
        val list = _boundingBoxList.value
        _boundingBoxList.value = list.updateAt(index) { box }
    }

    /** Selects a box at the given index. */
    fun selectBox(index: Int) {
        _boundingBoxList.value = _boundingBoxList.value.selectAt(index)
        _selectedBoxIndex.value = index
    }

    /** Deselects all boxes and clears corner selection. */
    fun deselectAll() {
        _boundingBoxList.value = _boundingBoxList.value.deselectAll()
        _selectedBoxIndex.value = -1
        _selectedCorner.value = null
    }

    /** Selects a corner for arrow key movement. */
    fun selectCorner(corner: Corner) {
        _selectedCorner.value = corner
    }

    /** Deselects the current corner. */
    fun deselectCorner() {
        _selectedCorner.value = null
    }

    // ========== Box Manipulation ==========

    /** Moves the selected box by the given delta (pushes to undo stack). */
    fun moveSelectedBox(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move
            val moved = box.move(deltaX, deltaY)
            updateBox(index, moved)
        }
    }

    /** Moves the selected box by the given delta without saving to undo (for drag intermediate). */
    fun moveSelectedBoxWithoutUndo(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            val moved = box.move(deltaX, deltaY)
            updateBox(index, moved)
        }
    }

    /** Moves a specific corner of the box at the given index (pushes to undo stack). */
    fun moveCorner(boxIndex: Int, corner: Corner, newX: Double, newY: Double) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move corner
            val moved = box.moveCorner(corner, Point(newX, newY))
            updateBox(boxIndex, moved)
        }
    }

    /** Moves a corner without saving to undo (for drag intermediate frames). */
    fun moveCornerWithoutUndo(boxIndex: Int, corner: Corner, newX: Double, newY: Double) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]
            val moved = box.moveCorner(corner, Point(newX, newY))
            if (!moved.corners.wouldCreateInvalidShape()) {
                updateBox(boxIndex, moved)
            }
        }
    }

    /**
     * Moves a corner with validation to prevent invalid shapes (bowties, self-intersecting).
     * Returns true if the move was applied, false if it was rejected. Pushes to undo stack.
     */
    fun moveCornerWithValidation(
        boxIndex: Int,
        corner: Corner,
        newX: Double,
        newY: Double,
    ): Boolean {
        val list = _boundingBoxList.value
        if (boxIndex < 0 || boxIndex >= list.size()) return false

        val box = list.boxes[boxIndex]
        val moved = box.moveCorner(corner, Point(newX, newY))

        // Check if the resulting shape would be valid (no crossing edges)
        if (moved.corners.wouldCreateInvalidShape()) {
            return false
        }

        // Save for undo
        _undoRedoManager.push(box.id, box)

        // Apply the move
        updateBox(boxIndex, moved)
        return true
    }

    /**
     * Saves the current state of a box to the undo stack. Call this once at the start of a drag
     * operation, then use moveCornerWithoutUndo/moveSelectedBoxWithoutUndo for intermediate frames.
     */
    fun saveBoxUndoSnapshot(boxIndex: Int) {
        val list = _boundingBoxList.value
        if (boxIndex >= 0 && boxIndex < list.size()) {
            val box = list.boxes[boxIndex]
            _undoRedoManager.push(box.id, box)
        }
    }

    /** Moves the selected corner by the given delta (arrow key movement, pushes to undo). */
    fun moveSelectedCorner(deltaX: Double, deltaY: Double) {
        val index = _selectedBoxIndex.value
        val corner = _selectedCorner.value
        if (index >= 0 && corner != null) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            val cornerPoint = box.corners.forCorner(corner)

            // Save for undo
            _undoRedoManager.push(box.id, box)

            // Move corner
            val moved =
                box.moveCorner(corner, Point(cornerPoint.x + deltaX, cornerPoint.y + deltaY))
            updateBox(index, moved)
        }
    }

    // ========== Box Scale/Rotate ==========

    /** Expands or contracts the selected box by the given scale factor. Pushes to undo stack. */
    fun expandSelectedBox(scaleFactor: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            if (index < list.size()) {
                val box = list.boxes[index]
                _undoRedoManager.push(box.id, box)
                val expanded = box.expand(scaleFactor)
                if (!expanded.corners.wouldCreateInvalidShape()) {
                    updateBox(index, expanded)
                }
            }
        }
    }

    /**
     * Rotates the selected box by the given angle in degrees around its center. Pushes to undo
     * stack.
     */
    fun rotateSelectedBox(angleDegrees: Double) {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            if (index < list.size()) {
                val box = list.boxes[index]
                _undoRedoManager.push(box.id, box)
                val rotated = box.rotate(angleDegrees)
                updateBox(index, rotated)
            }
        }
    }

    // ========== Undo/Redo ==========

    /** Undoes the last operation on the selected box. */
    fun undo() {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            val previousState = _undoRedoManager.undo(box, box.id)
            if (previousState != null) {
                updateBox(index, previousState)
            }
        }
        _undoRedoVersion.value++
    }

    /** Redoes the last undone operation on the selected box. */
    fun redo() {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]

            val restoredState = _undoRedoManager.redo(box, box.id)
            if (restoredState != null) {
                updateBox(index, restoredState)
            }
        }
        _undoRedoVersion.value++
    }

    /** Returns true if undo is available. */
    fun canUndo(): Boolean {
        return _undoRedoManager.totalUndoOperations() > 0
    }

    /** Returns true if redo is available for the current box. */
    fun canRedo(): Boolean {
        val index = _selectedBoxIndex.value
        if (index >= 0) {
            val list = _boundingBoxList.value
            val box = list.boxes[index]
            return _undoRedoManager.canRedo(box.id)
        }
        return false
    }

    // ========== Utility ==========

    /** Returns the box at the given index, or null if out of bounds. */
    fun getBox(index: Int): BoundingBox? {
        val list = _boundingBoxList.value
        return if (index >= 0 && index < list.size()) list.boxes[index] else null
    }

    /** Returns the currently selected box. */
    fun selectedBox(): BoundingBox? {
        val index = _selectedBoxIndex.value
        val list = _boundingBoxList.value
        return if (index >= 0 && index < list.size()) list.boxes[index] else null
    }

    /** Returns the box at refinement index. */
    fun refinementBox(): BoundingBox? {
        val index = _refinementBoxIndex.value
        val list = _boundingBoxList.value
        return if (index >= 0 && index < list.size()) list.boxes[index] else null
    }

    /** Returns the current box count. */
    fun boxCount(): Int = _boundingBoxList.value.size()

    /** Clears all undo history and resets selection state. Called during reset. */
    fun clearUndoAndSelection() {
        _undoRedoManager.clearAll()
        _undoRedoVersion.value++
        _selectedBoxIndex.value = -1
        _refinementBoxIndex.value = -1
        _selectedCorner.value = null
    }

    /** Sets the selected box index directly (used by parent coordination methods). */
    fun setSelectedBoxIndex(index: Int) {
        _selectedBoxIndex.value = index
    }

    /** Sets the refinement box index directly (used by parent coordination methods). */
    fun setRefinementBoxIndex(index: Int) {
        _refinementBoxIndex.value = index
    }

    /** Clears the bounding box list. Called during reset. */
    fun clearBoxes() {
        _boundingBoxList.value = BoundingBoxList.empty()
    }

    /** Stores detected boxes with reading-order sort. Overwrites current list. */
    fun setDetectedBoxes(boxes: List<BoundingBox>) {
        _boundingBoxList.value = BoundingBoxList(boxes)
        _selectedBoxIndex.value = -1
    }

    /**
     * Gets the last box index in the current list. Used by parent to set selection after
     * adding a box.
     */
    val lastBoxIndex: Int
        get() = _boundingBoxList.value.size() - 1
}