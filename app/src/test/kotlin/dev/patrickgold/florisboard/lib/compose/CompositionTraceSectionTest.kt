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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The candidate row's benchmark section began before its body and ended after it, so a composition that threw
 * in between left the section open. Each composition's section now ends after the body or when Compose abandons
 * the composition.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompositionTraceSectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Composable
    private fun Child(word: String) {
        if (word == FailingWord) error("composable failed")
        Text(word)
    }

    @Composable
    private fun TracedRow(sink: AsyncTraceSink, word: String) {
        val section = rememberCompositionTraceSection("row", sink)
        Child(word)
        section.end()
    }

    @Test
    fun everyCompositionEndsItsSection() {
        val sink = CountingTraceSink()
        var word by mutableStateOf("first")
        composeRule.setContent { TracedRow(sink, word) }
        composeRule.waitForIdle()
        word = "second"
        composeRule.waitForIdle()
        word = "third"
        composeRule.waitForIdle()

        sink.events.count { it == "begin row" } shouldBe 3
        sink.events.count { it == "end row" } shouldBe 3
        sink.open shouldBe emptySet()
    }

    @Test
    fun aCompositionThatThrowsStillEndsItsSection() {
        val sink = CountingTraceSink()
        var word by mutableStateOf("ok")
        composeRule.setContent { TracedRow(sink, word) }
        composeRule.waitForIdle()

        val thrown = runCatching {
            word = FailingWord
            composeRule.waitForIdle()
        }.exceptionOrNull()

        thrown?.message shouldBe "composable failed"
        sink.events shouldContainExactly listOf("begin row", "end row", "begin row", "end row")
        sink.open shouldBe emptySet()
    }

    private companion object {
        const val FailingWord = "fail"
    }
}
