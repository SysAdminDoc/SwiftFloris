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

    fun setEnabled(enabled: Boolean) {
        runCatching {
            if (enabled) {
                enableFile.writeText("enabled\n")
            } else {
                enableFile.delete()
            }
        }
    }

    fun traceFileSizeBytes(): Long {
        return traceFile.takeIf { it.exists() }?.length() ?: 0L
    }

    fun clearTraceFile() {
        runCatching {
            traceFile.delete()
        }
    }

    fun copyTraceFileToShareCache(): File? {
        if (!traceFile.exists() || traceFile.length() <= 0L) {
            return null
        }
        val exportDir = File(appContext.cacheDir, TraceExportCacheDir)
        exportDir.mkdirs()
        val exportFile = File(exportDir, TraceFileName)
        return runCatching {
            traceFile.copyTo(exportFile, overwrite = true)
            exportFile
        }.getOrNull()
    }

    fun copyReplayFixtureFileToShareCache(): File? {
        if (!traceFile.exists() || traceFile.length() <= 0L) {
            return null
        }
        val fixtures = runCatching {
            SwiftKeyTraceFixtureExporter.exportSuggestionFixtures(traceFile.readText())
        }.getOrDefault(emptyList())
        if (fixtures.isEmpty()) {
            return null
        }
        val exportDir = File(appContext.cacheDir, TraceExportCacheDir)
        exportDir.mkdirs()
        val exportFile = File(exportDir, ReplayFixtureFileName)
        return runCatching {
            exportFile.writeText(fixtures.joinToString(separator = "\n", postfix = "\n"))
            exportFile
        }.getOrNull()
    }

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
                .put("previousWords", JSONArray(previousWords(content.textBeforeSelection, context.currentWord)))
                .put("typedWordKnown", context.typedWordKnown)
                .put("touchEvidence", context.touchEvidence?.toJson() ?: JSONObject.NULL)
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
            .put("originalIndex", originalIndex)
            .put("role", score.role.name)
            .put("total", score.total)
            .put("spatialLikelihood", score.spatialLikelihood)
            .put("providerConfidence", score.providerConfidence)
            .put("autoCommitEligible", candidate.isEligibleForAutoCommit)
            .put("dictionaryFrequency", score.dictionaryFrequency)
            .put("contextProbability", score.contextProbability)
            .put("languageConfidence", score.languageConfidence)
            .put("acceptedCorrectionConfidence", score.acceptedCorrectionConfidence)
            .put("rejectionPenalty", score.rejectionPenalty)
            .put("editProximity", score.editProximity)
            .put("completionAffinity", score.completionAffinity)
    }

    private fun TouchDecoderEvidence.toJson(): JSONArray {
        return JSONArray(samples.map { sample ->
            JSONObject()
                .put("primaryText", sample.primaryText)
                .put(
                    "alternatives",
                    JSONArray(sample.alternatives.map { alternative ->
                        JSONObject()
                            .put("text", alternative.text)
                            .put("confidence", alternative.confidence)
                    }),
                )
        })
    }

    private fun previousWords(textBeforeCursor: String, currentWord: String): List<String> {
        val trimmedCurrent = currentWord.trim()
        val source = if (trimmedCurrent.isNotBlank() && textBeforeCursor.endsWith(trimmedCurrent)) {
            textBeforeCursor.dropLast(trimmedCurrent.length)
        } else {
            textBeforeCursor
        }
        val words = ArrayDeque<String>()
        var index = source.length
        while (index > 0 && words.size < MaxPreviousWordsInTrace) {
            while (index > 0 && !source[index - 1].isTraceWordChar()) {
                index--
            }
            if (index == 0) break
            val end = index
            while (index > 0) {
                val ch = source[index - 1]
                if (!ch.isTraceWordChar()) break
                index--
            }
            val word = source.substring(index, end)
            if (word.isNotBlank()) {
                words.addFirst(word)
            }
        }
        return words.toList()
    }

    private fun Char.isTraceWordChar(): Boolean {
        return isLetter() || this == '\'' || this == '-'
    }

    private companion object {
        const val EnableFileName = "swiftkey_trace.enabled"
        const val TraceFileName = "swiftkey_typing_traces.jsonl"
        const val ReplayFixtureFileName = "swiftkey_trace_replay_cases.jsonl"
        const val TraceExportCacheDir = "swiftkey-trace-export"
        const val MaxPreviousWordsInTrace = 3
    }
}
