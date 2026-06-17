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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PhysicalKeyboardPolicyTest : FunSpec({
    test("import and delete actions are blocked while another operation is active") {
        PhysicalKeyboardPolicy.canStartImport(activeOperation = null) shouldBe true
        PhysicalKeyboardPolicy.canStartImport(activeOperation = PhysicalKeyboardOperation.Importing) shouldBe false
        PhysicalKeyboardPolicy.canDelete(
            selectedLayoutId = "layout",
            activeOperation = PhysicalKeyboardOperation.Deleting,
        ) shouldBe false
    }

    test("apply requires a selected layout, selected device, and idle screen") {
        PhysicalKeyboardPolicy.canApply(
            selectedLayoutId = "layout",
            selectedDeviceId = 7,
            activeOperation = null,
        ) shouldBe true
        PhysicalKeyboardPolicy.canApply(
            selectedLayoutId = null,
            selectedDeviceId = 7,
            activeOperation = null,
        ) shouldBe false
        PhysicalKeyboardPolicy.canApply(
            selectedLayoutId = "layout",
            selectedDeviceId = null,
            activeOperation = null,
        ) shouldBe false
        PhysicalKeyboardPolicy.canApply(
            selectedLayoutId = "layout",
            selectedDeviceId = 7,
            activeOperation = PhysicalKeyboardOperation.Importing,
        ) shouldBe false
    }

    test("import result statuses map to user-visible notices") {
        PhysicalKeyboardPolicy.importNotice(HardwareKeyboardLayoutImportStatus.Imported) shouldBe
            PhysicalKeyboardNotice.ImportSuccess
        PhysicalKeyboardPolicy.importNotice(HardwareKeyboardLayoutImportStatus.UnsupportedFileType) shouldBe
            PhysicalKeyboardNotice.ImportUnsupported
        PhysicalKeyboardPolicy.importNotice(HardwareKeyboardLayoutImportStatus.NoImportableLayout) shouldBe
            PhysicalKeyboardNotice.ImportNoLayout
        PhysicalKeyboardPolicy.importNotice(HardwareKeyboardLayoutImportStatus.TooLarge) shouldBe
            PhysicalKeyboardNotice.ImportTooLarge
        PhysicalKeyboardPolicy.importNotice(HardwareKeyboardLayoutImportStatus.ReadFailure) shouldBe
            PhysicalKeyboardNotice.ImportFailure
    }

    test("default selectors preserve valid selections and otherwise pick the first option") {
        val devices = listOf(
            HardwareKeyboardDeviceOption(id = 3, displayName = "USB"),
            HardwareKeyboardDeviceOption(id = 7, displayName = "Bluetooth"),
        )

        PhysicalKeyboardPolicy.defaultSelectedDeviceId(devices, currentSelectedDeviceId = 7) shouldBe 7
        PhysicalKeyboardPolicy.defaultSelectedDeviceId(devices, currentSelectedDeviceId = 99) shouldBe 3
        PhysicalKeyboardPolicy.defaultSelectedDeviceId(emptyList(), currentSelectedDeviceId = 99) shouldBe null

        PhysicalKeyboardPolicy.defaultSelectedLayoutId(
            layoutIds = listOf("a", "b"),
            currentSelectedLayoutId = "b",
        ) shouldBe "b"
        PhysicalKeyboardPolicy.defaultSelectedLayoutId(
            layoutIds = listOf("a", "b"),
            currentSelectedLayoutId = "missing",
        ) shouldBe "a"
        PhysicalKeyboardPolicy.defaultSelectedLayoutId(emptyList(), currentSelectedLayoutId = "missing") shouldBe null
    }
    test("input view stays hidden when a physical keyboard is available on phone tablet and foldable layouts") {
        listOf(
            ImeFormFactor.Type.PHONE_PORTRAIT,
            ImeFormFactor.Type.TABLET_LANDSCAPE,
            ImeFormFactor.Type.TABLET_PORTRAIT,
        ).forEach { formFactorType ->
            val diagnostics = PhysicalKeyboardPolicy.inputViewVisibilityDiagnostics(
                formFactorType = formFactorType,
                configurationKeyboard = Configuration.KEYBOARD_QWERTY,
                hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
                frameworkWouldShowInputView = true,
                showOnScreenKeyboardPref = false,
                detectedHardwareKeyboards = listOf(HardwareKeyboardDeviceOption(id = 7, displayName = "Bluetooth")),
            )

            diagnostics.decision.shouldShow shouldBe false
            diagnostics.decision.reason shouldBe PhysicalKeyboardInputViewReason.HardwareKeyboardSuppressed
            diagnostics.summary().contains("showOnScreenKeyboardPref=false") shouldBe true
            diagnostics.summary().contains("7:Bluetooth") shouldBe true
        }
    }

    test("show-on-screen preference opts in even when a physical keyboard is available") {
        val decision = PhysicalKeyboardPolicy.inputViewVisibilityDecision(
            frameworkWouldShowInputView = false,
            configurationKeyboard = Configuration.KEYBOARD_QWERTY,
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
            showOnScreenKeyboardPref = true,
        )

        decision.shouldShow shouldBe true
        decision.reason shouldBe PhysicalKeyboardInputViewReason.UserPreference
    }

    test("soft-keyboard devices and hidden hard keyboards keep the framework show path") {
        PhysicalKeyboardPolicy.inputViewVisibilityDecision(
            frameworkWouldShowInputView = false,
            configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO,
            showOnScreenKeyboardPref = false,
        ).shouldShow shouldBe true

        PhysicalKeyboardPolicy.inputViewVisibilityDecision(
            frameworkWouldShowInputView = true,
            configurationKeyboard = Configuration.KEYBOARD_QWERTY,
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_YES,
            showOnScreenKeyboardPref = false,
        ).shouldShow shouldBe true
    }
})
