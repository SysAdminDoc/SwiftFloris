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

package dev.patrickgold.florisboard.app.settings.theme

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSelector

class ThemeRuleEditPolicyTest : FunSpec({
    test("add-rule validation rejects only the empty selection") {
        ThemeRuleEditPolicy.validateSelection(
            isAddRuleDialog = true,
            selectedIndex = 0,
        ) shouldBe ThemeRuleSelectionValidation.MissingSelection

        ThemeRuleEditPolicy.validateSelection(
            isAddRuleDialog = true,
            selectedIndex = 1,
        ) shouldBe ThemeRuleSelectionValidation.Ready

        ThemeRuleEditPolicy.validateSelection(
            isAddRuleDialog = false,
            selectedIndex = 0,
        ) shouldBe ThemeRuleSelectionValidation.Ready
    }

    test("selector toggle selects a new selector and clears the same selector") {
        val baseRule = SnyggElementRule("key")
        val pressedRule = ThemeRuleEditPolicy.toggleSelector(baseRule, SnyggSelector.PRESSED)

        pressedRule.selector shouldBe SnyggSelector.PRESSED
        ThemeRuleEditPolicy.toggleSelector(pressedRule, SnyggSelector.PRESSED).selector shouldBe SnyggSelector.NONE
    }

    test("code edit rejects blank non-numeric and out-of-range values") {
        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = "",
            currentCodeValue = KeyCode.UNSPECIFIED.toString(),
            codeExists = { false },
        ) shouldBe ThemeRuleCodeEditDecision.Invalid

        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = "abc",
            currentCodeValue = KeyCode.UNSPECIFIED.toString(),
            codeExists = { false },
        ) shouldBe ThemeRuleCodeEditDecision.Invalid

        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = (KeyCode.Spec.CHARACTERS_MAX + 1).toString(),
            currentCodeValue = KeyCode.UNSPECIFIED.toString(),
            codeExists = { false },
        ) shouldBe ThemeRuleCodeEditDecision.Invalid
    }

    test("code edit dismisses unchanged values before duplicate checks") {
        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = "65",
            currentCodeValue = "65",
            codeExists = { true },
        ) shouldBe ThemeRuleCodeEditDecision.Unchanged
    }

    test("code edit reports duplicates for different existing codes") {
        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = "65",
            currentCodeValue = KeyCode.UNSPECIFIED.toString(),
            codeExists = { it == "65" },
        ) shouldBe ThemeRuleCodeEditDecision.Duplicate
    }

    test("code edit applies new and replacement codes") {
        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = " 65 ",
            currentCodeValue = KeyCode.UNSPECIFIED.toString(),
            codeExists = { false },
        ) shouldBe ThemeRuleCodeEditDecision.Apply(
            newCode = "65",
            oldCodeToDelete = null,
        )

        ThemeRuleEditPolicy.codeEditDecision(
            inputCodeString = "-201",
            currentCodeValue = "65",
            codeExists = { false },
        ) shouldBe ThemeRuleCodeEditDecision.Apply(
            newCode = "-201",
            oldCodeToDelete = "65",
        )
    }
})
