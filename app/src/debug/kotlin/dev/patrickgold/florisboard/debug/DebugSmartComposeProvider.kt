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

package dev.patrickgold.florisboard.debug

import dev.patrickgold.florisboard.ime.smartcompose.LiteRtModelDescriptor
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeCandidate
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeContext
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeProvider
import dev.patrickgold.florisboard.ime.smartcompose.SmartComposeResult

/**
 * Debug-only `SmartComposeProvider`. Lives in `app/src/debug/kotlin/`
 * so the class is **only compiled into debug APKs** — release builds
 * have no reference to it, no Kotlin reflection of it, and no way to
 * activate it.
 *
 * Returns a tiny hard-coded continuation table for common English
 * trigrams. Lets us exercise the v1.8.3 ghost-text candidate plumbing
 * on a connected debug device before the real L1.1a LiteRT-LM addon
 * (prompt B1 in `docs/AI_PROMPTS_EXTERNAL_WORK.md`) ships.
 *
 * **Privacy posture:** even on debug builds, this is local-only
 * (no network call, no model download, no telemetry). It's a 1-line
 * lookup table + a fake model descriptor — strictly for verifying the
 * IME-side surface lights up correctly.
 */
object DebugSmartComposeProvider : SmartComposeProvider {

    override val activeModel = LiteRtModelDescriptor(
        name = "Debug stub (lookup table)",
        modelId = "debug-smart-compose-stub",
        preferredBackend = "cpu",
        supportedLocales = listOf("en-US", "en-GB"),
        sizeBytes = 0L,
        quantization = "int8",
        supportsLora = false,
    )

    override val supportedLocales: Set<String> = setOf("en-US", "en-GB", "en")

    override fun isReady(locale: String): Boolean {
        return locale.lowercase().startsWith("en")
    }

    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        val tail = context.precedingText.takeLast(64).lowercase().trim()
        if (tail.isBlank()) return SmartComposeResult.NoSuggestion
        // Tiny trigram-keyed lookup. Covers a few common conversation
        // openers so the ghost-text overlay reliably appears in casual
        // testing without lighting up for every keystroke.
        val candidate = when {
            tail.endsWith("on my") -> "way" to 0.82f
            tail.endsWith("at the") -> "meeting" to 0.74f
            tail.endsWith("see you") -> "soon" to 0.86f
            tail.endsWith("how are") -> "you doing" to 0.71f
            tail.endsWith("thank you so") -> "much" to 0.91f
            tail.endsWith("good") -> "morning" to 0.62f
            tail.endsWith("let me know") -> "if you need anything" to 0.66f
            tail.endsWith("looking forward to") -> "hearing from you" to 0.78f
            tail.endsWith("i'll") -> "let you know" to 0.68f
            tail.endsWith("happy") -> "birthday" to 0.58f
            else -> null
        } ?: return SmartComposeResult.NoSuggestion

        val (text, confidence) = candidate
        return SmartComposeResult.Suggestion(
            listOf(
                SmartComposeCandidate(
                    text = text,
                    confidence = confidence,
                    tokenCount = text.split(' ').size,
                ),
            ),
        )
    }
}
