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

import kotlin.math.max

enum class CjkBridgeMode {
    Disabled,
    EmbeddedTablePrototype,
    ExternalEngineAddon,
}

data class CjkBridgeConfig(
    val mode: CjkBridgeMode = CjkBridgeMode.Disabled,
)

data class CjkBridgeTradeoff(
    val option: String,
    val licensePosture: String,
    val runtimePosture: String,
    val baseAppDecision: String,
)

object CjkBridgeEvaluation {
    val tradeoffs = listOf(
        CjkBridgeTradeoff(
            option = "librime addon bridge",
            licensePosture = "BSD-3 engine; schemas and dictionaries still need per-pack review",
            runtimePosture = "Native runtime plus schema data belongs outside the base APK",
            baseAppDecision = "Keep the base app on this provider facade and load signed addon providers later",
        ),
        CjkBridgeTradeoff(
            option = "fcitx-style table data",
            licensePosture = "Engine and table packages vary; each table must carry its own license metadata",
            runtimePosture = "Large production tables should ship as language-pack addons",
            baseAppDecision = "Reuse the same table contract, but do not bundle broad table corpora in :app",
        ),
        CjkBridgeTradeoff(
            option = "embedded table prototype",
            licensePosture = "Small SwiftFloris-authored fixture data only",
            runtimePosture = "Negligible; useful for IME/candidate-row wiring tests",
            baseAppDecision = "Allowed behind an off-by-default feature flag as the first proof",
        ),
    )
}

object CjkBridgeProviderFactory {
    fun create(config: CjkBridgeConfig): CjkInputProvider {
        return when (config.mode) {
            CjkBridgeMode.Disabled -> CjkInputProvider.Default
            CjkBridgeMode.EmbeddedTablePrototype -> CjkTableBridgeProvider(CjkPrototypeTables.pinyinSimplified)
            CjkBridgeMode.ExternalEngineAddon -> CjkInputProviderRegistry.active
        }
    }
}

data class CjkTableEntry(
    val code: String,
    val text: String,
    val annotation: String,
    val weight: Int,
) {
    init {
        require(code.isNotBlank()) { "CJK table code must not be blank" }
        require(text.isNotBlank()) { "CJK table text must not be blank" }
        require(weight > 0) { "CJK table weight must be positive" }
    }
}

class CjkTableBridgeProvider(
    tables: Map<CjkSchema, List<CjkTableEntry>>,
) : CjkInputProvider {
    private val tables = tables.mapValues { (_, entries) ->
        entries.sortedWith(compareBy<CjkTableEntry> { it.code }.thenByDescending { it.weight })
    }
    private val commitBoosts = mutableMapOf<Pair<CjkSchema, String>, Int>()

    override val supportedSchemas: Set<CjkSchema> = this.tables.keys

    override fun convert(input: String, schema: CjkSchema, maxCandidates: Int): List<CjkCandidate> {
        if (maxCandidates <= 0) return emptyList()

        val normalizedInput = input.normalizeCode()
        if (normalizedInput.isEmpty()) return emptyList()

        val table = tables[schema] ?: return emptyList()
        val ranked = table
            .asSequence()
            .filter { entry -> entry.code.startsWith(normalizedInput) }
            .sortedWith(
                compareByDescending<CjkTableEntry> { it.code == normalizedInput }
                    .thenByDescending { it.weight + (commitBoosts[schema to it.text] ?: 0) }
                    .thenBy { it.code.length }
                    .thenBy { it.text },
            )
            .take(maxCandidates)
            .toList()

        val maxScore = ranked.maxOfOrNull { it.weight + (commitBoosts[schema to it.text] ?: 0) } ?: 1
        return ranked.mapIndexed { index, entry ->
            val score = entry.weight + (commitBoosts[schema to entry.text] ?: 0)
            CjkCandidate(
                text = entry.text,
                annotation = entry.annotation,
                confidence = score.toConfidence(maxScore),
                isPreferred = index == 0,
            )
        }
    }

    override fun commit(candidate: CjkCandidate, schema: CjkSchema) {
        if (schema !in supportedSchemas) return
        val key = schema to candidate.text
        commitBoosts[key] = (commitBoosts[key] ?: 0) + CommitBoostStep
    }

    private fun Int.toConfidence(maxScore: Int): Float {
        return (this.toFloat() / max(maxScore, 1).toFloat()).coerceIn(MinConfidence, 1.0f)
    }

    private fun String.normalizeCode(): String {
        return trim().lowercase().filter { it in 'a'..'z' || it == '\'' }
    }

    private companion object {
        const val CommitBoostStep = 100
        const val MinConfidence = 0.10f
    }
}

object CjkPrototypeTables {
    val pinyinSimplified: Map<CjkSchema, List<CjkTableEntry>> = mapOf(
        CjkSchema.PINYIN_SIMPLIFIED to listOf(
            CjkTableEntry(code = "ni", text = "你", annotation = "ni", weight = 980),
            CjkTableEntry(code = "ni", text = "尼", annotation = "ni", weight = 420),
            CjkTableEntry(code = "hao", text = "好", annotation = "hao", weight = 960),
            CjkTableEntry(code = "nihao", text = "你好", annotation = "ni hao", weight = 1200),
            CjkTableEntry(code = "zhong", text = "中", annotation = "zhong", weight = 950),
            CjkTableEntry(code = "zhongguo", text = "中国", annotation = "zhong guo", weight = 1180),
            CjkTableEntry(code = "shi", text = "是", annotation = "shi", weight = 990),
        ),
    )
}
