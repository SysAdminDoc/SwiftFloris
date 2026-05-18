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
import org.florisboard.lib.snygg.SnyggElementRule
import org.florisboard.lib.snygg.SnyggSelector

internal enum class ThemeRuleSelectionValidation {
    MissingSelection,
    Ready,
}

internal sealed interface ThemeRuleCodeEditDecision {
    data object Invalid : ThemeRuleCodeEditDecision
    data object Unchanged : ThemeRuleCodeEditDecision
    data object Duplicate : ThemeRuleCodeEditDecision

    data class Apply(
        val newCode: String,
        val oldCodeToDelete: String?,
    ) : ThemeRuleCodeEditDecision
}

internal object ThemeRuleEditPolicy {
    fun validateSelection(
        isAddRuleDialog: Boolean,
        selectedIndex: Int,
        emptySelectionIndex: Int = 0,
    ): ThemeRuleSelectionValidation {
        return if (isAddRuleDialog && selectedIndex == emptySelectionIndex) {
            ThemeRuleSelectionValidation.MissingSelection
        } else {
            ThemeRuleSelectionValidation.Ready
        }
    }

    fun toggleSelector(rule: SnyggElementRule, selector: SnyggSelector): SnyggElementRule {
        return if (rule.selector == selector) {
            rule.copy(selector = SnyggSelector.NONE)
        } else {
            rule.copy(selector = selector)
        }
    }

    fun codeEditDecision(
        inputCodeString: String,
        currentCodeValue: String,
        codeExists: (String) -> Boolean,
        emptyCodeValue: String = KeyCode.UNSPECIFIED.toString(),
    ): ThemeRuleCodeEditDecision {
        val code = inputCodeString.trim().toIntOrNull(radix = 10)
        if (code == null || (code !in KeyCode.Spec.CHARACTERS && code !in KeyCode.Spec.INTERNAL)) {
            return ThemeRuleCodeEditDecision.Invalid
        }
        val normalizedCode = code.toString()
        if (normalizedCode == currentCodeValue) {
            return ThemeRuleCodeEditDecision.Unchanged
        }
        if (codeExists(normalizedCode)) {
            return ThemeRuleCodeEditDecision.Duplicate
        }
        return ThemeRuleCodeEditDecision.Apply(
            newCode = normalizedCode,
            oldCodeToDelete = currentCodeValue.takeUnless { it == emptyCodeValue },
        )
    }
}
