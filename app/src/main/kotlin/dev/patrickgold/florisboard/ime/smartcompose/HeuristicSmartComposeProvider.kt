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

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalBigramStore
import dev.patrickgold.florisboard.ime.dictionary.PersonalTrigramStore
import dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriors
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.runBlocking

/**
 * SmartCompose feature contract F18 (refines F5) — an on-device, no-LLM
 * [SmartComposeProvider] that lights up inline ghost-text from the
 * infrastructure that already ships in `:app`: the per-locale
 * [PersonalTrigramStore] + [PersonalBigramStore] learned as the user types,
 * with [ColdStartNextWordPriors] as the fresh-install fallback.
 *
 * This is the production baseline registered at app start (see
 * `FlorisApplication`). It is **gated at call time** by
 * `prefs.correction.heuristicSmartCompose` (default off), so toggling the
 * Settings switch takes effect immediately without re-binding. A debug
 * provider (debug builds) or an out-of-tree LiteRT-LM addon (L1.1a) can still
 * override it through [SmartComposeProviderRegistry.setActive].
 *
 * Privacy posture is identical to the personal n-gram stores: every lookup is
 * local, nothing leaves the device, and incognito commits never reach the
 * stores in the first place (gated in `KeyboardManager.learnIfAllowed`).
 *
 * The ranking + confidence logic lives in the pure [HeuristicSmartCompose]
 * object so it can be unit-tested without Android or coroutine plumbing; this
 * class only wires the stores + preference to it.
 */
class HeuristicSmartComposeProvider(private val appContext: Context) : SmartComposeProvider {

    private val prefs by FlorisPreferenceStore

    private fun enabled(): Boolean =
        runCatching { prefs.correction.heuristicSmartCompose.get() }.getOrDefault(false)

    override fun isReady(locale: String): Boolean = enabled()

    override val activeModel: LiteRtModelDescriptor? = null

    // Personal n-gram coverage follows wherever the user has typed, not a
    // fixed model locale list, so we advertise no fixed set — readiness is the
    // preference gate above.
    override val supportedLocales: Set<String> = emptySet()

    override fun predictNextTokens(
        context: SmartComposeContext,
        maxCandidates: Int,
    ): SmartComposeResult {
        if (!enabled() || maxCandidates <= 0) return SmartComposeResult.NoSuggestion
        // Strip the in-progress word (composingPrefix) off the preceding text before
        // extracting the n-gram context: precedingText runs up to the selection start and
        // therefore INCLUDES the partially-typed word, so without this the store would
        // predict the word that FOLLOWS the fragment (and feed the fragment to the n-gram
        // store as if it were a finished word, which never matches).
        val base = if (context.composingPrefix.isNotEmpty() &&
            context.precedingText.endsWith(context.composingPrefix)
        ) {
            context.precedingText.dropLast(context.composingPrefix.length)
        } else {
            context.precedingText
        }
        val (prev2, prev1) = HeuristicSmartCompose.lastTwoWords(base)
        if (prev1 == null) return SmartComposeResult.NoSuggestion

        val locale = FlorisLocale.fromTag(context.locale)
        val trigramStore = PersonalTrigramStore.get(appContext)
        val bigramStore = PersonalBigramStore.get(appContext)

        return runBlocking {
            val trigram = if (prev2 != null) {
                trigramStore.predict(prev2, prev1, locale, maxCandidates)
            } else {
                emptyList()
            }
            val bigram = if (trigram.isEmpty()) {
                bigramStore.predict(prev1, locale, maxCandidates)
            } else {
                emptyList()
            }
            val coldStart = if (trigram.isEmpty() && bigram.isEmpty()) {
                ColdStartNextWordPriors
                    .suggest(base, context.locale, maxCandidates)
                    .map { it.word }
            } else {
                emptyList()
            }
            HeuristicSmartCompose.buildResult(trigram, bigram, coldStart, maxCandidates)
        }
    }
}

/** Tier of evidence behind a heuristic ghost-text candidate (highest first). */
internal enum class HeuristicTier { TRIGRAM, BIGRAM, COLD_START }

/**
 * Pure ranking + confidence core for [HeuristicSmartComposeProvider]. No
 * Android, no coroutines, no I/O — fully JVM-unit-testable.
 *
 * Confidence is tier-based so it composes with the existing
 * `NlpManager.buildGhostTextCandidate` gate (`confidence >= 0.45f`): a
 * trigram-context hit clears the gate comfortably, a bigram hit clears it
 * narrowly (matching the F5 "trigram ≥ 0.80 or bigram ≥ 0.55" intent), and the
 * cold-start priors sit just below the gate so a fresh install does not
 * over-fire ghost text before any personal history exists.
 */
internal object HeuristicSmartCompose {

    private const val TRIGRAM_BASE = 0.85f
    private const val BIGRAM_BASE = 0.58f
    private const val COLD_START_BASE = 0.42f
    private const val RANK_PENALTY = 0.12f
    private const val MIN_CONFIDENCE = 0.05f
    private const val MAX_CONFIDENCE = 0.97f

    /**
     * Extracts the two whitespace-delimited words immediately preceding the
     * cursor from [precedingText]. Returns `(prev2, prev1)` where `prev1` is the
     * closest word; either may be `null` when there isn't enough context. The
     * personal n-gram stores normalise the words themselves, so raw tokens
     * (including trailing punctuation) are fine to pass through.
     */
    fun lastTwoWords(precedingText: String): Pair<String?, String?> {
        val tokens = precedingText.trim().split(WHITESPACE).filter { it.isNotBlank() }
        return when (tokens.size) {
            0 -> null to null
            1 -> null to tokens[0]
            else -> tokens[tokens.size - 2] to tokens[tokens.size - 1]
        }
    }

    /** Tier- and rank-scaled confidence in the [SmartComposeCandidate] range `(0, 1]`. */
    fun confidenceFor(tier: HeuristicTier, rank: Int): Float {
        val base = when (tier) {
            HeuristicTier.TRIGRAM -> TRIGRAM_BASE
            HeuristicTier.BIGRAM -> BIGRAM_BASE
            HeuristicTier.COLD_START -> COLD_START_BASE
        }
        return (base - rank * RANK_PENALTY).coerceIn(MIN_CONFIDENCE, MAX_CONFIDENCE)
    }

    /**
     * Builds the ghost-text result from the three tiers, preferring the
     * highest-evidence non-empty source. The lists are assumed pre-ranked by the
     * respective stores (most likely first).
     */
    fun buildResult(
        trigram: List<String>,
        bigram: List<String>,
        coldStart: List<String>,
        maxCandidates: Int,
    ): SmartComposeResult {
        if (maxCandidates <= 0) return SmartComposeResult.NoSuggestion
        val (tier, words) = when {
            trigram.isNotEmpty() -> HeuristicTier.TRIGRAM to trigram
            bigram.isNotEmpty() -> HeuristicTier.BIGRAM to bigram
            coldStart.isNotEmpty() -> HeuristicTier.COLD_START to coldStart
            else -> return SmartComposeResult.NoSuggestion
        }
        val candidates = words.asSequence()
            .filter { it.isNotBlank() }
            .take(maxCandidates)
            .mapIndexed { rank, word ->
                SmartComposeCandidate(text = word, confidence = confidenceFor(tier, rank))
            }
            .toList()
        return if (candidates.isEmpty()) {
            SmartComposeResult.NoSuggestion
        } else {
            SmartComposeResult.Suggestion(candidates)
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
