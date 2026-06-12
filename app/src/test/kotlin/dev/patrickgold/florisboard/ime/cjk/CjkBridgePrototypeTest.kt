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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class CjkBridgePrototypeTest : FunSpec({
    afterEach { CjkInputProviderRegistry.reset() }

    test("bridge factory is disabled by default") {
        CjkBridgeProviderFactory.create(CjkBridgeConfig()) shouldBe CjkInputProvider.Default
    }

    test("bridge evaluation records base-app tradeoffs before native runtime work") {
        CjkBridgeEvaluation.tradeoffs.map { it.option } shouldContainExactly listOf(
            "librime addon bridge",
            "fcitx-style table data",
            "embedded table prototype",
        )
    }

    test("embedded table prototype exposes a feature-flagged Pinyin provider") {
        CjkInputProviderRegistry.configureBridge(CjkBridgeConfig(mode = CjkBridgeMode.EmbeddedTablePrototype))
        val provider = CjkInputProviderRegistry.active

        provider.supportedSchemas shouldContainExactlyInAnyOrder setOf(CjkSchema.PINYIN_SIMPLIFIED)

        val candidates = provider.convert("ni", CjkSchema.PINYIN_SIMPLIFIED, maxCandidates = 3)
        candidates.map { it.text } shouldContainExactly listOf("你", "尼", "你好")
        candidates.first().annotation shouldBe "ni"
        candidates.first().isPreferred shouldBe true
    }

    test("embedded table prototype normalizes input and respects max candidates") {
        val provider = CjkBridgeProviderFactory.create(
            CjkBridgeConfig(mode = CjkBridgeMode.EmbeddedTablePrototype),
        )

        val candidates = provider.convert("  Zhong-Guo!  ", CjkSchema.PINYIN_SIMPLIFIED, maxCandidates = 1)
        candidates.map { it.text } shouldContainExactly listOf("中国")
    }

    test("embedded table prototype returns no candidates for unsupported schemas") {
        val provider = CjkBridgeProviderFactory.create(
            CjkBridgeConfig(mode = CjkBridgeMode.EmbeddedTablePrototype),
        )

        provider.convert("ni", CjkSchema.WUBI_86) shouldBe emptyList()
    }

    test("committed candidates receive a local ranking boost") {
        val provider = CjkTableBridgeProvider(
            mapOf(
                CjkSchema.PINYIN_SIMPLIFIED to listOf(
                    CjkTableEntry(code = "ni", text = "你", annotation = "ni", weight = 980),
                    CjkTableEntry(code = "ni", text = "尼", annotation = "ni", weight = 950),
                ),
            ),
        )

        provider.commit(CjkCandidate("尼", annotation = "ni", confidence = 0.5f), CjkSchema.PINYIN_SIMPLIFIED)
        val candidates = provider.convert("ni", CjkSchema.PINYIN_SIMPLIFIED)

        candidates.map { it.text } shouldContainExactly listOf("尼", "你")
        candidates.first().isPreferred shouldBe true
    }
})
