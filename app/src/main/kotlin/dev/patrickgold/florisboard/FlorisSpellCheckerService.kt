/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard

import android.service.textservice.SpellCheckerService
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import dev.patrickgold.florisboard.lib.devtools.flogWarning
import dev.patrickgold.florisboard.lib.util.debugSummarizeTextForLog
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.florisboard.lib.kotlin.map

internal const val SPELLCHECK_SYNC_TIMEOUT_MS = 250L

internal object SpellCheckerSyncBridge {
    suspend fun <T> runWithTimeout(
        operation: String,
        fallback: T,
        timeoutMs: Long = SPELLCHECK_SYNC_TIMEOUT_MS,
        block: suspend () -> T,
    ): T {
        return withTimeoutOrNull(timeoutMs) {
            block()
        } ?: fallback.also {
            flogWarning(LogTopic.SPELL_EVENTS) {
                "$operation timed out after ${timeoutMs}ms; returning fallback"
            }
        }
    }
}

class FlorisSpellCheckerService : SpellCheckerService() {
    private val prefs by FlorisPreferenceStore
    private val dictionaryManager get() = DictionaryManager.default()
    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()

    override fun onCreate() {
        flogInfo(LogTopic.SPELL_EVENTS)

        super.onCreate()
        dictionaryManager.loadUserDictionariesIfNecessary()
    }

    override fun createSession(): Session {
        flogInfo(LogTopic.SPELL_EVENTS)

        return FlorisSpellCheckerSession()
    }

    override fun onDestroy() {
        flogInfo(LogTopic.SPELL_EVENTS)

        super.onDestroy()
    }

    private inner class FlorisSpellCheckerSession : Session() {
        private var cachedSpellingSubtype: Subtype? = null

        override fun onCreate() {
            flogInfo(LogTopic.SPELL_EVENTS) { "Session requested locale: $locale" }

            setupSpellingIfNecessary()
        }

        private fun setupSpellingIfNecessary() {
            val evaluatedSubtype = when (prefs.spelling.languageMode.get()) {
                SpellingLanguageMode.USE_KEYBOARD_SUBTYPES -> {
                    subtypeManager.activeSubtype
                }
                else -> {
                    Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.default())
                }
            }

            if (evaluatedSubtype != cachedSpellingSubtype) {
                cachedSpellingSubtype = evaluatedSubtype
                nlpManager.preload(evaluatedSubtype)
            }
            flogInfo(LogTopic.SPELL_EVENTS) {
                "Session actual locale: ${cachedSpellingSubtype?.primaryLocale?.languageTag()}"
            }
        }

        private fun spellMultiple(
            spellingSubtype: Subtype,
            textInfos: Array<out TextInfo>,
            suggestionsLimit: Int,
        ): Array<SpellingResult> = runBlocking {
            val retInfos = Array(textInfos.size) { n ->
                val word = textInfos[n].text ?: ""
                async {
                    SpellCheckerSyncBridge.runWithTimeout(
                        operation = "spellMultiple",
                        fallback = SpellingResult.unspecified(),
                    ) {
                        nlpManager.spell(spellingSubtype, word, emptyList(), emptyList(), suggestionsLimit)
                    }
                }
            }
            Array(textInfos.size) { n ->
                retInfos[n].await().apply {
                    suggestionsInfo.setCookieAndSequence(textInfos[n].cookie, textInfos[n].sequence)
                }
            }
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            flogInfo(LogTopic.SPELL_EVENTS) {
                "textInfo=${textInfo.debugSummarizeForSpellLog()}, limit=$suggestionsLimit"
            }

            textInfo?.text ?: return SpellingResult.unspecified().suggestionsInfo
            setupSpellingIfNecessary()
            val spellingSubtype = cachedSpellingSubtype ?: return SpellingResult.unspecified().suggestionsInfo

            return runBlocking {
                SpellCheckerSyncBridge
                    .runWithTimeout(
                        operation = "spellSingle",
                        fallback = SpellingResult.unspecified(),
                    ) {
                        nlpManager.spell(spellingSubtype, textInfo.text, emptyList(), emptyList(), suggestionsLimit)
                    }
                    .sendToDebugOverlayIfEnabled(textInfo)
                    .suggestionsInfo
            }
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
            sequentialWords: Boolean,
        ): Array<SuggestionsInfo> {
            flogInfo(LogTopic.SPELL_EVENTS)

            textInfos ?: return emptyArray()
            setupSpellingIfNecessary()
            val spellingSubtype = cachedSpellingSubtype ?: return emptyArray()

            return spellMultiple(spellingSubtype, textInfos, suggestionsLimit)
                .sendToDebugOverlayIfEnabled(textInfos)
                .map { it.suggestionsInfo }
        }

