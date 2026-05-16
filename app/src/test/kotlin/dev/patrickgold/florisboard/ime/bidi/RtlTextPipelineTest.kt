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

package dev.patrickgold.florisboard.ime.bidi

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RtlTextPipelineTest : FunSpec({

    test("isNoOp returns true for default Options") {
        RtlTextPipeline.Options().isNoOp shouldBe true
    }

    test("no-op pipeline returns input unchanged") {
        val text = "hello world ١٢٣"
        RtlTextPipeline.process(text, RtlTextPipeline.Options()) shouldBe text
    }

    test("HEBREW_DEFAULT profile strips Niqqud + rewrites apostrophe to Geresh") {
        // \u05D1\u05B7 = Bet with Patach (Niqqud) — should strip the Niqqud.
        val input = "\u05D1\u05B7\u05D9'\u05D8"
        val output = RtlTextPipeline.process(input, RtlTextPipeline.Options.HEBREW_DEFAULT)
        // After strip: \u05D1\u05D9'\u05D8; then ' → \u05F3 Geresh.
        output shouldBe "\u05D1\u05D9\u05F3\u05D8"
    }

    test("ARABIC_DEFAULT profile converts Western digits to Arabic-Indic") {
        val output = RtlTextPipeline.process(
            "Order: 42",
            RtlTextPipeline.Options.ARABIC_DEFAULT,
        )
        output shouldBe "Order: ٤٢"
    }

    test("PERSIAN_URDU_DEFAULT profile converts digits to Extended Arabic-Indic") {
        val output = RtlTextPipeline.process(
            "year 2026",
            RtlTextPipeline.Options.PERSIAN_URDU_DEFAULT,
        )
        output shouldBe "year ۲۰۲۶"
    }

    test("custom Options with only numeralTarget runs only the numeral pass") {
        val input = "value 7 of 10"
        val output = RtlTextPipeline.process(
            input,
            RtlTextPipeline.Options(numeralTarget = RtlTextPipeline.NumeralTarget.WESTERN),
        )
        output shouldBe input
    }

    test("normalize-to-Western collapses mixed digit families") {
        val output = RtlTextPipeline.process(
            "mix ١2۳",
            RtlTextPipeline.Options(numeralTarget = RtlTextPipeline.NumeralTarget.WESTERN),
        )
        output shouldBe "mix 123"
    }

    test("empty input passes through every profile") {
        RtlTextPipeline.process("", RtlTextPipeline.Options.HEBREW_DEFAULT) shouldBe ""
        RtlTextPipeline.process("", RtlTextPipeline.Options.ARABIC_DEFAULT) shouldBe ""
        RtlTextPipeline.process("", RtlTextPipeline.Options.PERSIAN_URDU_DEFAULT) shouldBe ""
    }
})
