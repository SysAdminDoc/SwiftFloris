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

class SwipeTraceReplayTest : FunSpec({

    test("toPointerData maps normalized samples into keyboard bounds") {
        val record = trace(
            word = "hello",
            samples = listOf(
                SwipeTraceSample(0.00f, 0.25f, 10L),
                SwipeTraceSample(0.50f, 0.50f, 30L),
                SwipeTraceSample(1.00f, 0.75f, 90L),
            ),
        )

        val pointerData = SwipeTraceReplay.toPointerData(
            record,
            SwipeTraceReplayBounds(widthPx = 200f, heightPx = 80f, leftPx = 10f, topPx = 5f),
        )

        pointerData.isActuallyGesture shouldBe true
        pointerData.startTime shouldBe 10L
        pointerData.positions.map { it.x } shouldBe listOf(10f, 110f, 210f)
        pointerData.positions.map { it.y } shouldBe listOf(25f, 45f, 65f)
    }

    test("SwipeTraceReplayBounds rejects invalid dimensions") {
        shouldThrowIllegalArgument { SwipeTraceReplayBounds(widthPx = 0f, heightPx = 80f) }
        shouldThrowIllegalArgument { SwipeTraceReplayBounds(widthPx = 100f, heightPx = -1f) }
        shouldThrowIllegalArgument { SwipeTraceReplayBounds(widthPx = Float.NaN, heightPx = 80f) }
    }
})

private fun shouldThrowIllegalArgument(block: () -> Unit) {
    try {
        block()
        throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) {
    }
}

private fun trace(
    word: String,
    samples: List<SwipeTraceSample> = listOf(SwipeTraceSample(0.5f, 0.5f, 0L)),
): SwipeTraceRecord {
    return SwipeTraceRecord(
        word = word,
        layout = "qwerty-en",
        languageTag = "en",
        source = "test",
        samples = samples,
    )
}
