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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StreamingVoiceTranscriptBufferTest : FunSpec({
    test("deduplicates repeated partial transcripts") {
        val buffer = StreamingVoiceTranscriptBuffer()

        val first = buffer.accept(VoiceTranscriptChunk.partial("delete"))
        val repeated = buffer.accept(VoiceTranscriptChunk.partial("delete"))

        first.changed shouldBe true
        first.visibleText shouldBe "delete"
        repeated.changed shouldBe false
        repeated.visibleText shouldBe "delete"
    }

    test("commits final chunks and clears the visible partial") {
        val buffer = StreamingVoiceTranscriptBuffer()

        buffer.accept(VoiceTranscriptChunk.partial("hello wor"))
        val update = buffer.accept(VoiceTranscriptChunk.finalResult("hello world"))

        update.committedText shouldBe "hello world"
        update.partialText shouldBe ""
        update.visibleText shouldBe "hello world"
        update.committedSegment shouldBe "hello world"
    }

    test("handles cumulative final transcripts by committing only the suffix") {
        val buffer = StreamingVoiceTranscriptBuffer()

        buffer.accept(VoiceTranscriptChunk.finalResult("hello"))
        val update = buffer.accept(VoiceTranscriptChunk.finalResult("hello world"))

        update.committedText shouldBe "hello world"
        update.committedSegment shouldBe "world"
    }

    test("command mode reports command matches from partial transcripts") {
        val buffer = StreamingVoiceTranscriptBuffer()

        val update = buffer.accept(
            chunk = VoiceTranscriptChunk.partial("delete that"),
            commandMode = true,
        )

        val commandMatch = update.commandMatch!!
        commandMatch.action shouldBe VoiceCommandAction.DELETE_THAT
        commandMatch.matchedPhrase shouldBe "delete that"
    }

    test("reset clears committed and partial transcript state") {
        val buffer = StreamingVoiceTranscriptBuffer()

        buffer.accept(VoiceTranscriptChunk.finalResult("hello"))
        buffer.accept(VoiceTranscriptChunk.partial("world"))
        buffer.reset()
        val update = buffer.accept(VoiceTranscriptChunk.partial("again"))

        update.committedText shouldBe ""
        update.visibleText shouldBe "again"
    }
})
