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

class BenchmarkSuggestionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inputText = intent.getStringExtra(ExtraInputText).orEmpty().ifBlank { DefaultInputText }
        setContentView(TextView(this).apply { text = inputText })

        lifecycleScope.launch {
            try {
                val provider = LatinLanguageProvider(this@BenchmarkSuggestionActivity)
                val content = EditorContent(
                    text = inputText,
                    offset = -1,
                    localSelection = EditorRange.cursor(inputText.length),
                    localComposing = EditorRange(0, inputText.length),
                    localCurrentWord = EditorRange(0, inputText.length),
                )
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val candidates = provider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = content,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = true,
                    isPrivateSession = false,
                )
                val durationMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                Log.i(
                    "SwiftFlorisPerf",
                    "swiftfloris.nlp.firstSuggestionMs=$durationMs " +
                        "currentWordLength=${inputText.length} candidateCount=${candidates.size}",
                )
                setResult(Activity.RESULT_OK)
            } catch (error: Throwable) {
                Log.e("SwiftFlorisPerf", "swiftfloris.nlp.firstSuggestionFailed", error)
                setResult(Activity.RESULT_CANCELED)
            } finally {
                finish()
            }
        }
    }

    companion object {
        const val ExtraInputText = "dev.patrickgold.florisboard.benchmark.INPUT_TEXT"
        const val DefaultInputText = "teh"
    }
}
