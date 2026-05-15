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

package dev.patrickgold.florisboard.ime.cjk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CjkInputProviderTest : FunSpec({
    afterEach { CjkInputProviderRegistry.reset() }

    test("Default provider returns empty candidates and accepts commits as no-ops") {
        CjkInputProvider.Default.convert("ni", CjkSchema.PINYIN_SIMPLIFIED) shouldBe emptyList()
        CjkInputProvider.Default.commit(CjkCandidate("你", confidence = 0.95f), CjkSchema.PINYIN_SIMPLIFIED)
        CjkInputProvider.Default.supportedSchemas shouldBe emptySet()
    }

    test("CjkSchema enum covers Pinyin / Jyutping / Zhuyin / Cangjie / Wubi / Mozc / Hangul") {
        val ids = CjkSchema.entries.map { it.schemaId }
        ids shouldBe listOf(
            "luna_pinyin", "luna_pinyin_tw", "jyutping", "bopomofo",
            "cangjie5", "wubi86", "double_pinyin_xiaohe",
            "mozc_ja", "hangul_2bul",
        )
    }

    test("CjkCandidate validates confidence + non-empty text") {
        shouldThrow<IllegalArgumentException> { CjkCandidate("", confidence = 0.5f) }
        shouldThrow<IllegalArgumentException> { CjkCandidate("好", confidence = -0.1f) }
        shouldThrow<IllegalArgumentException> { CjkCandidate("好", confidence = 1.5f) }
    }

    test("Registry default + replace + reset works") {
        val fake = object : CjkInputProvider {
            override fun convert(input: String, schema: CjkSchema, maxCandidates: Int) =
                listOf(CjkCandidate("你好", annotation = "nǐ hǎo", confidence = 0.92f, isPreferred = true))
            override fun commit(candidate: CjkCandidate, schema: CjkSchema) = Unit
            override val supportedSchemas = setOf(CjkSchema.PINYIN_SIMPLIFIED)
        }
        CjkInputProviderRegistry.setActive(fake)
        val candidates = CjkInputProviderRegistry.active.convert("nihao", CjkSchema.PINYIN_SIMPLIFIED)
        candidates.first().text shouldBe "你好"
        candidates.first().isPreferred shouldBe true
        CjkInputProviderRegistry.reset()
        CjkInputProviderRegistry.active shouldBe CjkInputProvider.Default
    }
})
