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

import android.content.Context
import dev.patrickgold.florisboard.ime.editor.EditorContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-only diagnostic trace recorder for tuning SwiftKey-style ranking.
 *
 * It is disabled unless `<filesDir>/swiftkey_trace.enabled` exists. When enabled,
 * it writes JSONL to `<filesDir>/swiftkey_typing_traces.jsonl`. No network path is
 * involved; this is a developer/user opt-in artifact for replay tuning.
 */
internal class SwiftKeyTypingTraceRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val enableFile: File
        get() = File(appContext.filesDir, EnableFileName)
    private val traceFile: File
        get() = File(appContext.filesDir, TraceFileName)

    fun isEnabled(): Boolean = enableFile.exists()

    fun recordSuggestion(
        content: EditorContent,
        context: SwiftKeyDecoderContext,
        scoredCandidates: List<SwiftKeyScoredCandidate>,
        rankedCandidates: List<SuggestionCandidate>,
    ) {
        if (!isEnabled()) return
        append(
            JSONObject()
                .put("type", "suggestion")
                .put("timestampMs", System.currentTimeMillis())
                .put("currentWord", context.currentWord)
                .put("textBeforeCursorLength", content.textBeforeSelection.length)
                .put("typedWordKnown", context.typedWordKnown)
                .put("ranked", JSONArray(rankedCandidates.map { it.text.toString() }))
                .put("scored", JSONArray(scoredCandidates.map { it.toJson() })),
        )
    }

    fun recordAutoCommitAccepted(content: EditorContent, candidate: SuggestionCandidate) {
        if (!isEnabled()) return
        append(
            JSONObject()
                .put("type", "autoCommitAccepted")
                .put("timestampMs", System.currentTimeMillis())
                .put("original", content.currentWordText.ifBlank { content.composingText })
                .put("candidate", candidate.text.toString())
                .put("textBeforeCursorLength", content.textBeforeSelection.length),
        )
    }

    fun recordAutoCommitRejected(content: EditorContent) {
        if (!isEnabled()) return
        append(
            JSONObject()
                .put("type", "autoCommitRejected")
                .put("timestampMs", System.currentTimeMillis())
                .put("currentWord", content.currentWordText.ifBlank { content.composingText })
                .put("textBeforeCursorLength", content.textBeforeSelection.length),
        )
    }

    private fun append(event: JSONObject) {
        ioScope.launch {
            runCatching {
                traceFile.parentFile?.mkdirs()
                traceFile.appendText(event.toString() + "\n")
            }
        }
    }

    private fun SwiftKeyScoredCandidate.toJson(): JSONObject {
        return JSONObject()
            .put("text", candidate.text.toString())
            .put("source", source.name)
            .put("role", score.role.name)
            .put("total", score.total)
            .put("spatialLikelihood", score.spatialLikelihood)
            .put("providerConfidence", score.providerConfidence)
            .put("dictionaryFrequency", score.dictionaryFrequency)
            .put("contextProbability", score.contextProbability)
            .put("languageConfidence", score.languageConfidence)
            .put("rejectionPenalty", score.rejectionPenalty)
            .put("editProximity", score.editProximity)
            .put("completionAffinity", score.completionAffinity)
    }

    private companion object {
        const val EnableFileName = "swiftkey_trace.enabled"
        const val TraceFileName = "swiftkey_typing_traces.jsonl"
    }
}
