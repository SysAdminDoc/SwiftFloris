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

package dev.patrickgold.florisboard.ime.popup

import dev.patrickgold.florisboard.ime.text.key.KeyHintConfiguration
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the long-press popup order for every combination of symbol and number
 * hint mode.
 *
 * There were no tests over this before. `MutablePopupSet.initPopupList` decides
 * the order with a `when` on the symbol hint mode holding a nested `when` on the
 * number hint mode, and the `HINT_PRIORITY` arm used to scrutinise the symbol
 * mode a second time. Because that arm is only reached when the symbol mode
 * already is `HINT_PRIORITY`, its first case always matched and its `else` was
 * unreachable, so `symbol, number, main` was returned for every number mode
 * instead of `symbol, main, number` when the number hint was not itself on hint
 * priority. Every pair below is asserted so the same copy-and-paste cannot come
 * back for one of the other arms either.
 */
class PopupHintPriorityTest : FunSpec({
    val main = TextKeyData(label = "main")
    val symbolHint = TextKeyData(label = "symbol")
    val numberHint = TextKeyData(label = "number")

    fun orderFor(symbolMode: KeyHintMode, numberMode: KeyHintMode): List<String> {
        val popupSet = MutablePopupSet<TextKeyData>(
            main = main,
            symbolHint = symbolHint,
            numberHint = numberHint,
        )
        val keys = popupSet.getPopupKeys(
            KeyHintConfiguration(
                symbolHintMode = symbolMode,
                numberHintMode = numberMode,
                mergeHintPopups = false,
            ),
        )
        return keys.prioritized.map { it.label }
    }

    test("both hints present resolve to a stable order for every mode pair") {
        val expected = mapOf(
            // Accent priority on the symbol hint keeps a non-hint key first.
            (KeyHintMode.ACCENT_PRIORITY to KeyHintMode.ACCENT_PRIORITY) to
                listOf("main", "symbol", "number"),
            // Hint priority on the number hint overrules the symbol's accent priority.
            (KeyHintMode.ACCENT_PRIORITY to KeyHintMode.HINT_PRIORITY) to
                listOf("number", "main", "symbol"),
            (KeyHintMode.ACCENT_PRIORITY to KeyHintMode.SMART_PRIORITY) to
                listOf("main", "number", "symbol"),
            // Hint priority on the symbol hint wins outright.
            (KeyHintMode.HINT_PRIORITY to KeyHintMode.HINT_PRIORITY) to
                listOf("symbol", "number", "main"),
            // ...but the number hint still decides where main sits. These two are
            // the cases the duplicated scrutinee made unreachable.
            (KeyHintMode.HINT_PRIORITY to KeyHintMode.ACCENT_PRIORITY) to
                listOf("symbol", "main", "number"),
            (KeyHintMode.HINT_PRIORITY to KeyHintMode.SMART_PRIORITY) to
                listOf("symbol", "main", "number"),
            // Smart priority on the symbol hint defers to the number hint's mode.
            (KeyHintMode.SMART_PRIORITY to KeyHintMode.ACCENT_PRIORITY) to
                listOf("main", "symbol", "number"),
            (KeyHintMode.SMART_PRIORITY to KeyHintMode.HINT_PRIORITY) to
                listOf("number", "main", "symbol"),
            (KeyHintMode.SMART_PRIORITY to KeyHintMode.SMART_PRIORITY) to
                listOf("main", "symbol", "number"),
        )

        expected.forEach { (modes, order) ->
            val (symbolMode, numberMode) = modes
            withClue("symbolHintMode=$symbolMode numberHintMode=$numberMode") {
                orderFor(symbolMode, numberMode) shouldBe order
            }
        }
    }

    test("the number hint mode changes the order when the symbol hint is on hint priority") {
        // The regression in one line: if these two ever match again, the nested
        // `when` is scrutinising the symbol mode instead of the number mode.
        val onAccent = orderFor(KeyHintMode.HINT_PRIORITY, KeyHintMode.ACCENT_PRIORITY)
        val onHint = orderFor(KeyHintMode.HINT_PRIORITY, KeyHintMode.HINT_PRIORITY)

        withClue("number hint mode must still affect the order") {
            (onAccent == onHint) shouldBe false
        }
    }

    test("the symbol hint is the announced hint whenever both hints are present") {
        listOf(KeyHintMode.ACCENT_PRIORITY, KeyHintMode.HINT_PRIORITY, KeyHintMode.SMART_PRIORITY)
            .forEach { symbolMode ->
                listOf(
                    KeyHintMode.ACCENT_PRIORITY,
                    KeyHintMode.HINT_PRIORITY,
                    KeyHintMode.SMART_PRIORITY,
                ).forEach { numberMode ->
                    val popupSet = MutablePopupSet<TextKeyData>(
                        main = main,
                        symbolHint = symbolHint,
                        numberHint = numberHint,
                    )
                    val keys = popupSet.getPopupKeys(
                        KeyHintConfiguration(symbolMode, numberMode, mergeHintPopups = false),
                    )
                    withClue("symbolHintMode=$symbolMode numberHintMode=$numberMode") {
                        keys.hint?.label shouldBe "symbol"
                    }
                }
            }
    }
})
