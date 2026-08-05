package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.input.key.Key

/**
 * True when Enter should submit a folder-based setup screen (Reorganize preview, Duplicate scan).
 */
fun shouldSubmitSetupOnEnter(
    isKeyDown: Boolean,
    key: Key,
    isSetupStep: Boolean,
    folderPath: String,
): Boolean = isKeyDown && key == Key.Enter && isSetupStep && folderPath.isNotBlank()

/** True when Esc should cancel an in-progress Duplicate Scanner operation. */
fun shouldCancelDuplicateOperationOnEscape(isKeyDown: Boolean, key: Key, isBusy: Boolean): Boolean =
    isKeyDown && key == Key.Escape && isBusy

/** True when Enter should open the resolve-all confirmation on Duplicate Scanner results. */
fun shouldConfirmDuplicateResolveOnEnter(
    isKeyDown: Boolean,
    key: Key,
    isResultsStep: Boolean,
    hasDuplicates: Boolean,
    dialogOpen: Boolean,
): Boolean = isKeyDown && key == Key.Enter && isResultsStep && hasDuplicates && !dialogOpen

/** True when Esc should leave Duplicate Scanner results back to setup. */
fun shouldLeaveDuplicateResultsOnEscape(
    isKeyDown: Boolean,
    key: Key,
    isResultsStep: Boolean,
    dialogOpen: Boolean,
): Boolean = isKeyDown && key == Key.Escape && isResultsStep && !dialogOpen

/** True when Enter should apply Reorganize changes from the preview step. */
fun shouldApplyReorganizeOnEnter(
    isKeyDown: Boolean,
    key: Key,
    isPreviewStep: Boolean,
    changeCount: Int,
    undoDialogOpen: Boolean,
): Boolean = isKeyDown && key == Key.Enter && isPreviewStep && changeCount > 0 && !undoDialogOpen

/** True when Esc should leave Reorganize preview back to setup. */
fun shouldLeaveReorganizePreviewOnEscape(
    isKeyDown: Boolean,
    key: Key,
    isPreviewStep: Boolean,
    undoDialogOpen: Boolean,
): Boolean = isKeyDown && key == Key.Escape && isPreviewStep && !undoDialogOpen

/** True when Enter should confirm an open undo dialog. */
fun shouldConfirmUndoDialogOnEnter(isKeyDown: Boolean, key: Key, dialogOpen: Boolean): Boolean =
    isKeyDown && key == Key.Enter && dialogOpen
