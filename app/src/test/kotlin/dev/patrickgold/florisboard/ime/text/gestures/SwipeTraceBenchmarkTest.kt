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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SwipeTraceBenchmarkTest : FunSpec({

    test("evaluate computes top-k hits and captures misses") {
        val records = listOf(
            benchmarkTrace("hello"),
            benchmarkTrace("world"),
            benchmarkTrace("swift"),
            benchmarkTrace("floris"),
        )
        var now = 0L
        val report = SwipeTraceBenchmark.evaluate(
            records = records,
            maxSuggestions = 3,
            maxMisses = 2,
            clockNanos = {
                now += 1_000_000L
                now
            },
            predictor = SwipeTracePredictor { record, _ ->
                when (record.word) {
                    "hello" -> listOf("hello", "help", "helm")
                    "world" -> listOf("word", "world", "ward")
                    "swift" -> listOf("shift", "swim", "swirl")
                    else -> error("classifier failed")
                }
            },
        )

        report.totalRecords shouldBe 4
        report.evaluatedRecords shouldBe 3
        report.failedRecords shouldBe 1
        report.top1Hits shouldBe 1
        report.top3Hits shouldBe 2
        report.topNHits shouldBe 2
        report.totalLatencyNanos shouldBe 4_000_000L
        report.averageLatencyMillis shouldBe 1.0
        report.misses.map { it.expectedWord } shouldBe listOf("swift", "floris")
    }

    test("evaluate treats expected words case-insensitively") {
        val report = SwipeTraceBenchmark.evaluate(
            records = listOf(benchmarkTrace("Hello")),
            predictor = SwipeTracePredictor { _, _ -> listOf("hello") },
        )

        report.top1Hits shouldBe 1
    }

    test("toMarkdown renders the benchmark summary table") {
        val report = SwipeTraceBenchmarkReport(
            totalRecords = 2,
            evaluatedRecords = 2,
            failedRecords = 0,
            top1Hits = 1,
            top3Hits = 2,
            topNHits = 2,
            maxSuggestions = 3,
            totalLatencyNanos = 4_000_000L,
            misses = emptyList(),
        )

        val markdown = report.toMarkdown("FUTO MIT Swipe Corpus")

        markdown.contains("## FUTO MIT Swipe Corpus") shouldBe true
        markdown.contains("| Top-1 accuracy | 50.00% |") shouldBe true
        markdown.contains("| Avg predictor latency | 2.000 ms |") shouldBe true
    }
})

private fun benchmarkTrace(word: String): SwipeTraceRecord {
    return SwipeTraceRecord(
        word = word,
        layout = "qwerty-en",
        languageTag = "en",
        source = "test",
        samples = listOf(SwipeTraceSample(0.5f, 0.5f, 0L)),
    )
}
