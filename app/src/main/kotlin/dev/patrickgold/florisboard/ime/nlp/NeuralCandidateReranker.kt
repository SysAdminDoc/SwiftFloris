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

package dev.patrickgold.florisboard.ime.nlp

/**
 * Optional boundary for a future local ONNX/TFLite candidate reranker.
 *
 * Implementations receive already-scored heuristic candidates and may reorder
 * them. The base app ships [Disabled], which is a no-op and keeps the decoder
 * fully offline with no model dependency.
 */
internal fun interface NeuralCandidateReranker {
    fun rerank(
        context: SwiftKeyDecoderContext,
        scoredCandidates: List<SwiftKeyScoredCandidate>,
    ): List<SwiftKeyScoredCandidate>

    companion object {
        val Disabled = NeuralCandidateReranker { _, scoredCandidates -> scoredCandidates }
    }
}
