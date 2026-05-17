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

    // ROADMAP §6 N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST buffer
    // excision tests.

    test("removeCommittedItem excises a single matching item from a comma-separated segment") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples, bread, eggs"))

        val result = buffer.removeCommittedItem("bread")

        result.didChange shouldBe true
        result.removedCount shouldBe 1
        result.newCommittedText shouldBe "apples eggs"
        buffer.committedSegmentsSnapshot() shouldBe listOf("apples eggs")
    }

    test("removeCommittedItem strips a dangling 'and' connector after removal") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("eggs and bread"))

        val result = buffer.removeCommittedItem("bread")

        result.didChange shouldBe true
        result.newCommittedText shouldBe "eggs"
    }

    test("removeCommittedItem is case-insensitive but preserves segment casing for non-matches") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("Apples, Bread, Eggs"))

        val result = buffer.removeCommittedItem("BREAD")

        result.didChange shouldBe true
        result.newCommittedText shouldBe "Apples Eggs"
    }

    test("removeCommittedItem reports no-op when the item is absent") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread"))

        val result = buffer.removeCommittedItem("grapes")

        result.didChange shouldBe false
        result.removedCount shouldBe 0
        result.newCommittedText shouldBe "apples bread"
        buffer.committedSegmentsSnapshot() shouldBe listOf("apples bread")
    }

    test("removeCommittedItem refuses to clear the buffer on blank / whitespace input") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread"))

        buffer.removeCommittedItem("").didChange shouldBe false
        buffer.removeCommittedItem("   ").didChange shouldBe false
        buffer.committedSegmentsSnapshot() shouldBe listOf("apples bread")
    }

    test("removeCommittedItem handles a multi-word item phrase") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples, almond butter, eggs"))

        val result = buffer.removeCommittedItem("almond butter")

        result.didChange shouldBe true
        result.removedCount shouldBe 1
        result.newCommittedText shouldBe "apples eggs"
    }

    test("removeCommittedItem walks across multiple committed segments") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread"))
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread eggs"))
        // After cumulative-suffix logic: segments = ["apples bread", "eggs"]
        // Now add a fresh sentence:
        buffer.reset()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples"))
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread"))
        // Segments now hold the cumulative-suffix carve: ["apples", "bread"]

        val result = buffer.removeCommittedItem("apples")

        result.didChange shouldBe true
        // Only the segment that contained "apples" is touched; the
        // empty segment is dropped so the rebuilt committed text is
        // just "bread".
        buffer.committedSegmentsSnapshot() shouldBe listOf("bread")
        result.newCommittedText shouldBe "bread"
    }

    test("removeCommittedItem tolerates an item argument with trailing punctuation") {
        val buffer = StreamingVoiceTranscriptBuffer()
        buffer.accept(VoiceTranscriptChunk.finalResult("apples bread eggs"))

        // Caller (parser.extractRaw) may return "bread." when the
        // utterance was "no longer want bread."; the buffer should
        // still match.
        val result = buffer.removeCommittedItem("bread.")

        result.didChange shouldBe true
        result.newCommittedText shouldBe "apples eggs"
    }
})
