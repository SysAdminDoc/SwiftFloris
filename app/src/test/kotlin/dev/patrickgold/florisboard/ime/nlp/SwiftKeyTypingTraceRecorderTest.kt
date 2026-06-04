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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SwiftKeyTypingTraceRecorderTest {
    private lateinit var recorder: SwiftKeyTypingTraceRecorder

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        recorder = SwiftKeyTypingTraceRecorder(context)
        recorder.setEnabled(false)
        recorder.clearTraceFile()
        recorder.setEnabled(true)
    }

    @After
    fun tearDown() {
        recorder.setEnabled(false)
        recorder.clearTraceFile()
    }

    @Test
    fun privateSuggestionTraceDoesNotCreateTraceFile() {
        recorder.recordSuggestion(
            content = editorContent("hello"),
            context = SwiftKeyDecoderContext(currentWord = "hello", maxCandidateCount = 3),
            scoredCandidates = emptyList(),
            rankedCandidates = listOf(WordSuggestionCandidate("hello")),
            isPrivateSession = true,
        )

        recorder.traceFileSizeBytes() shouldBe 0L
        recorder.copyTraceFileToShareCache() shouldBe null
    }

    @Test
    fun privateAutoCommitTraceDoesNotCreateTraceFile() {
        val content = editorContent("hello")

        recorder.recordAutoCommitAccepted(
            content = content,
            candidate = WordSuggestionCandidate("hello"),
            isPrivateSession = true,
        )
        recorder.recordAutoCommitRejected(
            content = content,
            isPrivateSession = true,
        )

        recorder.traceFileSizeBytes() shouldBe 0L
        recorder.copyTraceFileToShareCache() shouldBe null
    }
}

private fun editorContent(text: String): EditorContent {
    val cursor = EditorRange.cursor(text.length)
    val word = EditorRange(0, text.length)
    return EditorContent(
        text = text,
        offset = 0,
        localSelection = cursor,
        localComposing = word,
        localCurrentWord = word,
    )
}
