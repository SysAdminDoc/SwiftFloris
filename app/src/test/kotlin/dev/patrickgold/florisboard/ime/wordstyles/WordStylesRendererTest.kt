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

package dev.patrickgold.florisboard.ime.wordstyles

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class WordStylesRendererTest : FunSpec({
    afterEach { WordStylesRendererRegistry.reset() }

    test("Default renderer returns null bytes and exposes the built-in styles") {
        WordStylesRenderer.Default.renderStyledImage(
            "hello",
            WordStyle.BuiltIns.first(),
        ).shouldBeNull()
        WordStylesRenderer.Default.defaultStyles.size shouldBe 4
    }

    test("WordStyle enforces #RRGGBBAA hex on colour fields") {
        shouldThrow<IllegalArgumentException> {
            WordStyle("x", "X", "not-hex", "#000000FF", "sans-serif", 32)
        }
        shouldThrow<IllegalArgumentException> {
            WordStyle("x", "X", "#FF0000FF", "no-hash-or-alpha", "sans-serif", 32)
        }
    }

    test("WordStyle enforces font-size + shadow + padding ranges") {
        shouldThrow<IllegalArgumentException> {
            WordStyle("x", "X", "#FFFFFFFF", "#000000FF", "sans-serif", fontSizeSp = 4)
        }
        shouldThrow<IllegalArgumentException> {
            WordStyle("x", "X", "#FFFFFFFF", "#000000FF", "sans-serif", fontSizeSp = 64, shadowRadiusDp = 100)
        }
        shouldThrow<IllegalArgumentException> {
            WordStyle("x", "X", "#FFFFFFFF", "#000000FF", "sans-serif", fontSizeSp = 64, paddingDp = 200)
        }
    }

    test("BuiltIns ship with unique ids and at least four entries") {
        val ids = WordStyle.BuiltIns.map { it.id }
        ids.distinct().size shouldBe ids.size
        (ids.size >= 4) shouldBe true
        ids.contains("neon") shouldBe true
        ids.contains("gradient_sunset") shouldBe true
    }

    test("Registry default + replace + reset works") {
        WordStylesRendererRegistry.active shouldBe WordStylesRenderer.Default
        val custom = object : WordStylesRenderer {
            override fun renderStyledImage(text: String, style: WordStyle) = byteArrayOf(0x89.toByte(), 'P'.code.toByte())
            override val defaultStyles = listOf(WordStyle.BuiltIns.first())
        }
        WordStylesRendererRegistry.setActive(custom)
        WordStylesRendererRegistry.active.renderStyledImage("hi", WordStyle.BuiltIns.first())
            ?.size shouldBe 2
        WordStylesRendererRegistry.reset()
        WordStylesRendererRegistry.active shouldBe WordStylesRenderer.Default
    }
})
