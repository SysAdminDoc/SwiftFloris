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

package dev.patrickgold.florisboard.ime.smartbar

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Issue #23: a password manager's autofill chips stayed hidden behind the one-time "App tint?" hint
 * until the hint was dismissed. Autofill belongs to the field the user is filling right now, so it
 * takes the slot first and the hint waits for the next ordinary field.
 */
class SmartbarSuggestionsSlotTest : FunSpec({
    test("inline autofill outranks the per-app accent hint") {
        SmartbarSuggestionsSlot.pick(
            hasInlineSuggestions = true,
            perAppAccentHintReady = true,
        ) shouldBe SmartbarSuggestionsSlot.INLINE_AUTOFILL
    }

    test("the hint shows when there is no autofill") {
        SmartbarSuggestionsSlot.pick(
            hasInlineSuggestions = false,
            perAppAccentHintReady = true,
        ) shouldBe SmartbarSuggestionsSlot.PER_APP_ACCENT_HINT
    }

    test("autofill shows when the hint is done") {
        SmartbarSuggestionsSlot.pick(
            hasInlineSuggestions = true,
            perAppAccentHintReady = false,
        ) shouldBe SmartbarSuggestionsSlot.INLINE_AUTOFILL
    }

    test("candidates show when neither applies") {
        SmartbarSuggestionsSlot.pick(
            hasInlineSuggestions = false,
            perAppAccentHintReady = false,
        ) shouldBe SmartbarSuggestionsSlot.CANDIDATES
    }
})
