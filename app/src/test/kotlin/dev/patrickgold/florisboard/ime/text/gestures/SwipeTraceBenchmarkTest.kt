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
        report.p95LatencyMillis shouldBe 1.0
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
            p95LatencyNanos = 3_000_000L,
            misses = emptyList(),
        )

        val markdown = report.toMarkdown("FUTO MIT Swipe Corpus")

        markdown.contains("## FUTO MIT Swipe Corpus") shouldBe true
        markdown.contains("| Top-1 accuracy | 50.00% |") shouldBe true
        markdown.contains("| Avg predictor latency | 2.000 ms |") shouldBe true
        markdown.contains("| p95 predictor latency | 3.000 ms |") shouldBe true
    }

    test("Gesture caps overlong traces at the classifier maximum") {
        val gesture = StatisticalGlideTypingClassifier.Gesture()
        repeat(StatisticalGlideTypingClassifier.Gesture.MAX_SIZE + 75) { i ->
            gesture.addPoint(i.toFloat(), (i * 2).toFloat())
        }

        gesture.pointCount shouldBe StatisticalGlideTypingClassifier.Gesture.MAX_SIZE
        gesture.getLastX() shouldBe (StatisticalGlideTypingClassifier.Gesture.MAX_SIZE - 1).toFloat()
        gesture.getLastY() shouldBe ((StatisticalGlideTypingClassifier.Gesture.MAX_SIZE - 1) * 2).toFloat()
    }

    test("synthetic edge traces report p95 latency and failure count without external corpus") {
        val records = listOf(
            overlongTrace(),
            sparseTrace(),
            noisyTrace(),
            highSpeedTrace(),
        )
        val latencies = listOf(1_000_000L, 2_000_000L, 5_000_000L, 25_000_000L)
        var latencyIndex = 0
        var nextCallStartsRecord = true
        var now = 0L

        val report = SwipeTraceBenchmark.evaluate(
            records = records,
            maxSuggestions = 4,
            clockNanos = {
                if (nextCallStartsRecord) {
                    nextCallStartsRecord = false
                    now
                } else {
                    nextCallStartsRecord = true
                    now += latencies[latencyIndex++]
                    now
                }
            },
            predictor = SwipeTracePredictor { record, _ ->
                when (record.source) {
                    "synthetic-overlong" -> listOf(record.word, "overflow")
                    "synthetic-sparse" -> listOf("space", record.word)
                    "synthetic-noisy" -> listOf("noisy", "noise", record.word)
                    "synthetic-high-speed" -> error("high-speed trace rejected by fixture predictor")
                    else -> emptyList()
                }
            },
        )

        records[0].sampleCount shouldBe StatisticalGlideTypingClassifier.Gesture.MAX_SIZE + 75
        records[1].sampleCount shouldBe 2
        records[2].sampleCount shouldBe 48
        records[3].durationMillis shouldBe 12L
        report.totalRecords shouldBe 4
        report.evaluatedRecords shouldBe 3
        report.failedRecords shouldBe 1
        report.top1Hits shouldBe 1
        report.top3Hits shouldBe 3
        report.p95LatencyMillis shouldBe 25.0
        report.misses.map { it.expectedWord } shouldBe listOf("zoom")
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

private fun overlongTrace(): SwipeTraceRecord {
    val samples = List(StatisticalGlideTypingClassifier.Gesture.MAX_SIZE + 75) { i ->
        val progress = i.toFloat() / (StatisticalGlideTypingClassifier.Gesture.MAX_SIZE + 74).toFloat()
        SwipeTraceSample(
            x = progress,
            y = (0.35f + 0.2f * progress).coerceIn(0f, 1f),
            t = i * 4L,
        )
    }
    return syntheticTrace("overflow", "synthetic-overlong", samples)
}

private fun sparseTrace(): SwipeTraceRecord {
    return syntheticTrace(
        word = "sparse",
        source = "synthetic-sparse",
        samples = listOf(
            SwipeTraceSample(0.12f, 0.72f, 0L),
            SwipeTraceSample(0.82f, 0.18f, 240L),
        ),
    )
}

private fun noisyTrace(): SwipeTraceRecord {
    val samples = List(48) { i ->
        val progress = i / 47f
        val wobble = when (i % 4) {
            0 -> -0.025f
            1 -> 0.018f
            2 -> -0.012f
            else -> 0.03f
        }
        SwipeTraceSample(
            x = (0.1f + 0.78f * progress + wobble).coerceIn(0f, 1f),
            y = (0.55f - 0.28f * progress - wobble).coerceIn(0f, 1f),
            t = i * 8L,
        )
    }
    return syntheticTrace("noise", "synthetic-noisy", samples)
}

private fun highSpeedTrace(): SwipeTraceRecord {
    val samples = listOf(
        SwipeTraceSample(0.18f, 0.72f, 0L),
        SwipeTraceSample(0.28f, 0.60f, 2L),
        SwipeTraceSample(0.40f, 0.44f, 4L),
        SwipeTraceSample(0.58f, 0.32f, 7L),
        SwipeTraceSample(0.74f, 0.24f, 10L),
        SwipeTraceSample(0.88f, 0.18f, 12L),
    )
    return syntheticTrace("zoom", "synthetic-high-speed", samples)
}

private fun syntheticTrace(
    word: String,
    source: String,
    samples: List<SwipeTraceSample>,
): SwipeTraceRecord {
    return SwipeTraceRecord(
        word = word,
        layout = "qwerty-en",
        languageTag = "en",
        source = source,
        samples = samples,
    )
}
