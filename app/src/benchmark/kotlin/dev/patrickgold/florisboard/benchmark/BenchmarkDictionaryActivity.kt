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

package dev.patrickgold.florisboard.benchmark

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import kotlinx.coroutines.launch

class BenchmarkDictionaryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inputText = intent.getStringExtra(ExtraInputText).orEmpty().ifBlank { DefaultInputText }
        setContentView(TextView(this).apply { text = inputText })

        lifecycleScope.launch {
            try {
                val provider = LatinLanguageProvider(this@BenchmarkDictionaryActivity)
                val subtype = Subtype.DEFAULT

                val preloadStartedAt = SystemClock.elapsedRealtimeNanos()
                provider.preload(subtype)
                val preloadMs = (SystemClock.elapsedRealtimeNanos() - preloadStartedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.dict.preloadMs=$preloadMs language=${subtype.primaryLocale.language}",
                )

                val spellStartedAt = SystemClock.elapsedRealtimeNanos()
                val spellingResult = provider.spell(
                    subtype = subtype,
                    word = inputText,
                    precedingWords = emptyList(),
                    followingWords = emptyList(),
                    maxSuggestionCount = 8,
                    allowPossiblyOffensive = true,
                    isPrivateSession = false,
                )
                val spellMs = (SystemClock.elapsedRealtimeNanos() - spellStartedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.dict.postPreloadSpellMs=$spellMs " +
                        "wordLength=${inputText.length} suggestionCount=${spellingResult.suggestions().size}",
                )

                val content = EditorContent(
                    text = inputText,
                    offset = -1,
                    localSelection = EditorRange.cursor(inputText.length),
                    localComposing = EditorRange(0, inputText.length),
                    localCurrentWord = EditorRange(0, inputText.length),
                )
                val suggestionStartedAt = SystemClock.elapsedRealtimeNanos()
                val candidates = provider.suggest(
                    subtype = subtype,
                    content = content,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = true,
                    isPrivateSession = false,
                )
                val suggestionMs = (SystemClock.elapsedRealtimeNanos() - suggestionStartedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.dict.postPreloadSuggestionMs=$suggestionMs " +
                        "currentWordLength=${inputText.length} candidateCount=${candidates.size}",
                )
                setResult(Activity.RESULT_OK)
            } catch (error: Throwable) {
                Log.e("SwiftFlorisPerf", "swiftfloris.dict.benchmarkFailed", error)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }

    companion object {
        const val ExtraInputText = "dev.patrickgold.florisboard.benchmark.INPUT_TEXT"
        const val DefaultInputText = "zzzxqq"
    }
}
