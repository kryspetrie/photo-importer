package org.kryspetrie.fileimport.ui.components

import androidx.compose.ui.input.key.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.kryspetrie.fileimport.infrastructure.adapter.Platform

@DisplayName("ShortcutLabels")
class ShortcutLabelsTest {

    @Test
    fun modifierMatchesPlatform() {
        val expected = if (Platform.isMac) "⌘" else "Ctrl"
        assertThat(ShortcutLabels.modifier).isEqualTo(expected)
    }

    @Test
    fun chordFormatsSingleKey() {
        assertThat(ShortcutLabels.chord("1")).isEqualTo("${ShortcutLabels.modifier}+1")
    }

    @Test
    fun chordFormatsDualKeys() {
        assertThat(ShortcutLabels.chord(",", "."))
            .isEqualTo("${ShortcutLabels.modifier}+, / ${ShortcutLabels.modifier}+.")
    }
}

@DisplayName("AppKeyboardShortcuts")
class AppKeyboardShortcutsTest {

    @Test
    fun mapsCtrlDigitsToTabIndices() {
        assertThat(appTabIndexForCtrlDigit(Key.One)).isEqualTo(0)
        assertThat(appTabIndexForCtrlDigit(Key.Two)).isEqualTo(1)
        assertThat(appTabIndexForCtrlDigit(Key.Three)).isEqualTo(2)
        assertThat(appTabIndexForCtrlDigit(Key.Four)).isEqualTo(3)
        assertThat(appTabIndexForCtrlDigit(Key.Five)).isEqualTo(4)
    }

    @Test
    fun ignoresNonTabKeys() {
        assertThat(appTabIndexForCtrlDigit(Key.Slash)).isNull()
        assertThat(appTabIndexForCtrlDigit(Key.Enter)).isNull()
    }

    @Test
    fun opensHelpOnCtrlSlashRegardlessOfShift() {
        assertThat(shouldOpenKeyboardShortcutHelp(isCtrlPressed = true, key = Key.Slash)).isTrue()
        assertThat(shouldOpenKeyboardShortcutHelp(isCtrlPressed = false, key = Key.Slash)).isFalse()
    }

    @Test
    fun opensHelpOnF1WithoutModifier() {
        assertThat(shouldOpenKeyboardShortcutHelp(isCtrlPressed = false, key = Key.F1)).isTrue()
        assertThat(shouldOpenKeyboardShortcutHelp(isCtrlPressed = true, key = Key.F1)).isTrue()
    }
}

@DisplayName("SetupScreenKeyboard")
class SetupScreenKeyboardTest {

    @Test
    fun submitsOnEnterWhenSetupHasFolder() {
        assertThat(
                shouldSubmitSetupOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isSetupStep = true,
                    folderPath = "/photos",
                )
            )
            .isTrue()
    }

    @Test
    fun ignoresEnterWhenNotOnSetupStep() {
        assertThat(
                shouldSubmitSetupOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isSetupStep = false,
                    folderPath = "/photos",
                )
            )
            .isFalse()
    }

    @Test
    fun ignoresEnterWhenFolderBlank() {
        assertThat(
                shouldSubmitSetupOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isSetupStep = true,
                    folderPath = "",
                )
            )
            .isFalse()
    }

    @Test
    fun cancelsDuplicateBusyOnEscape() {
        assertThat(
                shouldCancelDuplicateOperationOnEscape(
                    isKeyDown = true,
                    key = Key.Escape,
                    isBusy = true,
                )
            )
            .isTrue()
    }

    @Test
    fun confirmsDuplicateResolveOnEnter() {
        assertThat(
                shouldConfirmDuplicateResolveOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isResultsStep = true,
                    hasDuplicates = true,
                    dialogOpen = false,
                )
            )
            .isTrue()
        assertThat(
                shouldConfirmDuplicateResolveOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isResultsStep = true,
                    hasDuplicates = true,
                    dialogOpen = true,
                )
            )
            .isFalse()
    }

    @Test
    fun leavesDuplicateResultsOnEscape() {
        assertThat(
                shouldLeaveDuplicateResultsOnEscape(
                    isKeyDown = true,
                    key = Key.Escape,
                    isResultsStep = true,
                    dialogOpen = false,
                )
            )
            .isTrue()
    }

    @Test
    fun appliesReorganizeOnEnterFromPreview() {
        assertThat(
                shouldApplyReorganizeOnEnter(
                    isKeyDown = true,
                    key = Key.Enter,
                    isPreviewStep = true,
                    changeCount = 3,
                    undoDialogOpen = false,
                )
            )
            .isTrue()
    }

    @Test
    fun leavesReorganizePreviewOnEscape() {
        assertThat(
                shouldLeaveReorganizePreviewOnEscape(
                    isKeyDown = true,
                    key = Key.Escape,
                    isPreviewStep = true,
                    undoDialogOpen = false,
                )
            )
            .isTrue()
    }

    @Test
    fun confirmsUndoDialogOnEnter() {
        assertThat(
                shouldConfirmUndoDialogOnEnter(isKeyDown = true, key = Key.Enter, dialogOpen = true)
            )
            .isTrue()
    }
}
