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

package dev.patrickgold.florisboard.ime.nlp.kenlm

/**
 * ROADMAP §10.5 Next-3.1h — KenLM model-type dispatch.
 *
 * Decides which pure-Kotlin navigator to use based on the
 * `modelType` field read from the header (`Next-3.1`). Keeps the call
 * site in `NlpManager` shallow: pass a header + the assembled order
 * tables, get back a [KenLmScorer] facade that hides the trie vs
 * probing split.
 *
 * Mapping:
 *
 *  - `PROBING` / `REST_PROBING` → `KenLmProbingNavigator` (linear-
 *    probing hash table per order).
 *  - `TRIE` / `QUANT_TRIE` → `KenLmTrieNavigator` (sorted
 *    entry table per order, with Bhiksha-encoded next-pointers).
 *  - `ARRAY_TRIE` / `QUANT_ARRAY_TRIE` → currently fall back to the
 *    trie navigator (their on-disk shape differs only in how the
 *    quantization codebook is split across orders, not in the
 *    navigation algorithm).
 *  - `UNKNOWN` → throws [IllegalArgumentException] so the caller can
 *    safely default to the existing bigram chain.
 */
interface KenLmScorer {
    val modelType: KenLmModelType
    val maxOrder: Int
    fun score(history: List<String>, tail: String): Float
}

private class ProbingScorer(
    override val modelType: KenLmModelType,
    private val navigator: KenLmProbingNavigator,
) : KenLmScorer {
    override val maxOrder: Int get() = navigator.maxOrder
    override fun score(history: List<String>, tail: String): Float =
        navigator.score(history, tail)
}

private class TrieScorer(
    override val modelType: KenLmModelType,
    private val navigator: KenLmTrieNavigator,
) : KenLmScorer {
    override val maxOrder: Int get() = navigator.maxOrder
    override fun score(history: List<String>, tail: String): Float =
        navigator.score(history, tail)
}

object KenLmModelTypeDispatch {

    /**
     * Build a [KenLmScorer] suitable for [modelType]. Caller must
     * supply the relevant navigator inputs in [probingPath] or
     * [triePath]; the wrong path for the modelType is ignored.
     *
     * Throws [IllegalArgumentException] for `UNKNOWN` model types.
     */
    fun build(
        modelType: KenLmModelType,
        vocabulary: KenLmVocabulary,
        probingPath: ProbingInputs? = null,
        triePath: TrieInputs? = null,
    ): KenLmScorer = when (modelType) {
        KenLmModelType.PROBING, KenLmModelType.REST_PROBING -> {
            requireNotNull(probingPath) {
                "$modelType requires ProbingInputs; got null"
            }
            ProbingScorer(
                modelType = modelType,
                navigator = KenLmProbingNavigator(
                    vocabulary = vocabulary,
                    ordersByLevel = probingPath.ordersByLevel,
                ),
            )
        }
        KenLmModelType.TRIE,
        KenLmModelType.QUANT_TRIE,
        KenLmModelType.ARRAY_TRIE,
        KenLmModelType.QUANT_ARRAY_TRIE -> {
            requireNotNull(triePath) {
                "$modelType requires TrieInputs; got null"
            }
            TrieScorer(
                modelType = modelType,
                navigator = KenLmTrieNavigator(
                    vocabulary = vocabulary,
                    ordersByLevel = triePath.ordersByLevel,
                ),
            )
        }
        KenLmModelType.UNKNOWN -> {
            throw IllegalArgumentException("KenLM UNKNOWN model type cannot be dispatched")
        }
    }

    data class ProbingInputs(val ordersByLevel: Map<Int, KenLmProbingHash>)
    data class TrieInputs(val ordersByLevel: Map<Int, TrieOrderTable>)
}
