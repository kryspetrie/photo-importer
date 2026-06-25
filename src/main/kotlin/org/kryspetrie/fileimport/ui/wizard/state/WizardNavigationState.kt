package org.kryspetrie.fileimport.ui.wizard.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages wizard step navigation state. Extracted from [PhotoScanWizardState] to separate
 * navigation concerns from box manipulation and other state.
 *
 * Other sub-states and the parent facade can set the step directly through the shared
 * [step] MutableStateFlow when needed (e.g., initialization, reset, refinement transitions).
 */
class WizardNavigationState {

    private val _currentStep = MutableStateFlow(WizardStep.IMPORT)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    /** The mutable reference, shared with [PhotoScanWizardState] for initialization and reset. */
    val step: MutableStateFlow<WizardStep> = _currentStep

    /** Goes to the overview step. */
    fun goToOverview() {
        _currentStep.value = WizardStep.OVERVIEW
    }

    /** Goes to the summary step. */
    fun goToSummary() {
        _currentStep.value = WizardStep.SUMMARY
    }

    /** Goes to the edit step (rotation or metadata mode). */
    fun goToEdit() {
        _currentStep.value = WizardStep.EDIT
    }

    /** Goes to processing step. */
    fun goToProcessing() {
        _currentStep.value = WizardStep.PROCESSING
    }

    /** Goes to complete step. */
    fun goToComplete() {
        _currentStep.value = WizardStep.COMPLETE
    }

    /** Resets to the import step. Called during [PhotoScanWizardState.resetToImportStep]. */
    fun resetToImport() {
        _currentStep.value = WizardStep.IMPORT
    }
}