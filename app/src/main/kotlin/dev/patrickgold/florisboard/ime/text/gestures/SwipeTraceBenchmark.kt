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

package dev.patrickgold.florisboard.ime.text.gestures

import java.util.Locale

fun interface SwipeTracePredictor {
    fun suggest(record: SwipeTraceRecord, maxSuggestions: Int): List<CharSequence>
}

data class SwipeTraceMiss(
    val expectedWord: String,
    val layout: String,
    val languageTag: String?,
    val suggestions: List<String>,
)

data class SwipeTraceBenchmarkReport(
    val totalRecords: Int,
    val evaluatedRecords: Int,
    val failedRecords: Int,
    val top1Hits: Int,
    val top3Hits: Int,
    val topNHits: Int,
    val maxSuggestions: Int,
    val totalLatencyNanos: Long,
    val misses: List<SwipeTraceMiss>,
) {
    val top1Accuracy: Double get() = ratio(top1Hits)
    val top3Accuracy: Double get() = ratio(top3Hits)
    val topNAccuracy: Double get() = ratio(topNHits)
    val attemptedRecords: Int get() = evaluatedRecords + failedRecords
    val averageLatencyMillis: Double
        get() = if (attemptedRecords == 0) 0.0 else totalLatencyNanos / attemptedRecords / 1_000_000.0

    fun toMarkdown(title: String = "Swipe Trace Benchmark"): String {
        return buildString {
            appendLine("## $title")
            appendLine()
            appendLine("| Metric | Value |")
            appendLine("|---|---:|")
            appendLine("| Total records | $totalRecords |")
            appendLine("| Evaluated records | $evaluatedRecords |")
            appendLine("| Failed records | $failedRecords |")
            appendLine("| Top-1 accuracy | ${formatPercent(top1Accuracy)} |")
            appendLine("| Top-3 accuracy | ${formatPercent(top3Accuracy)} |")
            appendLine("| Top-$maxSuggestions accuracy | ${formatPercent(topNAccuracy)} |")
            appendLine("| Avg predictor latency | ${String.format(Locale.ROOT, "%.3f", averageLatencyMillis)} ms |")
            if (misses.isNotEmpty()) {
                appendLine()
                appendLine("| Miss | Layout | Language | Suggestions |")
                appendLine("|---|---|---|---|")
                for (miss in misses) {
                    appendLine(
                        "| ${miss.expectedWord} | ${miss.layout} | ${miss.languageTag ?: ""} | " +
                            miss.suggestions.joinToString(", ") + " |",
                    )
                }
            }
        }
    }

    private fun ratio(count: Int): Double = if (evaluatedRecords == 0) 0.0 else count.toDouble() / evaluatedRecords
}

object SwipeTraceBenchmark {

    fun evaluate(
        records: List<SwipeTraceRecord>,
        predictor: SwipeTracePredictor,
        maxSuggestions: Int = 3,
        maxMisses: Int = 25,
        clockNanos: () -> Long = { System.nanoTime() },
    ): SwipeTraceBenchmarkReport {
        require(maxSuggestions > 0) { "maxSuggestions must be positive" }
        require(maxMisses >= 0) { "maxMisses must not be negative" }

        var evaluated = 0
        var failed = 0
        var top1 = 0
        var top3 = 0
        var topN = 0
        var totalLatency = 0L
        val misses = mutableListOf<SwipeTraceMiss>()
        val top3Window = minOf(3, maxSuggestions)

        for (record in records) {
            val start = clockNanos()
            val rawSuggestions = runCatching { predictor.suggest(record, maxSuggestions) }
            val elapsed = (clockNanos() - start).coerceAtLeast(0L)
            totalLatency += elapsed

            val suggestions = if (rawSuggestions.isSuccess) {
                rawSuggestions.getOrThrow().take(maxSuggestions).map { it.toString() }
            } else {
                failed += 1
                if (misses.size < maxMisses) {
                    misses.add(record.toMiss(emptyList()))
                }
                continue
            }

            evaluated += 1
            val expected = normalize(record.word)
            val hitIndex = suggestions.indexOfFirst { normalize(it) == expected }
            if (hitIndex == 0) top1 += 1
            if (hitIndex in 0 until top3Window) top3 += 1
            if (hitIndex >= 0) {
                topN += 1
            } else if (misses.size < maxMisses) {
                misses.add(record.toMiss(suggestions))
            }
        }

        return SwipeTraceBenchmarkReport(
            totalRecords = records.size,
            evaluatedRecords = evaluated,
            failedRecords = failed,
            top1Hits = top1,
            top3Hits = top3,
            topNHits = topN,
            maxSuggestions = maxSuggestions,
            totalLatencyNanos = totalLatency,
            misses = misses,
        )
    }

    private fun SwipeTraceRecord.toMiss(suggestions: List<String>): SwipeTraceMiss {
        return SwipeTraceMiss(
            expectedWord = word,
            layout = layout,
            languageTag = languageTag,
            suggestions = suggestions,
        )
    }

    private fun normalize(word: String): String = word.trim().lowercase(Locale.ROOT)
}

private fun formatPercent(value: Double): String = String.format(Locale.ROOT, "%.2f%%", value * 100.0)
