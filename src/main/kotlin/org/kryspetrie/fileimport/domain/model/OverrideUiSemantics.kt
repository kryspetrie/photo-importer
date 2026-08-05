package org.kryspetrie.fileimport.domain.model

/**
 * Shared UI mapping for [OverrideState] checkboxes in the Photo Scan metadata editor.
 *
 * Rule: a field is "included" (checked / editable) when its override is not
 * [OverrideState.NULL_OUT]. Null and [OverrideState.KEEP_SOURCE] both count as included so defaults
 * render editable.
 */
object OverrideUiSemantics {
    fun isIncluded(state: OverrideState?): Boolean = state != OverrideState.NULL_OUT

    fun fromIncluded(included: Boolean): OverrideState =
        if (included) OverrideState.KEEP_SOURCE else OverrideState.NULL_OUT
}
