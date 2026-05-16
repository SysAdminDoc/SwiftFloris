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

class SwipeTraceImporterTest : FunSpec({

    test("parse returns empty list for blank / whitespace input") {
        SwipeTraceImporter.parse("") shouldBe emptyList()
        SwipeTraceImporter.parse("   ") shouldBe emptyList()
        SwipeTraceImporter.parse("\n\n\n") shouldBe emptyList()
    }

    test("parseJsonLine returns null on malformed JSON") {
        SwipeTraceImporter.parseJsonLine("{not-json") shouldBe null
        SwipeTraceImporter.parseJsonLine("{}") shouldBe null  // missing required fields
        SwipeTraceImporter.parseJsonLine("\"just a string\"") shouldBe null
    }

    test("parseJsonLine accepts a valid record") {
        val line = """
            {"word":"hello","layout":"qwerty-en","language_tag":"en","source":"swipe.futo.org/v1",
             "samples":[{"x":0.10,"y":0.40,"t":0},{"x":0.30,"y":0.40,"t":50},{"x":0.60,"y":0.30,"t":120}]}
        """.trimIndent().replace("\n", " ")

        val record = SwipeTraceImporter.parseJsonLine(line)!!

        record.word shouldBe "hello"
        record.layout shouldBe "qwerty-en"
        record.languageTag shouldBe "en"
        record.source shouldBe "swipe.futo.org/v1"
        record.sampleCount shouldBe 3
        record.durationMillis shouldBe 120L
    }

    test("parseJsonLines drops malformed lines without rejecting the whole batch") {
        val text = """
            {"word":"a","layout":"qwerty-en","samples":[{"x":0.0,"y":0.0,"t":0}]}
            {malformed

            {"word":"b","layout":"qwerty-en","samples":[{"x":0.1,"y":0.1,"t":10}]}
            {"word":"","layout":"qwerty-en","samples":[{"x":0.1,"y":0.1,"t":10}]}
        """.trimIndent()

        val records = SwipeTraceImporter.parseJsonLines(text)

        records.size shouldBe 2
        records[0].word shouldBe "a"
        records[1].word shouldBe "b"
    }

    test("parse auto-detects JSON Array shape and parses every entry") {
        val text = """
            [
              {"word":"alpha","layout":"qwerty-en","samples":[{"x":0.1,"y":0.2,"t":0}]},
              {"word":"beta","layout":"qwerty-en","samples":[{"x":0.2,"y":0.3,"t":10}]}
            ]
        """.trimIndent()

        val records = SwipeTraceImporter.parse(text)

        records.size shouldBe 2
        records.map { it.word } shouldBe listOf("alpha", "beta")
    }

    test("parse falls back to JSON Lines when input does not start with '['") {
        val text = """
            {"word":"jl","layout":"qwerty-en","samples":[{"x":0.1,"y":0.1,"t":0}]}
        """.trimIndent()

        SwipeTraceImporter.parse(text).map { it.word } shouldBe listOf("jl")
    }

    test("SwipeTraceRecord rejects blank word / blank layout / empty samples") {
        try { SwipeTraceRecord(word = "", layout = "qwerty-en", samples = listOf(SwipeTraceSample(0f, 0f, 0L)))
            throw AssertionError("expected blank-word to throw")
        } catch (_: IllegalArgumentException) {}
        try { SwipeTraceRecord(word = "hi", layout = "", samples = listOf(SwipeTraceSample(0f, 0f, 0L)))
            throw AssertionError("expected blank-layout to throw")
        } catch (_: IllegalArgumentException) {}
        try { SwipeTraceRecord(word = "hi", layout = "qwerty", samples = emptyList())
            throw AssertionError("expected empty-samples to throw")
        } catch (_: IllegalArgumentException) {}
    }

    test("SwipeTraceSample rejects out-of-range coordinates and negative timestamps") {
        try { SwipeTraceSample(-0.1f, 0.5f, 0L); throw AssertionError("x<0 should throw") }
        catch (_: IllegalArgumentException) {}
        try { SwipeTraceSample(0.5f, 1.5f, 0L); throw AssertionError("y>1 should throw") }
        catch (_: IllegalArgumentException) {}
        try { SwipeTraceSample(0.5f, 0.5f, -1L); throw AssertionError("t<0 should throw") }
        catch (_: IllegalArgumentException) {}
    }

    test("durationMillis returns 0 for single-sample traces") {
        val record = SwipeTraceRecord(
            word = "x",
            layout = "qwerty-en",
            samples = listOf(SwipeTraceSample(0.5f, 0.5f, 0L)),
        )
        record.durationMillis shouldBe 0L
        record.sampleCount shouldBe 1
    }

    test("durationMillis reflects last - first timestamp delta") {
        val record = SwipeTraceRecord(
            word = "x",
            layout = "qwerty-en",
            samples = listOf(
                SwipeTraceSample(0.1f, 0.5f, 100L),
                SwipeTraceSample(0.5f, 0.5f, 250L),
            ),
        )
        record.durationMillis shouldBe 150L
    }
})
