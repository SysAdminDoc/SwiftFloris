/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

import android.icu.text.BreakIterator
import dev.patrickgold.florisboard.lib.FlorisLocale

open class BreakIteratorGroup {
    private val charInstancesLock = Any()
    private val charInstances = mutableMapOf<FlorisLocale, LockedBreakIterator>()

    private val wordInstancesLock = Any()
    private val wordInstances = mutableMapOf<FlorisLocale, LockedBreakIterator>()

    private val sentenceInstancesLock = Any()
    private val sentenceInstances = mutableMapOf<FlorisLocale, LockedBreakIterator>()

    fun <R> characterSync(locale: FlorisLocale, action: (BreakIterator) -> R): R {
        val instance = synchronized(charInstancesLock) {
            charInstances.getOrPut(locale) {
                LockedBreakIterator(BreakIterator.getCharacterInstance(locale.base))
            }
        }
        return instance.withLock(action)
    }

    suspend fun <R> character(locale: FlorisLocale, action: (BreakIterator) -> R): R = characterSync(locale, action)

    fun <R> wordSync(locale: FlorisLocale, action: (BreakIterator) -> R): R {
        val instance = synchronized(wordInstancesLock) {
            wordInstances.getOrPut(locale) {
                LockedBreakIterator(BreakIterator.getWordInstance(locale.base))
            }
        }
        return instance.withLock(action)
    }

    suspend fun <R> word(locale: FlorisLocale, action: (BreakIterator) -> R): R = wordSync(locale, action)

    fun <R> sentenceSync(locale: FlorisLocale, action: (BreakIterator) -> R): R {
        val instance = synchronized(sentenceInstancesLock) {
            sentenceInstances.getOrPut(locale) {
                LockedBreakIterator(BreakIterator.getSentenceInstance(locale.base))
            }
        }
        return instance.withLock(action)
    }

    suspend fun <R> sentence(locale: FlorisLocale, action: (BreakIterator) -> R): R = sentenceSync(locale, action)

    fun measureUCharsSync(
        text: String,
        numUnicodeChars: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int {
        return characterSync(locale) {
            it.setText(text)
            val start = it.first()
            var end: Int
            var n = 0
            do {
                end = it.next()
            } while (end != BreakIterator.DONE && ++n < numUnicodeChars)
            (if (end == BreakIterator.DONE) text.length else end) - start
        }.coerceIn(0, text.length)
    }

    suspend fun measureUChars(
        text: String,
        numUnicodeChars: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int = measureUCharsSync(text, numUnicodeChars, locale)

    fun measureLastUCharsSync(
        text: String,
        numUnicodeChars: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int {
        return characterSync(locale) {
            it.setText(text)
            val end = it.last()
            var start: Int
            var n = 0
            do {
                start = it.previous()
            } while (start != BreakIterator.DONE && ++n < numUnicodeChars)
            end - (if (start == BreakIterator.DONE) 0 else start)
        }.coerceIn(0, text.length)
    }

    suspend fun measureLastUChars(
        text: String,
        numUnicodeChars: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int = measureLastUCharsSync(text, numUnicodeChars, locale)

    fun measureUWordsSync(
        text: String,
        numUnicodeWords: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int {
        return wordSync(locale) {
            it.setText(text)
            val start = it.first()
            var end: Int
            var n = 0
            do {
                end = it.next()
                if (it.ruleStatus != BreakIterator.WORD_NONE) n++
            } while (end != BreakIterator.DONE && n < numUnicodeWords)
            (if (end == BreakIterator.DONE) text.length else end) - start
        }.coerceIn(0, text.length)
    }

    suspend fun measureUWords(
        text: String,
        numUnicodeWords: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int = measureUWordsSync(text, numUnicodeWords, locale)

    fun measureLastUWordsSync(
        text: String,
        numUnicodeWords: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int {
        return wordSync(locale) {
            it.setText(text)
            val end = it.last()
            var start: Int
            var n = 0
            do {
                if (it.ruleStatus != BreakIterator.WORD_NONE) n++
                start = it.previous()
            } while (start != BreakIterator.DONE && n < numUnicodeWords)
            end - (if (start == BreakIterator.DONE) 0 else start)
        }.coerceIn(0, text.length)
    }

    suspend fun measureLastUWords(
        text: String,
        numUnicodeWords: Int,
        locale: FlorisLocale = FlorisLocale.default(),
    ): Int = measureLastUWordsSync(text, numUnicodeWords, locale)

    private class LockedBreakIterator(private val iterator: BreakIterator) {
        private val lock = Any()

        fun <R> withLock(action: (BreakIterator) -> R): R {
            return synchronized(lock) { action(iterator) }
        }
    }
}
