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

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.core.Subtype
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FallbackLocalComposingLatencyTest {
    @Test
    fun fallbackLocalComposingBridgeStaysInsideTheMainThreadBudget() = runBlocking {
        val text = "SwiftFloris keeps local composing CPU-only across repeated typing events"
        val breakIterators = BreakIteratorGroup()

        repeat(100) {
            FallbackNlpProvider.determineLocalComposing(Subtype.DEFAULT, text, breakIterators, 0)
        }

        val samples = List(400) {
            val started = System.nanoTime()
            val range = FallbackNlpProvider.determineLocalComposing(Subtype.DEFAULT, text, breakIterators, 0)
            assertEquals(text.length, range.end)
            assertEquals("events", text.substring(range.start, range.end))
            (System.nanoTime() - started) / 1_000_000.0
        }
        val p95Millis = samples.sorted()[((samples.size - 1) * 95) / 100]

        assertTrue(
            "determineLocalComposing p95 ${"%.3f".format(Locale.US, p95Millis)}ms exceeded " +
                "$DetermineLocalComposingBudgetMs ms",
            p95Millis < DetermineLocalComposingBudgetMs,
        )
    }
}

private const val DetermineLocalComposingBudgetMs = 5.0
