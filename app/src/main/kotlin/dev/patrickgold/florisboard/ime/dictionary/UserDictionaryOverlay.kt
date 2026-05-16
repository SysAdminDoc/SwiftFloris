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

package dev.patrickgold.florisboard.ime.dictionary

import dev.patrickgold.florisboard.lib.FlorisLocale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * ROADMAP §7 Next-3 — in-memory user-dictionary overlay for the
 * Latin ranker.
 *
 * The existing `DictionaryManager.learnWord` path writes typed
 * words to the disk-backed user dictionary and bumps their stored
 * frequency. But the **completion + correction ranker** in
 * `LatinDictionarySuggester` walks the SCOWL `sortedWords` table
 * built at load time — typed words never made it into the
 * ranking surface, so a word the user typed dozens of times still
 * lost to whatever SCOWL had at that prefix.
 *
 * This overlay closes the loop: every `learnWord` call also bumps
 * an in-memory `Map<localeTag, ConcurrentHashMap<word, freq>>` that
 * the ranker consults alongside the SCOWL snapshot. The overlay
 * frequency lives on the same `0..255` scale as SCOWL (initial 80
 * on first sight, +6 per re-use, capped at 250 — matching
 * `LEARN_INITIAL_FREQUENCY` / `LEARN_INCREMENT` /
 * `LEARN_MAX_FREQUENCY` in `DictionaryManager`).
 *
 * Reads are lock-free (`ConcurrentHashMap` + atomic per-locale
 * map switch); writes are short-critical-section.
 *
 * Per §1 (no cloud / no telemetry) the overlay lives in process
 * memory only.  Disk durability comes from the existing user-dict
 * DAO write that `DictionaryManager.learnWord` already performs;
 * the overlay is hydrated from the DAO on first read per locale
 * (lazy + idempotent) so a process restart picks up the user's
 * vocabulary on the next suggest.
 */
class UserDictionaryOverlay private constructor() {

    private val perLocale = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private val hydratedLocales = ConcurrentHashMap.newKeySet<String>()

    /** Bump the overlay entry for [rawWord] under [locale]. */
    fun learn(rawWord: String, locale: FlorisLocale) {
        val normalized = normalise(rawWord) ?: return
        val key = locale.languageTag()
        val map = perLocale.getOrPut(key) { ConcurrentHashMap() }
        // Optimistic increment loop — concurrent learners converge on the cap.
        while (true) {
            val current = map[normalized]
            val next = if (current == null) {
                INITIAL_FREQUENCY
            } else {
                (current + INCREMENT).coerceAtMost(MAX_FREQUENCY)
            }
            if (current == null) {
                if (map.putIfAbsent(normalized, next) == null) return
            } else {
                if (map.replace(normalized, current, next)) return
            }
        }
    }

    /** Forget the overlay entry for [rawWord] under [locale] (if any). */
    fun forget(rawWord: String, locale: FlorisLocale) {
        val normalized = normalise(rawWord) ?: return
        perLocale[locale.languageTag()]?.remove(normalized)
    }

    /** Return the overlay frequency on the SCOWL `0..255` scale, or 0 when absent. */
    fun frequencyFor(rawWord: String, locale: FlorisLocale): Int {
        val normalized = normalise(rawWord) ?: return 0
        return perLocale[locale.languageTag()]?.get(normalized) ?: 0
    }

    /**
     * Words in the overlay for [locale] that start with [prefix].
     * Used by the completion path to surface user-typed words
     * alongside SCOWL completions.  Returns up to [limit] matches;
     * the ranker sorts the merged list, so order here is not
     * meaningful.
     */
    fun wordsWithPrefix(prefix: String, locale: FlorisLocale, limit: Int = 64): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val map = perLocale[locale.languageTag()] ?: return emptyList()
        if (map.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (key in map.keys) {
            if (key.startsWith(prefix)) {
                out.add(key)
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** True when [rawWord] appears in the overlay for [locale]. */
    fun contains(rawWord: String, locale: FlorisLocale): Boolean {
        val normalized = normalise(rawWord) ?: return false
        return perLocale[locale.languageTag()]?.containsKey(normalized) == true
    }

    /** True when [locale] has been hydrated from the DAO. */
    fun isHydrated(locale: FlorisLocale): Boolean =
        locale.languageTag() in hydratedLocales

    /**
     * Mark [locale] as hydrated and bulk-load DAO entries via
     * [entries].  Idempotent — the second call is a no-op so the
     * caller can attempt hydration on every suggest without
     * thrashing the DAO.  Existing in-memory entries take
     * precedence (the user typed since the DAO snapshot).
     */
    fun hydrateLocale(locale: FlorisLocale, entries: Iterable<Pair<String, Int>>) {
        val key = locale.languageTag()
        if (!hydratedLocales.add(key)) return
        val map = perLocale.getOrPut(key) { ConcurrentHashMap() }
        for ((word, freq) in entries) {
            val normalized = normalise(word) ?: continue
            // Preserve whatever frequency the DAO has — entries written by
            // older builds may sit anywhere on the 1..255 scale; clamping
            // them up to INITIAL_FREQUENCY would mass-promote forgotten /
            // imported words to top-tier. Just keep the value in the legal
            // 1..MAX_FREQUENCY range.
            val clamped = freq.coerceIn(1, MAX_FREQUENCY)
            map.putIfAbsent(normalized, clamped)
        }
    }

    /** Snapshot of the per-locale map for diagnostics + tests. */
    fun snapshotFor(locale: FlorisLocale): Map<String, Int> =
        perLocale[locale.languageTag()]?.toMap() ?: emptyMap()

    /** Drop every entry for [locale]. */
    fun clearLocale(locale: FlorisLocale) {
        perLocale[locale.languageTag()]?.clear()
        hydratedLocales.remove(locale.languageTag())
    }

    /** Drop everything — used by Settings → Reset typing learning. */
    fun clearAll() {
        perLocale.clear()
        hydratedLocales.clear()
    }

    private fun normalise(rawWord: String): String? {
        val cleaned = rawWord.trim()
            .trim { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }
        if (cleaned.length < MIN_LENGTH || cleaned.length > MAX_LENGTH) return null
        if (cleaned.any { it.isDigit() }) return null
        if (cleaned.none { it.isLetter() }) return null
        if (cleaned.any { ch -> !ch.isLetter() && ch != '\'' && ch != '-' }) return null
        return cleaned.lowercase()
    }

    companion object {
        /**
         * SwiftKey-style "instant remember" — a word the user typed even
         * once lands near the top of the frequency scale so it surfaces
         * in the suggestion strip the very next time its prefix is
         * typed.  240 / 255 ≈ 0.94 weight; after 2 re-uses we hit cap
         * and the candidate becomes auto-commit eligible (confidence
         * crosses `AutoCommitMinFrequency = 0.78`).
         */
        const val INITIAL_FREQUENCY: Int = 240
        const val INCREMENT: Int = 5
        const val MAX_FREQUENCY: Int = 250
        const val MIN_LENGTH: Int = 3
        const val MAX_LENGTH: Int = 32

        private val instance = AtomicReference(UserDictionaryOverlay())

        /** Process-wide overlay singleton. */
        fun get(): UserDictionaryOverlay = instance.get()

        /** Test-only — swap in a fresh instance. */
        internal fun resetForTest() {
            instance.set(UserDictionaryOverlay())
        }
    }
}
