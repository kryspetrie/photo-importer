package org.kryspetrie.fileimport.ui.wizard.state

import org.kryspetrie.fileimport.domain.model.geometry.BoundingBox
/**
 * Manages undo/redo history for bounding box operations. Each box maintains its own history stack.
 */
class UndoRedoManager<T>(private val maxSize: Int = 50) {
    // Map of box ID to list of states (undo stack)
    private val undoStacks = mutableMapOf<String, MutableList<T>>()

    // Map of box ID to list of states (redo stack)
    private val redoStacks = mutableMapOf<String, MutableList<T>>()

    /** Saves a state to the undo stack for a given box. Clears the redo stack for that box. */
    fun push(boxId: String, state: T) {
        // Get or create undo stack for this box
        val undoStack = undoStacks.getOrPut(boxId) { mutableListOf() }

        // Add state to undo stack
        undoStack.add(state)

        // Trim if exceeds max size
        while (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }

        // Clear redo stack for this box
        redoStacks[boxId]?.clear()
    }

    /**
     * Undoes the last operation for a box. Returns the previous state if available, null otherwise.
     */
    fun undo(currentState: T, boxId: String): T? {
        val undoStack = undoStacks[boxId] ?: return null
        if (undoStack.isEmpty()) return null

        // Pop from undo stack
        val previousState = undoStack.removeLast()

        // Push current state to redo stack
        val redoStack = redoStacks.getOrPut(boxId) { mutableListOf() }
        redoStack.add(currentState)

        return previousState
    }

    /**
     * Redoes the last undone operation for a box. Returns the restored state if available, null
     * otherwise.
     */
    fun redo(currentState: T, boxId: String): T? {
        val redoStack = redoStacks[boxId] ?: return null
        if (redoStack.isEmpty()) return null

        // Pop from redo stack
        val restoredState = redoStack.removeLast()

        // Push current state to undo stack
        val undoStack = undoStacks.getOrPut(boxId) { mutableListOf() }
        undoStack.add(currentState)

        return restoredState
    }

    /** Returns true if undo is available for the given box. */
    fun canUndo(boxId: String): Boolean {
        return undoStacks[boxId]?.isNotEmpty() == true
    }

    /** Returns true if redo is available for the given box. */
    fun canRedo(boxId: String): Boolean {
        return redoStacks[boxId]?.isNotEmpty() == true
    }

    /** Returns the number of undo operations available for a box. */
    fun undoCount(boxId: String): Int {
        return undoStacks[boxId]?.size ?: 0
    }

    /** Returns the number of redo operations available for a box. */
    fun redoCount(boxId: String): Int {
        return redoStacks[boxId]?.size ?: 0
    }

    /** Clears all undo/redo history for a box. */
    fun clear(boxId: String) {
        undoStacks.remove(boxId)
        redoStacks.remove(boxId)
    }

    /** Clears all undo/redo history for all boxes. */
    fun clearAll() {
        undoStacks.clear()
        redoStacks.clear()
    }

    /** Returns total number of operations across all boxes. */
    fun totalOperations(): Int {
        return undoStacks.values.sumOf { it.size } + redoStacks.values.sumOf { it.size }
    }

    /** Returns total undo operations across all boxes. */
    fun totalUndoOperations(): Int {
        return undoStacks.values.sumOf { it.size }
    }

    /** Trims all stacks to save memory if total exceeds limit. */
    fun trimIfNeeded(maxTotal: Int = 200) {
        if (totalOperations() > maxTotal) {
            // Remove oldest operations from the box with the most
            val boxesBySize = undoStacks.entries.sortedByDescending { it.value.size }
            for (entry in boxesBySize) {
                val stack = entry.value
                while (stack.size > maxSize / 2 && totalOperations() > maxTotal * 3 / 4) {
                    stack.removeAt(0)
                }
            }
        }
    }

    companion object {
        /** Creates an UndoRedoManager for BoundingBox operations. */
        fun forBoundingBox(): UndoRedoManager<BoundingBox> {
            return UndoRedoManager(50)
        }
    }
}