        override fun onGetSentenceSuggestionsMultiple(
            textInfos: Array<out TextInfo>?,
            suggestionsLimit: Int,
        ): Array<SentenceSuggestionsInfo> {
            flogInfo(LogTopic.SPELL_EVENTS)

            val fallback = super.onGetSentenceSuggestionsMultiple(textInfos, suggestionsLimit)
            if (!BuildConfig.DEBUG || textInfos == null || !locale.startsWith("en", ignoreCase = true)) {
                return fallback
            }

            // The evaluator lives only in app/src/debug. Reflection keeps release APKs free of
            // the spike while still exercising Android's real sentence spell-checker callback on
            // a connected device. A missing or broken evaluator must never affect normal spelling.
            return Array(textInfos.size) { index ->
                debugGrammarSuggestion(textInfos[index], suggestionsLimit)
                    ?: fallback.getOrNull(index)
                    ?: SentenceSuggestionsInfo(emptyArray(), intArrayOf(), intArrayOf())
            }
        }

        private fun debugGrammarSuggestion(
            textInfo: TextInfo,
            suggestionsLimit: Int,
        ): SentenceSuggestionsInfo? {
            return try {
                val evaluatorClass = Class.forName(
                    "dev.patrickgold.florisboard.debug.EnglishGrammarRuleSpike",
                )
                val evaluator = evaluatorClass.getField("INSTANCE").get(null)
                evaluatorClass
                    .getMethod("evaluate", TextInfo::class.java, Int::class.javaPrimitiveType)
                    .invoke(evaluator, textInfo, suggestionsLimit) as? SentenceSuggestionsInfo
            } catch (error: ReflectiveOperationException) {
                flogWarning(LogTopic.SPELL_EVENTS) {
                    "Debug proofreader spike unavailable: ${error.javaClass.simpleName}"
                }
                null
            }
        }

        override fun onCancel() {
            flogInfo(LogTopic.SPELL_EVENTS)

            super.onCancel()
            if (BuildConfig.DEBUG && prefs.devtools.showSpellingOverlay.get()) {
                nlpManager.clearDebugOverlay()
            }
        }

        override fun onClose() {
            flogInfo(LogTopic.SPELL_EVENTS)

            super.onClose()
            if (BuildConfig.DEBUG && prefs.devtools.showSpellingOverlay.get()) {
                nlpManager.clearDebugOverlay()
            }
        }

        fun SpellingResult.sendToDebugOverlayIfEnabled(
            textInfo: TextInfo,
        ): SpellingResult {
            if (BuildConfig.DEBUG && prefs.devtools.showSpellingOverlay.get()) {
                nlpManager.addToDebugOverlay(textInfo.text, this)
            }
            return this
        }

        fun Array<SpellingResult>.sendToDebugOverlayIfEnabled(
            textInfos: Array<out TextInfo>,
        ): Array<SpellingResult> {
            if (BuildConfig.DEBUG && prefs.devtools.showSpellingOverlay.get()) {
                for ((n, info) in this.withIndex()) {
                    nlpManager.addToDebugOverlay(textInfos[n].text, info)
                }
            }
            return this
        }

        private fun TextInfo?.debugSummarizeForSpellLog(): String {
            if (this == null) return "(null)"
            return "TextInfo(text=${text.debugSummarizeTextForLog()}, cookie=$cookie, sequence=$sequence)"
        }
    }
}
