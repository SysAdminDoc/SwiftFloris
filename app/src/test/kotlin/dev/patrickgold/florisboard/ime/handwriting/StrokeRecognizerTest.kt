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

package dev.patrickgold.florisboard.ime.handwriting

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private fun strokeOf(vararg coords: Triple<Float, Float, Long>): Stroke =
    Stroke(coords.map { (x, y, t) -> StrokePoint(x, y, t) })

class StrokeRecognizerTest : FunSpec({

    afterEach { StrokeRecognizerRegistry.reset() }

    test("Default recogniser always returns NoRecognition") {
        val stroke = strokeOf(0f to 0f via 0L, 10f to 0f via 50L)
        val result = StrokeRecognizer.Default.recognize(listOf(stroke), "en-US")
        result shouldBe StrokeRecognitionResult.NoRecognition
        StrokeRecognizer.Default.isReady("en-US") shouldBe false
        StrokeRecognizer.Default.supportedLocales shouldBe emptySet()
    }

    test("registry starts as Default and accepts replacement") {
        StrokeRecognizerRegistry.active shouldBe StrokeRecognizer.Default
        val fake = object : StrokeRecognizer {
            override fun recognize(strokes: List<Stroke>, locale: String) =
                StrokeRecognitionResult.Candidates(listOf(StrokeCandidate("hi", 0.9f)))
            override fun isReady(locale: String) = true
            override val supportedLocales: Set<String> = setOf("en-US")
        }
        StrokeRecognizerRegistry.setActive(fake)
        StrokeRecognizerRegistry.active shouldBe fake
        StrokeRecognizerRegistry.active.isReady("en-US") shouldBe true
    }

    test("registry.reset reverts to Default") {
        val fake = object : StrokeRecognizer {
            override fun recognize(strokes: List<Stroke>, locale: String) =
                StrokeRecognitionResult.NoRecognition
            override fun isReady(locale: String) = true
            override val supportedLocales: Set<String> = setOf("en-US")
        }
        StrokeRecognizerRegistry.setActive(fake)
        StrokeRecognizerRegistry.reset()
        StrokeRecognizerRegistry.active shouldBe StrokeRecognizer.Default
    }

    test("Stroke duration reports the timestamp range") {
        val stroke = strokeOf(
            0f to 0f via 10L,
            5f to 0f via 30L,
            10f to 0f via 100L,
        )
        stroke.durationMs shouldBe 90L
        stroke.isInstantaneous shouldBe false
    }

    test("Stroke requires at least two points") {
        shouldThrow<IllegalArgumentException> {
            Stroke(listOf(StrokePoint(0f, 0f, 0L)))
        }
    }

    test("StrokeCandidate validates confidence range") {
        shouldThrow<IllegalArgumentException> { StrokeCandidate("hi", -0.1f) }
        shouldThrow<IllegalArgumentException> { StrokeCandidate("hi", 1.5f) }
        shouldThrow<IllegalArgumentException> { StrokeCandidate("", 0.5f) }
    }

    test("Candidates result preserves ordering and confidences") {
        val stroke = strokeOf(0f to 0f via 0L, 5f to 0f via 50L)
        val fake = object : StrokeRecognizer {
            override fun recognize(strokes: List<Stroke>, locale: String) =
                StrokeRecognitionResult.Candidates(
                    listOf(
                        StrokeCandidate("hello", 0.92f),
                        StrokeCandidate("hallo", 0.45f),
                        StrokeCandidate("hello!", 0.31f),
                    ),
                )
            override fun isReady(locale: String) = true
            override val supportedLocales: Set<String> = setOf("en-US")
        }
        val result = fake.recognize(listOf(stroke), "en-US")
        val candidates = result.shouldBeInstanceOf<StrokeRecognitionResult.Candidates>()
        candidates.candidates.first().text shouldBe "hello"
        candidates.candidates.first().confidence shouldBe 0.92f
    }
})

private infix fun Pair<Float, Float>.via(t: Long): Triple<Float, Float, Long> =
    Triple(this.first, this.second, t)
