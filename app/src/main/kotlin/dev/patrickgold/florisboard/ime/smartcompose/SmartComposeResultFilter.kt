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

package dev.patrickgold.florisboard.ime.smartcompose

/**
 * ROADMAP §10.5 L1.1e — smart-compose candidate post-processor.
 *
 * Smart-compose addons (LiteRT-LM, Gemini Nano, future on-device
 * runtimes) return raw candidate lists that need a couple of
 * cleanups before they're safe to feed into the ghost-text overlay:
 *
 *  1. **Drop low-confidence candidates** — anything below
 *     `minConfidence` (default 0.30) is noise the user shouldn't see.
 *  2. **Drop empty / whitespace-only text** — the inline overlay
 *     would render as a phantom space if not stripped.
 *  3. **Normalize internal whitespace** — collapse runs of spaces
 *     so two-space artefacts don't sneak into the user's commit.
 *  4. **De-duplicate** — addons sometimes return the same string
 *     twice with different confidences; keep the highest one.
 *  5. **Clamp to `maxCandidates`** — bounded output even when the
 *     addon ignored its `maxCandidates` hint.
 *  6. **Stable sort by descending confidence** — top-ranked first
 *     for the overlay's tap-to-accept priority.
 *
 * Pure function over a `SmartComposeResult` — no side effects, no
 * registry coupling. Lives on the dispatcher path between the addon
 * and the smartbar.
 */
object SmartComposeResultFilter {

    const val DEFAULT_MIN_CONFIDENCE: Float = 0.30f

    /**
     * Apply the filter chain to [input]. `NoSuggestion` passes
     * through unchanged.  A `Suggestion` whose candidate list
     * becomes empty after filtering is downgraded to `NoSuggestion`
     * so the overlay disappears cleanly instead of showing a blank
     * box.
     */
    fun filter(
        input: SmartComposeResult,
        minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
        maxCandidates: Int = 3,
    ): SmartComposeResult {
        require(minConfidence in 0f..1f) { "minConfidence must be in [0, 1]" }
        require(maxCandidates in 1..16) { "maxCandidates must be in 1..16" }

        if (input !is SmartComposeResult.Suggestion) return input

        val cleaned = LinkedHashMap<String, SmartComposeCandidate>(input.candidates.size)
        for (candidate in input.candidates) {
            if (candidate.confidence < minConfidence) continue
            val text = candidate.text.normaliseInternalWhitespace()
            if (text.isBlank()) continue
            val existing = cleaned[text]
            val winner = if (existing == null || candidate.confidence > existing.confidence) {
                // Recompute tokenCount when whitespace normalization changed the text:
                // collapsing "see  you" -> "see you" must drop the stale 3-token count to
                // 2, since tokenCount drives partial-accept granularity.
                if (text == candidate.text) {
                    candidate
                } else {
                    candidate.copy(
                        text = text,
                        tokenCount = text.split(' ').filter { it.isNotEmpty() }.size.coerceIn(1, 32),
                    )
                }
            } else {
                existing
            }
            cleaned[text] = winner
        }
        if (cleaned.isEmpty()) return SmartComposeResult.NoSuggestion

        val ranked = cleaned.values
            .sortedByDescending { it.confidence }
            .take(maxCandidates)
        return SmartComposeResult.Suggestion(ranked)
    }

    private fun String.normaliseInternalWhitespace(): String {
        if (isEmpty()) return this
        val out = StringBuilder(length)
        var lastWasSpace = false
        for (ch in this) {
            val isSpace = ch.isWhitespace()
            if (isSpace) {
                if (!lastWasSpace) out.append(' ')
                lastWasSpace = true
            } else {
                out.append(ch)
                lastWasSpace = false
            }
        }
        return out.toString().trim()
    }
}
