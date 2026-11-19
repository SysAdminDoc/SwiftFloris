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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes learned words to the encrypted personal dictionary for [DictionaryManager.learnWord].
 *
 * The store opens lazily and can fail to open (the keystore not ready yet, say). A word learned
 * then used to be dropped from disk: the in-memory overlay kept suggesting it until the process
 * ended, and then it was gone. Now it waits here, in memory, and is written the next time a write
 * or a keyboard hide ([retry]) finds the store open. At most [maxWaiting] uses wait; past that the
 * oldest are let go.
 *
 * Every use is written as one: a new word goes in at [initialFrequency], and each later use adds
 * [increment] up to [maxFrequency].
 */
internal class LearnedWordWriter(
    private val dao: () -> UserDictionaryDao?,
    private val initialFrequency: Int,
    private val increment: Int,
    private val maxFrequency: Int,
    private val maxWaiting: Int = 500,
) {
    private val waiting = ConcurrentLinkedQueue<Pair<String, FlorisLocale>>()
    private val waitingCount = AtomicInteger(0)

    /** Uses waiting for the store to open. */
    val waitingSize: Int get() = waitingCount.get()

    /**
     * Writes one use of the normalized [word] in [locale], after any uses still waiting. Returns
     * false when the store is closed and the use was kept for later. Throws when the write itself
     * fails, so the caller can take the word back out of the overlay.
     */
    fun write(word: String, locale: FlorisLocale): Boolean {
        val dao = dao()
        if (dao == null) {
            enqueue(word, locale)
            return false
        }
        drain(dao)
        persist(dao, word, locale)
        return true
    }

    /**
     * Writes the waiting uses when the store is open. Returns how many couldn't be written; those
     * are dropped, since a write that throws would throw again.
     */
    fun retry(): Int {
        if (waitingCount.get() == 0) return 0
        val dao = dao() ?: return 0
        return drain(dao)
    }

    private fun enqueue(word: String, locale: FlorisLocale) {
        waiting.add(word to locale)
        if (waitingCount.incrementAndGet() > maxWaiting && waiting.poll() != null) {
            waitingCount.decrementAndGet()
        }
    }

    private fun drain(dao: UserDictionaryDao): Int {
        var failed = 0
        while (true) {
            val (word, locale) = waiting.poll() ?: break
            waitingCount.decrementAndGet()
            if (runCatching { persist(dao, word, locale) }.isFailure) failed++
        }
        return failed
    }

    private fun persist(dao: UserDictionaryDao, word: String, locale: FlorisLocale) {
        // LIKE-based query is ASCII case-insensitive, so a manually-added
        // cased entry ("Patrick") reinforces instead of duplicating.
        val existingMatches = dao.query(word, locale)
            .filter { it.word.equals(word, ignoreCase = true) }
        if (existingMatches.isNotEmpty()) {
            val entry = existingMatches.first()
            val newFreq = (entry.freq + increment).coerceAtMost(maxFrequency)
            if (newFreq != entry.freq) {
                dao.update(entry.copy(freq = newFreq))
            }
        } else {
            dao.insert(
                UserDictionaryEntry(
                    id = 0,
                    word = word,
                    freq = initialFrequency,
                    // Must match the Room TypeConverter format (localeTag(), e.g.
                    // "en_US") — FlorisLocale.toString() is a debug format that no
                    // locale-scoped DAO query can ever match, which made learned
                    // words invisible after process restart and grew a new row per
                    // re-commit. See DictionaryManager.repairLegacyLearnedLocaleRowsAsync().
                    locale = locale.localeTag(),
                    shortcut = null,
                ),
            )
        }
    }
}
