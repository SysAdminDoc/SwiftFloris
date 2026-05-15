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

class RtlBidiResolverTest : FunSpec({
    test("pure Latin text resolves as LTR with one run") {
        val analysis = RtlBidiResolver.analyze("Hello world")
        analysis.isLeftToRight shouldBe true
        analysis.isMixed shouldBe false
        analysis.runs.size shouldBe 1
        analysis.runs.first().isRightToLeft shouldBe false
    }

    test("pure Arabic text resolves as RTL") {
        // مرحبا = "marhaba" = hello
        val analysis = RtlBidiResolver.analyze("مرحبا")
        analysis.isLeftToRight shouldBe false
        analysis.runs.first().isRightToLeft shouldBe true
        RtlBidiResolver.primaryDirection("مرحبا") shouldBe TextDirection.RTL
    }

    test("mixed Arabic+Latin text reports isMixed and >= 2 runs") {
        // "Hello مرحبا there"
        val analysis = RtlBidiResolver.analyze("Hello مرحبا there")
        analysis.isMixed shouldBe true
        (analysis.runs.size >= 2) shouldBe true
        analysis.runs.any { it.isRightToLeft } shouldBe true
        analysis.runs.any { !it.isRightToLeft } shouldBe true
        RtlBidiResolver.hasMixedDirections("Hello مرحبا there") shouldBe true
    }

    test("forced LTR base direction overrides RTL-leaning content") {
        val analysis = RtlBidiResolver.analyze("مرحبا", ParagraphBaseDirection.FORCE_LTR)
        analysis.isLeftToRight shouldBe true
    }

    test("forced RTL base direction overrides LTR-leaning content") {
        val analysis = RtlBidiResolver.analyze("Hello", ParagraphBaseDirection.FORCE_RTL)
        analysis.isLeftToRight shouldBe false
    }

    test("empty input produces zero runs but a sensible base direction") {
        val analysis = RtlBidiResolver.analyze("")
        analysis.runs shouldBe emptyList()
        analysis.isLeftToRight shouldBe true
        RtlBidiResolver.primaryDirection("") shouldBe TextDirection.LTR
        RtlBidiResolver.hasMixedDirections("") shouldBe false
    }

    test("each run covers a non-empty character range") {
        val analysis = RtlBidiResolver.analyze("ab مرحبا cd")
        analysis.runs.forEach { it.length shouldBe (it.endExclusive - it.start) }
        analysis.runs.forEach { (it.length > 0) shouldBe true }
    }
})
