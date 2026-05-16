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

package dev.patrickgold.florisboard.ime.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private fun close(
    typed: String,
    preceding: String = typed,
    following: String = "",
    variation: KeyVariation = KeyVariation.NORMAL,
    enabled: Boolean = true,
): String? = QuoteAutoCloseGate.closerFor(typed, preceding, following, variation, enabled)

class QuoteAutoCloseGateTest : FunSpec({

    test("straight double quote auto-closes to straight double quote") {
        close(typed = "\"", preceding = "\"") shouldBe "\""
    }

    test("straight single quote auto-closes at sentence start") {
        close(typed = "'", preceding = "'") shouldBe "'"
    }

    test("single quote suppresses auto-close after a letter (apostrophe)") {
        close(typed = "'", preceding = "don'") shouldBe null
    }

    test("single quote suppresses auto-close after a digit (foot/inch shorthand)") {
        close(typed = "'", preceding = "5'") shouldBe null
    }

    test("auto-close suppressed when pref is disabled") {
        close(typed = "\"", preceding = "\"", enabled = false) shouldBe null
    }

    test("auto-close suppressed in password fields") {
        close(typed = "\"", preceding = "\"", variation = KeyVariation.PASSWORD) shouldBe null
    }

    test("auto-close suppressed in URI fields") {
        close(typed = "\"", preceding = "\"", variation = KeyVariation.URI) shouldBe null
    }

    test("auto-close suppressed in email fields") {
        close(typed = "\"", preceding = "\"", variation = KeyVariation.EMAIL_ADDRESS) shouldBe null
    }

    test("auto-close suppressed when next char is the same closer (avoid double-up)") {
        close(typed = "\"", preceding = "\"", following = "\"") shouldBe null
    }

    test("auto-close suppressed when next char is alphabetic (mid-word)") {
        close(typed = "\"", preceding = "hel\"", following = "lo") shouldBe null
    }

    test("French guillemet « auto-closes to »") {
        close(typed = "«", preceding = "«") shouldBe "»"
    }

    test("German low-9 quote „ auto-closes to high-66 “") {
        close(typed = "„", preceding = "„") shouldBe "“"
    }

    test("German low-9 single quote ‚ auto-closes to high-66 ‘") {
        close(typed = "‚", preceding = "‚") shouldBe "‘"
    }

    test("curly double-left “ auto-closes to curly double-right ”") {
        close(typed = "“", preceding = "“") shouldBe "”"
    }

    test("curly single-left ‘ auto-closes to curly single-right ’") {
        close(typed = "‘", preceding = "‘") shouldBe "’"
    }

    test("curly single right ’ suppresses auto-close after letter (apostrophe context)") {
        close(typed = "’", preceding = "don’") shouldBe null
    }

    test("Japanese corner brackets 「 auto-close to 」") {
        close(typed = "「", preceding = "「") shouldBe "」"
    }

    test("Japanese white corner brackets 『 auto-close to 』") {
        close(typed = "『", preceding = "『") shouldBe "』"
    }

    test("CJK angle brackets 〈 auto-close to 〉") {
        close(typed = "〈", preceding = "〈") shouldBe "〉"
    }

    test("CJK double angle brackets 《 auto-close to 》") {
        close(typed = "《", preceding = "《") shouldBe "》"
    }

    test("returns null for unknown typed characters") {
        close(typed = "x", preceding = "x") shouldBe null
        close(typed = "(", preceding = "(") shouldBe null
        close(typed = "[", preceding = "[") shouldBe null
    }

    test("single quote at start of sentence after period+space auto-closes") {
        close(typed = "'", preceding = "Hello. '") shouldBe "'"
    }

    test("double quote at start of buffer auto-closes") {
        close(typed = "\"", preceding = "\"") shouldBe "\""
    }
})
