/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.advanced

import android.content.res.Configuration
import dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardLayoutImportStatus
import dev.patrickgold.florisboard.ime.window.ImeFormFactor

internal data class HardwareKeyboardDeviceOption(
    val id: Int,
    val displayName: String,
)

internal enum class PhysicalKeyboardInputViewReason {
    UserPreference,
    SmartbarOnly,
    HardwareKeyboardSuppressed,
    FrameworkOrSoftKeyboard,
}

internal data class PhysicalKeyboardInputViewDecision(
    val shouldShow: Boolean,
    val smartbarOnly: Boolean = false,
    val reason: PhysicalKeyboardInputViewReason,
)

internal data class PhysicalKeyboardVisibilityDiagnostics(
    val formFactorType: ImeFormFactor.Type,
    val configurationKeyboard: Int,
    val hardKeyboardHidden: Int,
    val frameworkWouldShowInputView: Boolean,
    val showOnScreenKeyboardPref: Boolean,
    val detectedHardwareKeyboards: List<HardwareKeyboardDeviceOption>,
    val decision: PhysicalKeyboardInputViewDecision,
) {
    fun summary(): String {
        val devices = detectedHardwareKeyboards.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]",
        ) { device ->
            "${device.id}:${device.displayName}"
        }
        return "formFactor=$formFactorType " +
            "configKeyboard=$configurationKeyboard hardKeyboardHidden=$hardKeyboardHidden " +
            "frameworkWouldShow=$frameworkWouldShowInputView " +
            "showOnScreenKeyboardPref=$showOnScreenKeyboardPref " +
            "detectedHardwareKeyboards=$devices " +
            "shouldShow=${decision.shouldShow} reason=${decision.reason}"
    }
}

internal enum class PhysicalKeyboardOperation {
    Importing,
    Deleting,
}

internal enum class PhysicalKeyboardNotice {
    None,
    Importing,
    DeleteInProgress,
    ImportSuccess,
    ImportUnsupported,
    ImportNoLayout,
    ImportTooLarge,
    ImportFailure,
    ApplySuccess,
    ApplyFailure,
    DeleteSuccess,
    DeleteFailure,
}

internal object PhysicalKeyboardPolicy {
    fun canStartImport(activeOperation: PhysicalKeyboardOperation?): Boolean {
        return activeOperation == null
    }

    fun canApply(
        selectedLayoutId: String?,
        selectedDeviceId: Int?,
        activeOperation: PhysicalKeyboardOperation?,
    ): Boolean {
        return selectedLayoutId != null && selectedDeviceId != null && activeOperation == null
    }

    fun canDelete(
        selectedLayoutId: String?,
        activeOperation: PhysicalKeyboardOperation?,
    ): Boolean {
        return selectedLayoutId != null && activeOperation == null
    }

    fun importNotice(status: HardwareKeyboardLayoutImportStatus): PhysicalKeyboardNotice {
        return when (status) {
            HardwareKeyboardLayoutImportStatus.Imported -> PhysicalKeyboardNotice.ImportSuccess
            HardwareKeyboardLayoutImportStatus.UnsupportedFileType -> PhysicalKeyboardNotice.ImportUnsupported
            HardwareKeyboardLayoutImportStatus.NoImportableLayout -> PhysicalKeyboardNotice.ImportNoLayout
            HardwareKeyboardLayoutImportStatus.TooLarge -> PhysicalKeyboardNotice.ImportTooLarge
            HardwareKeyboardLayoutImportStatus.ReadFailure -> PhysicalKeyboardNotice.ImportFailure
        }
    }

    fun resolveNotice(
        activeOperation: PhysicalKeyboardOperation?,
        lastTerminalNotice: PhysicalKeyboardNotice?,
    ): PhysicalKeyboardNotice {
        return when (activeOperation) {
            PhysicalKeyboardOperation.Importing -> PhysicalKeyboardNotice.Importing
            PhysicalKeyboardOperation.Deleting -> PhysicalKeyboardNotice.DeleteInProgress
            null -> lastTerminalNotice ?: PhysicalKeyboardNotice.None
        }
    }

