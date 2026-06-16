/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

class GlideBenchmarkHarnessTest : FunSpec({
    val json = Json { ignoreUnknownKeys = true }

    test("SwipeTraceRecord parses the example fixture format") {
        val raw = """{"word":"hello","layout":"qwerty-en","samples":[{"x":0.267,"y":0.333,"t":0},{"x":0.3,"y":0.167,"t":30}],"language_tag":"en","source":"test"}"""
        val record = json.decodeFromString(SwipeTraceRecord.serializer(), raw)

        record.word shouldBe "hello"
        record.layout shouldBe "qwerty-en"
        record.samples shouldHaveSize 2
        record.languageTag shouldBe "en"
        record.source shouldBe "test"
    }

    test("example fixture JSONL loads all records") {
        val stream = javaClass.classLoader!!.getResourceAsStream("glide-benchmark/example-traces.jsonl")
        stream shouldNotBe null
        val lines = stream!!.bufferedReader().readLines().filter { it.isNotBlank() }

        lines shouldHaveSize 3
        val records = lines.map { json.decodeFromString(SwipeTraceRecord.serializer(), it) }

        records[0].word shouldBe "hello"
        records[1].word shouldBe "the"
        records[2].word shouldBe "world"
    }

    test("SwipeTraceRecord rejects blank word") {
        val result = runCatching {
            SwipeTraceRecord(
                word = "",
                layout = "qwerty-en",
                samples = listOf(SwipeTraceSample(0.5f, 0.5f, 0L)),
            )
        }
        result.isFailure shouldBe true
    }

    test("SwipeTraceRecord rejects empty samples") {
        val result = runCatching {
            SwipeTraceRecord(
                word = "hello",
                layout = "qwerty-en",
                samples = emptyList(),
            )
        }
        result.isFailure shouldBe true
    }

    test("SwipeTraceRecord computes duration from first to last sample") {
        val record = SwipeTraceRecord(
            word = "test",
            layout = "qwerty-en",
            samples = listOf(
                SwipeTraceSample(0.1f, 0.1f, 10L),
                SwipeTraceSample(0.5f, 0.5f, 50L),
                SwipeTraceSample(0.9f, 0.9f, 130L),
            ),
        )
        record.durationMillis shouldBe 120L
        record.sampleCount shouldBe 3
    }
})