    fun defaultSelectedDeviceId(
        devices: List<HardwareKeyboardDeviceOption>,
        currentSelectedDeviceId: Int?,
    ): Int? {
        if (currentSelectedDeviceId != null && devices.any { it.id == currentSelectedDeviceId }) {
            return currentSelectedDeviceId
        }
        return devices.firstOrNull()?.id
    }

    fun defaultSelectedLayoutId(
        layoutIds: List<String>,
        currentSelectedLayoutId: String?,
    ): String? {
        if (currentSelectedLayoutId != null && currentSelectedLayoutId in layoutIds) {
            return currentSelectedLayoutId
        }
        return layoutIds.firstOrNull()
    }

    fun inputViewVisibilityDecision(
        frameworkWouldShowInputView: Boolean,
        configurationKeyboard: Int,
        hardKeyboardHidden: Int,
        showOnScreenKeyboardPref: Boolean,
        showSmartbarOnlyPref: Boolean = false,
    ): PhysicalKeyboardInputViewDecision {
        if (showOnScreenKeyboardPref) {
            return PhysicalKeyboardInputViewDecision(
                shouldShow = true,
                reason = PhysicalKeyboardInputViewReason.UserPreference,
            )
        }
        if (isHardwareKeyboardAvailable(configurationKeyboard, hardKeyboardHidden)) {
            if (showSmartbarOnlyPref) {
                return PhysicalKeyboardInputViewDecision(
                    shouldShow = true,
                    smartbarOnly = true,
                    reason = PhysicalKeyboardInputViewReason.SmartbarOnly,
                )
            }
            return PhysicalKeyboardInputViewDecision(
                shouldShow = false,
                reason = PhysicalKeyboardInputViewReason.HardwareKeyboardSuppressed,
            )
        }
        return PhysicalKeyboardInputViewDecision(
            shouldShow = frameworkWouldShowInputView || configurationKeyboard == Configuration.KEYBOARD_NOKEYS,
            reason = PhysicalKeyboardInputViewReason.FrameworkOrSoftKeyboard,
        )
    }

    fun inputViewVisibilityDiagnostics(
        formFactorType: ImeFormFactor.Type,
        configurationKeyboard: Int,
        hardKeyboardHidden: Int,
        frameworkWouldShowInputView: Boolean,
        showOnScreenKeyboardPref: Boolean,
        showSmartbarOnlyPref: Boolean = false,
        detectedHardwareKeyboards: List<HardwareKeyboardDeviceOption>,
    ): PhysicalKeyboardVisibilityDiagnostics {
        val decision = inputViewVisibilityDecision(
            frameworkWouldShowInputView = frameworkWouldShowInputView,
            configurationKeyboard = configurationKeyboard,
            hardKeyboardHidden = hardKeyboardHidden,
            showOnScreenKeyboardPref = showOnScreenKeyboardPref,
            showSmartbarOnlyPref = showSmartbarOnlyPref,
        )
        return PhysicalKeyboardVisibilityDiagnostics(
            formFactorType = formFactorType,
            configurationKeyboard = configurationKeyboard,
            hardKeyboardHidden = hardKeyboardHidden,
            frameworkWouldShowInputView = frameworkWouldShowInputView,
            showOnScreenKeyboardPref = showOnScreenKeyboardPref,
            detectedHardwareKeyboards = detectedHardwareKeyboards,
            decision = decision,
        )
    }

    private fun isHardwareKeyboardAvailable(
        configurationKeyboard: Int,
        hardKeyboardHidden: Int,
    ): Boolean {
        return configurationKeyboard != Configuration.KEYBOARD_NOKEYS &&
            hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    }
}
